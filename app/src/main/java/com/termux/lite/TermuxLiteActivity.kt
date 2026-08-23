package com.termux.lite

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.AttributeSet
import android.view.ContextMenu
import android.view.KeyEvent
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.launch

class TermuxLiteActivity : ComponentActivity() {

    var terminalView: TerminalView? = null
    var service: TermuxLiteService? = null
    var appliedFontSize: Int = -1
    var attachedSession: TerminalSession? = null
    var appliedThemeId: String? = null
    private var lastBackHandledAt = 0L

    private var awaitingAllFiles = false

    private val storagePermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (StoragePermission.has(this)) {
            applyStorageSetup(true)
        } else if (StoragePermission.needsAllFilesPage()) {
            openAllFilesAccess()
        } else {
            applyStorageSetup(StoragePermission.hasLegacyWrite(this))
        }
    }

    private val allFilesAccess = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        awaitingAllFiles = false
        applyStorageSetup(StoragePermission.has(this))
    }

    var textSize: Int
        get() = Prefs.textSize
        set(value) {
            Prefs.textSize = value
            AppState.fontSize = Prefs.textSize
        }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as TermuxLiteService.LocalBinder).getService()
            service = svc
            svc.onExitRequested = {
                if (!isFinishing) finishAndRemoveTask()
            }
            terminalView?.let { svc.attachView(it) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service?.onExitRequested = null
            service = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        Prefs.init(this)
        AppState.loadFromPrefs()
        applyKeepScreenOn()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleSystemBack()
            }
        })

        val start = Intent(this, TermuxLiteService::class.java)
        startService(start)
        bindService(start, connection, Context.BIND_AUTO_CREATE)

        setContent {
            TermuxLiteApp(activity = this)
        }

        window.decorView.post {
            if (StorageActionReceiver.wantsStorage(intent) || !StoragePermission.has(this)) {
                requestStorageSetup()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isFinishing) {
            startActivity(
                Intent(this, TermuxLiteActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            return
        }
        startService(Intent(this, TermuxLiteService::class.java))
        terminalView?.let { service?.attachView(it) }
        if (StorageActionReceiver.wantsStorage(intent)) {
            requestStorageSetup()
        }
    }

    override fun onStart() {
        super.onStart()
        if (isFinishing) return
        startService(Intent(this, TermuxLiteService::class.java))
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing) return
        terminalView?.onScreenUpdated()
        terminalView?.let { tv -> service?.attachView(tv) }
        if (awaitingAllFiles && StoragePermission.has(this)) {
            awaitingAllFiles = false
            applyStorageSetup(true)
        }
    }

    override fun onDestroy() {
        service?.onExitRequested = null
        service?.detachView(terminalView)
        try {
            unbindService(connection)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            AppState.volumeCtrl = event.action != KeyEvent.ACTION_UP
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_UP) {
            AppState.settingsOpen = !AppState.settingsOpen
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) {
                handleSystemBack()
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && (event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
            val session = currentSession()
            if (session != null && !session.isRunning) {
                service?.closeIfFinished(session)
                return true
            }
        }
        if (Essentials.swallowChromeKey(event.keyCode)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Back / predictive-back: close chrome or hide the IME. Never write ESC
     * (or ^C) to the PTY — extra-keys ESC is the explicit cancel path.
     */
    fun handleSystemBack() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastBackHandledAt < 80) return
        lastBackHandledAt = now
        if (AppState.settingsOpen) {
            AppState.settingsOpen = false
            return
        }
        if (AppState.drawerOpen) {
            AppState.pendingDrawerClose = true
            return
        }
        hideKeyboard()
    }

    fun hideKeyboardIfVisible(): Boolean {
        if (!isImeVisible()) return false
        hideKeyboard()
        return true
    }

    fun hideKeyboard() {
        val view = currentFocus ?: terminalView ?: window.decorView
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun isImeVisible(): Boolean {
        val view = terminalView ?: window.decorView
        val insets = ViewCompat.getRootWindowInsets(view)
            ?: ViewCompat.getRootWindowInsets(window.decorView)
        return insets?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }

    fun pasteClipboard() {
        service?.pasteClipboard()
    }

    fun openUrl(url: String) {
        val parsed = try {
            Uri.parse(url)
        } catch (_: Exception) {
            return
        }
        if (parsed.scheme.equals("file", ignoreCase = true)) {
            sendBroadcast(
                Intent(Intent.ACTION_VIEW)
                    .setClassName(packageName, "com.termux.app.TermuxOpenReceiver")
                    .setData(parsed)
            )
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, parsed).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Essentials.stripMenus(menu)
        return false
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        Essentials.stripMenus(menu)
        return false
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, info: ContextMenu.ContextMenuInfo?) {
        Essentials.stripContextMenu(menu)
    }

    override fun onContextItemSelected(item: android.view.MenuItem): Boolean = true

    fun currentSession(): TerminalSession? = service?.session

    fun applyAppearance() {
        service?.reapplyTheme()
        applyKeepScreenOn()
    }

    fun setTheme(theme: TerminalTheme) {
        Prefs.themeId = theme.id
        AppState.theme = theme
        applyAppearance()
    }

    fun setExtraKeys(enabled: Boolean) {
        Prefs.extraKeys = enabled
        AppState.extraKeys = enabled
    }

    fun setKeepScreenOn(enabled: Boolean) {
        Prefs.keepScreenOn = enabled
        AppState.keepScreenOn = enabled
        applyKeepScreenOn()
    }

    fun setFontSize(size: Int) {
        textSize = size
        terminalView?.setTextSize(Prefs.textSize)
        appliedFontSize = Prefs.textSize
    }

    private fun applyKeepScreenOn() {
        if (AppState.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun requestStorageSetup() {
        if (StoragePermission.has(this)) {
            applyStorageSetup(true)
            return
        }
        if (!StoragePermission.hasLegacyWrite(this)) {
            storagePermission.launch(StoragePermission.RUNTIME)
            return
        }
        if (StoragePermission.needsAllFilesPage()) {
            openAllFilesAccess()
            return
        }
        applyStorageSetup(false)
    }

    private fun openAllFilesAccess() {
        awaitingAllFiles = true
        try {
            allFilesAccess.launch(StoragePermission.manageAppAllFilesIntent(this))
        } catch (_: Exception) {
            try {
                allFilesAccess.launch(StoragePermission.manageAllFilesListIntent())
            } catch (_: Exception) {
                try {
                    allFilesAccess.launch(StoragePermission.appDetailsIntent(this))
                } catch (_: Exception) {
                    awaitingAllFiles = false
                    applyStorageSetup(false)
                }
            }
        }
    }

    private fun applyStorageSetup(granted: Boolean) {
        Thread {
            val result = if (granted) StorageSetup.setup(this) else null
            val probe = StorageSetup.probeMessage()
            runOnUiThread {
                if (!granted) {
                    currentSession()?.write(
                        if (StoragePermission.needsAllFilesPage()) {
                            "\r\n/sdcard denied. Enable All files access.\r\n" +
                                "If the toggle is missing: App info → menu → Allow restricted settings,\r\n" +
                                "then Settings → Apps → Special app access → All files access.\r\n"
                        } else {
                            "\r\nStorage permission denied. Allow Storage, then run termux-setup-storage again.\r\n"
                        }
                    )
                    return@runOnUiThread
                }
                result?.fold(
                    onSuccess = { dir ->
                        service?.restartLoginSession()
                        currentSession()?.write(
                            "\r\nStorage ready. $probe\r\n" +
                                "~/storage -> ${dir.absolutePath}   also ~/sdcard\r\n"
                        )
                    },
                    onFailure = { err ->
                        currentSession()?.write("\r\nStorage setup failed: ${err.message}\r\n")
                    }
                )
            }
        }.start()
    }

    fun handleExtra(action: ExtraAction, longPress: Boolean = false) {
        if (longPress) {
            when (action) {
                ExtraAction.Ctrl -> {
                    AppState.ctrl = AppState.ctrl.lock()
                    return
                }
                ExtraAction.Alt -> {
                    AppState.alt = AppState.alt.lock()
                    return
                }
                ExtraAction.Keyboard -> {
                    setExtraKeys(false)
                    return
                }
                ExtraAction.CtrlC -> {
                    pasteClipboard()
                    return
                }
                ExtraAction.Tab -> {
                    sendSpecialKey(KeyEvent.KEYCODE_FORWARD_DEL)
                    AppState.consumeOneShotModifiers()
                    return
                }
                ExtraAction.Left -> {
                    sendSpecialKey(KeyEvent.KEYCODE_MOVE_HOME)
                    AppState.consumeOneShotModifiers()
                    return
                }
                ExtraAction.Up -> {
                    sendSpecialKey(KeyEvent.KEYCODE_PAGE_UP)
                    AppState.consumeOneShotModifiers()
                    return
                }
                ExtraAction.Down -> {
                    sendSpecialKey(KeyEvent.KEYCODE_PAGE_DOWN)
                    AppState.consumeOneShotModifiers()
                    return
                }
                ExtraAction.Right -> {
                    sendSpecialKey(KeyEvent.KEYCODE_MOVE_END)
                    AppState.consumeOneShotModifiers()
                    return
                }
                else -> {}
            }
        }
        when (action) {
            ExtraAction.Ctrl -> {
                AppState.ctrl = AppState.ctrl.tap()
            }
            ExtraAction.Alt -> {
                AppState.alt = AppState.alt.tap()
            }
            ExtraAction.Keyboard -> toggleKeyboard()
            ExtraAction.Slash -> {
                currentSession()?.write("/")
                AppState.consumeOneShotModifiers()
            }
            ExtraAction.Minus -> {
                currentSession()?.write("-")
                AppState.consumeOneShotModifiers()
            }
            ExtraAction.CtrlC -> {
                currentSession()?.write("\u0003")
                AppState.consumeOneShotModifiers()
            }
            ExtraAction.Paste -> pasteClipboard()
            else -> {
                val code = action.toKeyCode() ?: return
                sendSpecialKey(code)
                AppState.consumeOneShotModifiers()
            }
        }
    }

    private fun toggleKeyboard() {
        val tv = terminalView ?: return
        tv.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (isImeVisible()) {
            imm.hideSoftInputFromWindow(tv.windowToken, 0)
        } else {
            imm.showSoftInput(tv, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun sendSpecialKey(code: Int) {
        val current = currentSession() ?: return
        if (!current.isRunning && (code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_DPAD_CENTER)) {
            service?.closeIfFinished(current)
            return
        }
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> current.write("\u001b[A")
            KeyEvent.KEYCODE_DPAD_DOWN -> current.write("\u001b[B")
            KeyEvent.KEYCODE_DPAD_RIGHT -> current.write("\u001b[C")
            KeyEvent.KEYCODE_DPAD_LEFT -> current.write("\u001b[D")
            KeyEvent.KEYCODE_TAB -> current.write("\t")
            KeyEvent.KEYCODE_ESCAPE -> current.write("\u001b")
            KeyEvent.KEYCODE_MOVE_HOME -> current.write("\u001b[H")
            KeyEvent.KEYCODE_MOVE_END -> current.write("\u001b[F")
            KeyEvent.KEYCODE_PAGE_UP -> current.write("\u001b[5~")
            KeyEvent.KEYCODE_PAGE_DOWN -> current.write("\u001b[6~")
            KeyEvent.KEYCODE_FORWARD_DEL -> current.write("\u001b[3~")
        }
    }

    companion object {
        const val ACTION_SETUP_STORAGE = "com.termux.lite.SETUP_STORAGE"
        const val PREFS_NAME = Prefs.NAME
        const val KEY_TEXT_SIZE = Prefs.KEY_TEXT_SIZE
        const val DEFAULT_TEXT_SIZE = Prefs.DEFAULT_TEXT_SIZE
        const val MIN_TEXT_SIZE = Prefs.MIN_TEXT_SIZE
        const val MAX_TEXT_SIZE = Prefs.MAX_TEXT_SIZE
    }
}

private class LiteViewClient(
    private val activity: TermuxLiteActivity,
    private val tv: TerminalView
) : TerminalViewClient {

    override fun onScale(scale: Float): Float {
        // Ignore small scale jitter from a one-finger scroll; only pinch.
        if (scale < 0.8f || scale > 1.25f) {
            val next = if (scale > 1f) {
                (activity.textSize + 1).coerceAtMost(TermuxLiteActivity.MAX_TEXT_SIZE)
            } else {
                (activity.textSize - 1).coerceAtLeast(TermuxLiteActivity.MIN_TEXT_SIZE)
            }
            activity.setFontSize(next)
            return 1.0f
        }
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        val url = UrlAtTap.find(tv, e)
        if (url != null) {
            activity.openUrl(url)
            return
        }
        tv.requestFocus()
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(tv, 0)
    }

    override fun shouldBackButtonBeMappedToEscape() = false
    override fun shouldEnforceCharBasedInput() = false
    override fun shouldUseCtrlSpaceWorkaround() = false
    override fun isTerminalViewSelected() = !AppState.settingsOpen
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        if (!session.isRunning && (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
            activity.service?.closeIfFinished(session)
            return true
        }
        // TerminalView maps BACK to ESC in onKeyPreIme when the flag above is
        // true. Keep swallowing BACK here too so a focused view never writes
        // ESC / cancel into the PTY (Grok CLI, vim, etc.).
        return keyCode == KeyEvent.KEYCODE_BACK
    }
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean {
        return keyCode == KeyEvent.KEYCODE_BACK
    }
    override fun onLongPress(event: MotionEvent) = false
    override fun readControlKey() = AppState.controlDown()
    override fun readAltKey() = AppState.altDown()
    override fun readShiftKey() = false
    override fun readFnKey() = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        if (!session.isRunning && (codePoint == 13 || codePoint == 10)) {
            activity.service?.closeIfFinished(session)
            return true
        }
        AppState.consumeOneShotModifiers()
        return false
    }
    override fun onEmulatorSet() {
        TermThemes.apply(activity.currentSession(), tv)
    }
    override fun logError(tag: String, message: String) {}
    override fun logWarn(tag: String, message: String) {}
    override fun logInfo(tag: String, message: String) {}
    override fun logDebug(tag: String, message: String) {}
    override fun logVerbose(tag: String, message: String) {}
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
    override fun logStackTrace(tag: String, e: Exception) {}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TermuxLiteApp(
    activity: TermuxLiteActivity
) {
    val theme = AppState.theme
    val sessions = AppState.sessions
    val state = AppState.bootstrap
    val settingsOpen = AppState.settingsOpen
    val extraKeys = AppState.extraKeys
    val ready = state is BootstrapState.Ready

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.isOpen }.collect { AppState.drawerOpen = it }
    }

    SideEffect {
        val window = activity.window
        window.statusBarColor = theme.chromeColor.toArgb()
        window.navigationBarColor = theme.chromeColor.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = theme.isLight
            isAppearanceLightNavigationBars = theme.isLight
        }
    }

    LaunchedEffect(AppState.pendingDrawerClose) {
        if (AppState.pendingDrawerClose) {
            drawerState.close()
            AppState.pendingDrawerClose = false
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Drawer drag fights terminal vertical scroll and makes it jitter.
        gesturesEnabled = false,
        drawerContent = {
            SessionDrawer(
                theme = theme,
                sessions = sessions,
                bootstrapReady = ready,
                onSelect = { id ->
                    activity.service?.switchSession(id)
                    scope.launch { drawerState.close() }
                },
                onNew = {
                    activity.service?.createSession()
                    scope.launch { drawerState.close() }
                },
                onClose = { id -> activity.service?.closeSession(id) },
                onRename = { id, name -> activity.service?.renameSession(id, name) },
                onSettings = {
                    AppState.settingsOpen = true
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize().background(theme.bgColor)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                        )
                    )
                    .windowInsetsPadding(
                        WindowInsets.navigationBars.union(WindowInsets.ime)
                    )
            ) {
                val currentTitle = sessions.firstOrNull { it.selected }?.title ?: "TermuxLite"
                TopBar(
                    theme = theme,
                    title = currentTitle,
                    sessions = sessions,
                    canCreate = ready && sessions.size < MAX_SESSIONS,
                    onMenu = {
                        scope.launch {
                            if (drawerState.isOpen) drawerState.close() else drawerState.open()
                        }
                    },
                    onSelect = { id -> activity.service?.switchSession(id) },
                    onNew = { activity.service?.createSession() },
                    onSettings = { AppState.settingsOpen = !AppState.settingsOpen }
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    if (ready) {
                        TerminalScreen()
                    } else {
                        BootstrapOverlay(
                            state = state,
                            theme = theme,
                            onRetry = { activity.service?.retryBootstrap() },
                            onCancel = { activity.service?.cancelBootstrap() }
                        )
                    }
                    if (settingsOpen) {
                        SettingsScreen(
                            theme = theme,
                            fontSize = AppState.fontSize,
                            extraKeys = extraKeys,
                            keepScreenOn = AppState.keepScreenOn,
                            onTheme = { activity.setTheme(it) },
                            onFontSize = { activity.setFontSize(it) },
                            onExtraKeys = { activity.setExtraKeys(it) },
                            onKeepScreenOn = { activity.setKeepScreenOn(it) },
                            onClose = { AppState.settingsOpen = false },
                            onOpenUrl = { activity.openUrl(it) }
                        )
                    }
                }
                if (extraKeys && !settingsOpen) {
                    ExtraKeysPad(
                        ctrl = AppState.ctrl,
                        alt = AppState.alt,
                        theme = theme,
                        onAction = { action, longPress -> activity.handleExtra(action, longPress) }
                    )
                }
            }
        }
    }
}

@Composable
fun TerminalScreen() {
    val context = LocalContext.current
    val activity = context as TermuxLiteActivity
    val theme = AppState.theme
    val fontSize = AppState.fontSize

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val host = TerminalScrollHost(ctx)
            val tv = host.terminal
            tv.setTextSize(activity.textSize)
            activity.appliedFontSize = activity.textSize
            tv.isFocusable = true
            tv.isFocusableInTouchMode = true
            tv.isClickable = true
            tv.setTerminalViewClient(LiteViewClient(activity, tv))
            Essentials.disableContextMenu(tv)
            host.onUrlTap = { activity.openUrl(it) }
            activity.terminalView = tv
            TermThemes.apply(activity.currentSession(), tv, theme)
            activity.appliedThemeId = theme.id
            tv.post {
                activity.service?.attachView(tv)
                activity.attachedSession = activity.currentSession()
                tv.requestFocus()
            }
            host
        },
        update = { host ->
            val tv = (host as TerminalScrollHost).terminal
            if (activity.appliedFontSize != fontSize) {
                tv.setTextSize(fontSize)
                activity.appliedFontSize = fontSize
            }
            val current = activity.currentSession()
            if (activity.terminalView !== tv || activity.attachedSession !== current) {
                activity.service?.attachView(tv)
                activity.attachedSession = current
            }
            if (activity.appliedThemeId != theme.id) {
                TermThemes.apply(current, tv, theme)
                activity.appliedThemeId = theme.id
            }
        }
    )
}

@Composable
fun BootstrapOverlay(
    state: BootstrapState,
    theme: TerminalTheme,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "TermuxLite",
            color = theme.fgColor,
            fontSize = 20.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
        Text(
            text = when (state) {
                is BootstrapState.Starting -> "Preparing…"
                is BootstrapState.Downloading -> {
                    val mb = state.bytes / (1024 * 1024)
                    val totBytes = if (state.total > 0) state.total else BootstrapConfig.SIZE_BYTES
                    val tot = totBytes / (1024 * 1024)
                    val pct = if (totBytes > 0) (state.bytes * 100 / totBytes).coerceIn(0, 100) else 0
                    "Downloading Termux bootstrap…\n$mb / $tot MB ($pct%)\nNeeds network, once."
                }
                is BootstrapState.Extracting -> "Extracting bootstrap…"
                is BootstrapState.SecondStage -> "Running second stage…"
                is BootstrapState.Ready -> "Ready"
                is BootstrapState.Error -> "Bootstrap failed:\n${state.message}"
            },
            color = if (state is BootstrapState.Error) color(0xFFB00020.toInt()) else theme.fgColor,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (state is BootstrapState.Downloading) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = theme.accentColor, fontFamily = FontFamily.Monospace)
            }
        }
        if (state is BootstrapState.Error) {
            TextButton(onClick = onRetry) {
                Text("Retry", color = theme.accentColor, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
