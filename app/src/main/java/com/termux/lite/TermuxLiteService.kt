package com.termux.lite

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class TermuxLiteService : Service() {

    private val binder = LocalBinder()
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var paths: Paths

    private val holders = mutableListOf<SessionHolder>()
    private var nextId = 1
    private var currentId = -1

    var terminalView: TerminalView? = null
    var onExitRequested: (() -> Unit)? = null
    private var stopping = false
    private var bootstrapStarted = false
    private val bootstrapCancel = AtomicBoolean(false)

    val session: TerminalSession?
        get() = currentHolder()?.session

    inner class LocalBinder : Binder() {
        fun getService(): TermuxLiteService = this@TermuxLiteService
    }

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        AppState.loadFromPrefs()
        paths = Paths(this)
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            requestFullStop()
            return START_NOT_STICKY
        }
        // A new start means the user wants the service alive. Clear a previous
        // stop that did not actually destroy this instance (still bound).
        stopping = false
        startForeground(NOTIF_ID, buildNotification())
        when (intent?.action) {
            ACTION_NEW -> {
                if (AppState.bootstrap is BootstrapState.Ready) {
                    createSession()
                } else {
                    ensureBootstrapThenSession()
                }
            }
            ACTION_CYCLE -> cycleSession()
            else -> ensureBootstrapThenSession()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        stopping = true
        holders.forEach { it.session.finishIfRunning() }
        holders.clear()
        currentId = -1
        AppState.sessions = emptyList()
        terminalView = null
        worker.shutdownNow()
        super.onDestroy()
    }

    fun attachView(tv: TerminalView) {
        terminalView = tv
        if (stopping) return
        val current = session
        if (current != null) {
            if (tv.mTermSession !== current) {
                current.updateTerminalSessionClient(SessionClient())
                tv.attachSession(current)
                TermThemes.apply(current, tv)
                tv.onScreenUpdated()
            }
        } else if (AppState.bootstrap is BootstrapState.Ready) {
            createSession()
        }
    }

    fun detachView(tv: TerminalView?) {
        if (tv == null || terminalView !== tv) return
        terminalView = null
    }

    fun retryBootstrap() {
        bootstrapCancel.set(false)
        bootstrapStarted = false
        AppState.bootstrap = BootstrapState.Starting
        ensureBootstrapThenSession()
    }

    fun cancelBootstrap() {
        bootstrapCancel.set(true)
    }

    fun pasteClipboard() {
        val current = session ?: return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip != null && clip.itemCount > 0) {
            current.write(clip.getItemAt(0).coerceToText(this).toString())
        }
    }

    fun renameSession(id: Int, name: String) {
        val holder = holders.find { it.id == id } ?: return
        holder.customName = name.trim().ifEmpty { null }
        publishSessions()
    }

    fun cycleSession() {
        if (holders.isEmpty()) return
        val idx = holders.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        val next = holders[(idx + 1) % holders.size]
        switchSession(next.id)
    }

    fun restartLoginSession() {
        if (stopping) return
        if (AppState.bootstrap !is BootstrapState.Ready) return
        val old = currentHolder()
        addSessionInternal()
        if (old != null) {
            holders.remove(old)
            old.session.finishIfRunning()
        }
        attachCurrent()
        publishSessions()
    }

    fun createSession(): Boolean {
        // Plus / notification New are explicit "keep running" requests.
        stopping = false
        if (AppState.bootstrap !is BootstrapState.Ready) return false
        if (holders.size >= MAX_SESSIONS) return false
        if (!paths.isReady()) return false
        addSessionInternal()
        attachCurrent()
        publishSessions()
        return true
    }

    fun switchSession(id: Int) {
        if (holders.none { it.id == id }) return
        currentId = id
        attachCurrent()
        publishSessions()
    }

    fun closeSession(id: Int) {
        if (stopping) return
        val holder = holders.find { it.id == id } ?: return
        val wasCurrent = holder.id == currentId
        holders.remove(holder)
        holder.session.finishIfRunning()
        if (holders.isEmpty()) {
            requestFullStop()
            return
        }
        if (wasCurrent) {
            val next = holders.firstOrNull { it.session.isRunning } ?: holders.first()
            currentId = next.id
        }
        attachCurrent()
        publishSessions()
    }

    fun closeIfFinished(session: TerminalSession) {
        val run = Runnable {
            if (stopping) return@Runnable
            val holder = holders.find { it.session === session } ?: currentHolder() ?: return@Runnable
            if (holder.session.isRunning) return@Runnable
            val others = holders.filter { it.id != holder.id }
            if (others.isEmpty()) {
                requestFullStop()
                return@Runnable
            }
            holders.remove(holder)
            holder.session.finishIfRunning()
            val next = others.firstOrNull { it.session.isRunning } ?: others.first()
            currentId = next.id
            attachCurrent()
            publishSessions()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            run.run()
        } else {
            main.post(run)
        }
    }

    private fun findActivity(context: Context?): Activity? {
        var ctx = context ?: return null
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun reapplyTheme() {
        holders.forEach { holder ->
            val view = if (holder.id == currentId) terminalView else null
            TermThemes.apply(holder.session, view)
        }
        terminalView?.onScreenUpdated()
    }

    private fun currentHolder(): SessionHolder? = holders.find { it.id == currentId }

    private fun addSessionInternal(): SessionHolder {
        paths.home.mkdirs()
        paths.tmp.mkdirs()
        if (StoragePermission.has(this)) {
            StorageSetup.setup(this)
        }

        val env = arrayOf(
            "TERM=xterm-256color",
            "HOME=${Paths.GUEST_HOME}",
            "PREFIX=${Paths.GUEST_PREFIX}",
            "PATH=${Paths.GUEST_PREFIX}/bin",
            "TMPDIR=${Paths.GUEST_TMP}",
            "LANG=en_US.UTF-8",
            "TERMUX_VERSION=${BuildConfig.VERSION_NAME}",
            "TERMUX_APP__PACKAGE_NAME=com.termux",
            "TERMUX_APP_PACKAGE_MANAGER=apt",
            "EXTERNAL_STORAGE=/sdcard"
        )

        val shell = paths.guestShell()
        val s = TerminalSession(
            shell,
            Paths.GUEST_HOME,
            arrayOf(shell),
            env,
            TRANSCRIPT_ROWS,
            SessionClient()
        )
        val holder = SessionHolder(nextId++, s)
        holders.add(holder)
        currentId = holder.id
        return holder
    }

    private fun attachCurrent() {
        val tv = terminalView ?: return
        val current = session ?: return
        current.updateTerminalSessionClient(SessionClient())
        tv.attachSession(current)
        TermThemes.apply(current, tv)
        tv.onScreenUpdated()
    }

    private fun displayTitle(holder: SessionHolder, index: Int): String {
        val custom = holder.customName?.trim()?.takeIf { it.isNotEmpty() }
        val named = holder.session.mSessionName?.trim()?.takeIf { it.isNotEmpty() }
        val osc = holder.session.title?.trim()?.takeIf { it.isNotEmpty() }
        val cwd = cwdLabel(holder.session)
        val name = custom ?: named ?: osc ?: cwd ?: "login"
        val suffix = if (holder.session.isRunning) "" else " (exit)"
        return "${index + 1}. $name$suffix"
    }

    private fun cwdLabel(session: TerminalSession): String? {
        val cwd = session.cwd ?: return null
        val home = Paths.GUEST_HOME
        return when {
            cwd == home || cwd == "$home/" -> "~"
            cwd.startsWith("$home/") -> "~/" + cwd.removePrefix("$home/")
            else -> cwd.trimEnd('/').substringAfterLast('/')
        }
    }

    private fun publishSessions() {
        val runningCount = holders.count { it.session.isRunning }
        AppState.sessions = holders.mapIndexed { i, h ->
            SessionInfo(
                id = h.id,
                title = displayTitle(h, i),
                running = h.session.isRunning,
                selected = h.id == currentId,
                canClose = true
            )
        }
        if (stopping) return
        startForeground(NOTIF_ID, buildNotification(runningCount))
    }

    private fun ensureBootstrapThenSession() {
        if (stopping) return
        if (AppState.bootstrap is BootstrapState.Ready) {
            if (holders.isEmpty()) createSession()
            return
        }
        if (bootstrapStarted) return
        bootstrapStarted = true
        worker.execute {
            BootstrapInstaller(paths, { bootstrapCancel.get() }) { state ->
                main.post {
                    if (stopping) return@post
                    AppState.bootstrap = state
                    if (state is BootstrapState.Ready) {
                        if (holders.isEmpty()) createSession()
                    }
                    if (state is BootstrapState.Error) {
                        bootstrapStarted = false
                    }
                }
            }.ensureReady()
        }
    }

    private fun requestFullStop() {
        stopping = true
        holders.forEach { it.session.finishIfRunning() }
        holders.clear()
        currentId = -1
        AppState.sessions = emptyList()
        val host = findActivity(terminalView?.context)
        val exit = onExitRequested
        onExitRequested = null
        terminalView = null
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        (getSystemService(NotificationManager::class.java))?.cancel(NOTIF_ID)
        try {
            exit?.invoke()
        } catch (_: Exception) {
        }
        if (host != null && !host.isFinishing) {
            host.finishAndRemoveTask()
        }
        stopSelf()
    }

    private inner class SessionClient : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            if (changedSession !== session) return
            val tv = terminalView ?: return
            // Stock Termux snaps back to the live row on any output. Keep the
            // user where they scrolled until they fling/drag back to bottom.
            if (tv.topRow != 0) {
                tv.invalidate()
            } else {
                tv.onScreenUpdated()
            }
        }

        override fun onTitleChanged(changedSession: TerminalSession) {
            main.post { publishSessions() }
        }

        override fun onSessionFinished(finishedSession: TerminalSession) {
            if (stopping) return
            if (holders.none { it.session === finishedSession }) return
            main.post {
                if (stopping) return@post
                publishSessions()
                val status = finishedSession.exitStatus
                if (shouldAutoCloseFinishedSession(status, holders.size)) {
                    closeIfFinished(finishedSession)
                }
            }
        }

        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("terminal", text))
        }

        override fun onPasteTextFromClipboard(session: TerminalSession) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) {
                session.write(clip.getItemAt(0).coerceToText(this@TermuxLiteService).toString())
            }
        }

        override fun onBell(session: TerminalSession) {}
        override fun onColorsChanged(changedSession: TerminalSession) {
            val view = if (changedSession === session) terminalView else null
            TermThemes.lock(changedSession, view)
        }
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun getTerminalCursorStyle(): Int? = null
        override fun logError(tag: String, message: String) {}
        override fun logWarn(tag: String, message: String) {}
        override fun logInfo(tag: String, message: String) {}
        override fun logDebug(tag: String, message: String) {}
        override fun logVerbose(tag: String, message: String) {}
        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
        override fun logStackTrace(tag: String, e: Exception) {}
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(
            CHANNEL_ID,
            "TermuxLite session",
            NotificationManager.IMPORTANCE_LOW
        )
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(runningCount: Int = holders.count { it.session.isRunning }): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, TermuxLiteActivity::class.java).setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            ),
            pendingFlags()
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, TermuxLiteService::class.java).setAction(ACTION_STOP),
            pendingFlags()
        )
        val newSession = PendingIntent.getService(
            this, 2,
            Intent(this, TermuxLiteService::class.java).setAction(ACTION_NEW),
            pendingFlags()
        )
        val cycle = PendingIntent.getService(
            this, 3,
            Intent(this, TermuxLiteService::class.java).setAction(ACTION_CYCLE),
            pendingFlags()
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val text = when (runningCount) {
            0 -> "no running sessions"
            1 -> "1 session running"
            else -> "$runningCount sessions running"
        }
        return builder
            .setContentTitle("TermuxLite")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_add, "New", newSession)
            .addAction(android.R.drawable.ic_media_next, "Next", cycle)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop)
            .build()
    }

    private fun pendingFlags(): Int {
        return if (Build.VERSION.SDK_INT >= 23) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    private class SessionHolder(
        val id: Int,
        val session: TerminalSession,
        var customName: String? = null
    )

    companion object {
        const val ACTION_STOP = "com.termux.lite.STOP"
        const val ACTION_NEW = "com.termux.lite.NEW_SESSION"
        const val ACTION_CYCLE = "com.termux.lite.CYCLE_SESSION"
        const val CHANNEL_ID = "termuxlite-session"
        const val NOTIF_ID = 1
        const val TRANSCRIPT_ROWS = 5000
    }
}

internal fun shouldAutoCloseFinishedSession(exitStatus: Int, sessionCount: Int): Boolean {
    return sessionCount <= 1 || exitStatus == 0 || exitStatus == 130
}
