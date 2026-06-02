package com.termux.lite

import android.os.Bundle
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

import java.io.File

import rikka.shizuku.Shizuku

class TermuxLiteActivity : ComponentActivity() {

    private var terminalView: TerminalView? = null
    private var session: TerminalSession? = null

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 1001
    }

    private val shizukuBinderListener = object : Shizuku.OnBinderReceivedListener {
        override fun onBinderReceived() {
            try {
                if (!Shizuku.isPreV11()) {
                    val granted = Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private val shizukuPermissionListener = object : Shizuku.OnRequestPermissionResultListener {
        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            // Permission result received — terminal will auto-retry via wrapper
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register Shizuku listeners BEFORE checking binder
        try {
            Shizuku.addBinderReceivedListenerSticky(shizukuBinderListener)
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Exception) {
            // Shizuku not installed
        }

        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Extra keys bar at top
                    var ctrlDown by remember { mutableStateOf(false) }
                    var altDown by remember { mutableStateOf(false) }

                    ExtraKeysBar(
                        ctrlDown = ctrlDown,
                        altDown = altDown,
                        onToggleCtrl = { ctrlDown = !ctrlDown },
                        onToggleAlt = { altDown = !altDown },
                        onKey = { code ->
                            val session = this@TermuxLiteActivity.session ?: return@ExtraKeysBar
                            val tv = this@TermuxLiteActivity.terminalView ?: return@ExtraKeysBar
                            sendSpecialKey(tv, session, code, ctrlDown, altDown)
                            // Reset modifiers after sending key
                            ctrlDown = false
                            altDown = false
                        }
                    )

                    // Terminal view fills remaining space
                    Box(modifier = Modifier.weight(1f).background(Color.Black)) {
                        TerminalScreen(
                            onTerminalViewReady = { tv -> terminalView = tv },
                            onSessionReady = { s -> session = s }
                        )
                    }
                }
            }
        }
    }

    private fun sendSpecialKey(tv: TerminalView, session: TerminalSession, code: Int, ctrlDown: Boolean, altDown: Boolean) {
        when {
            // Arrow keys
            code == KeyEvent.KEYCODE_DPAD_UP -> session.write(byteArrayOf(0x1b, 0x5b, 0x41).toString(Charsets.UTF_8))
            code == KeyEvent.KEYCODE_DPAD_DOWN -> session.write(byteArrayOf(0x1b, 0x5b, 0x42).toString(Charsets.UTF_8))
            code == KeyEvent.KEYCODE_DPAD_RIGHT -> session.write(byteArrayOf(0x1b, 0x5b, 0x43).toString(Charsets.UTF_8))
            code == KeyEvent.KEYCODE_DPAD_LEFT -> session.write(byteArrayOf(0x1b, 0x5b, 0x44).toString(Charsets.UTF_8))
            // Tab
            code == KeyEvent.KEYCODE_TAB -> session.write(byteArrayOf(0x09).toString(Charsets.UTF_8))
            // Escape
            code == KeyEvent.KEYCODE_ESCAPE -> session.write(byteArrayOf(0x1b).toString(Charsets.UTF_8))
            // Home (Ctrl+A or ESC[H)
            code == KeyEvent.KEYCODE_MOVE_HOME -> {
                if (ctrlDown) session.write(byteArrayOf(0x01).toString(Charsets.UTF_8))
                else session.write(byteArrayOf(0x1b, 0x5b, 0x48).toString(Charsets.UTF_8))
            }
            // End (Ctrl+E or ESC[F)
            code == KeyEvent.KEYCODE_MOVE_END -> {
                if (ctrlDown) session.write(byteArrayOf(0x05).toString(Charsets.UTF_8))
                else session.write(byteArrayOf(0x1b, 0x5b, 0x46).toString(Charsets.UTF_8))
            }
            // Page Up
            code == KeyEvent.KEYCODE_PAGE_UP -> session.write(byteArrayOf(0x1b, 0x5b, 0x35, 0x7e).toString(Charsets.UTF_8))
            // Page Down
            code == KeyEvent.KEYCODE_PAGE_DOWN -> session.write(byteArrayOf(0x1b, 0x5b, 0x36, 0x7e).toString(Charsets.UTF_8))
            // Delete
            code == KeyEvent.KEYCODE_FORWARD_DEL -> session.write(byteArrayOf(0x1b, 0x5b, 0x33, 0x7e).toString(Charsets.UTF_8))
            // Ctrl+key combos
            ctrlDown && code >= KeyEvent.KEYCODE_A && code <= KeyEvent.KEYCODE_Z -> {
                val ctrlCode = (code - KeyEvent.KEYCODE_A + 1).toByte()
                session.write(byteArrayOf(ctrlCode).toString(Charsets.UTF_8))
            }
            // Alt+key combos (ESC prefix)
            altDown && code >= KeyEvent.KEYCODE_A && code <= KeyEvent.KEYCODE_Z -> {
                session.write(byteArrayOf(0x1b, (code - KeyEvent.KEYCODE_A + 'a'.code).toByte()).toString(Charsets.UTF_8))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        terminalView?.onScreenUpdated()
    }

    override fun onDestroy() {
        super.onDestroy()
        session?.finishIfRunning()
        try {
            Shizuku.removeBinderReceivedListener(shizukuBinderListener)
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Exception) {}
    }
}

@Composable
fun ExtraKeysBar(
    ctrlDown: Boolean,
    altDown: Boolean,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onKey: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Modifier keys (toggle)
        ExtraKey("Ctrl", isActive = ctrlDown, onClick = onToggleCtrl, width = 48)
        ExtraKey("Alt", isActive = altDown, onClick = onToggleAlt, width = 40)

        Spacer(modifier = Modifier.width(4.dp))

        // Arrow keys
        ExtraKey("▲", onClick = { onKey(KeyEvent.KEYCODE_DPAD_UP) }, width = 36)
        ExtraKey("▼", onClick = { onKey(KeyEvent.KEYCODE_DPAD_DOWN) }, width = 36)
        ExtraKey("◀", onClick = { onKey(KeyEvent.KEYCODE_DPAD_LEFT) }, width = 36)
        ExtraKey("▶", onClick = { onKey(KeyEvent.KEYCODE_DPAD_RIGHT) }, width = 36)

        Spacer(modifier = Modifier.width(4.dp))

        // Special keys
        ExtraKey("Tab", onClick = { onKey(KeyEvent.KEYCODE_TAB) }, width = 40)
        ExtraKey("Esc", onClick = { onKey(KeyEvent.KEYCODE_ESCAPE) }, width = 36)
        ExtraKey("Home", onClick = { onKey(KeyEvent.KEYCODE_MOVE_HOME) }, width = 44)
        ExtraKey("End", onClick = { onKey(KeyEvent.KEYCODE_MOVE_END) }, width = 40)

        Spacer(modifier = Modifier.width(4.dp))

        // Page keys
        ExtraKey("PgUp", onClick = { onKey(KeyEvent.KEYCODE_PAGE_UP) }, width = 44)
        ExtraKey("PgDn", onClick = { onKey(KeyEvent.KEYCODE_PAGE_DOWN) }, width = 44)

        Spacer(modifier = Modifier.width(4.dp))

        // Function keys
        ExtraKey("Del", onClick = { onKey(KeyEvent.KEYCODE_FORWARD_DEL) }, width = 36)
    }
}

@Composable
fun ExtraKey(
    label: String,
    isActive: Boolean = false,
    isCtrl: Boolean = false,
    width: Int = 40,
    onClick: () -> Unit
) {
    val bgColor = when {
        isActive -> Color(0xFF4CAF50)  // Green when active
        isCtrl -> Color(0xFF333333)
        else -> Color(0xFF2D2D2D)
    }
    val textColor = when {
        isActive -> Color.Black
        else -> Color(0xFFCCCCCC)
    }

    Box(
        modifier = Modifier
            .height(32.dp)
            .width(width.dp)
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TerminalScreen(
    onTerminalViewReady: (TerminalView) -> Unit,
    onSessionReady: (TerminalSession) -> Unit
) {
    val context = LocalContext.current
    val activity = context as TermuxLiteActivity

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val tv = TerminalView(ctx, null as AttributeSet?)
            tv.setBackgroundColor(android.graphics.Color.BLACK)

            var currentTextSize = 14

            val viewClient = object : TerminalViewClient {
                override fun onScale(scale: Float): Float {
                    if (scale < 0.9f || scale > 1.1f) {
                        if (scale > 1f) currentTextSize = (currentTextSize + 1).coerceAtMost(40)
                        else currentTextSize = (currentTextSize - 1).coerceAtLeast(6)
                        tv.setTextSize(currentTextSize)
                        return 1.0f
                    }
                    return scale
                }
                override fun onSingleTapUp(e: MotionEvent) {
                    tv.requestFocus()
                    val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(tv, 0)
                }
                override fun shouldBackButtonBeMappedToEscape() = true
                override fun shouldEnforceCharBasedInput() = false
                override fun shouldUseCtrlSpaceWorkaround() = false
                override fun isTerminalViewSelected() = true
                override fun copyModeChanged(copyMode: Boolean) {}
                override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession) = false
                override fun onKeyUp(keyCode: Int, e: KeyEvent) = false
                override fun onLongPress(event: MotionEvent) = false
                override fun readControlKey() = false
                override fun readAltKey() = false
                override fun readShiftKey() = false
                override fun readFnKey() = false
                override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession) = false
                override fun onEmulatorSet() {}
                override fun logError(tag: String, message: String) {}
                override fun logWarn(tag: String, message: String) {}
                override fun logInfo(tag: String, message: String) {}
                override fun logDebug(tag: String, message: String) {}
                override fun logVerbose(tag: String, message: String) {}
                override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
                override fun logStackTrace(tag: String, e: Exception) {}
            }
            tv.setTerminalViewClient(viewClient)

            // Initialize renderer BEFORE attachSession (required)
            tv.setTextSize(14)

            // Focus properties for keyboard
            tv.isFocusable = true
            tv.isFocusableInTouchMode = true
            tv.isClickable = true

            onTerminalViewReady(tv)

            // Defer session creation until after layout pass
            tv.post {
                val homeDir = ctx.filesDir.absolutePath
                File(homeDir).mkdirs()
                File("$homeDir/tmp").mkdirs()

                // Copy rish and wrapper from assets/data to app's internal dir
                val binDir = File("$homeDir/bin")
                binDir.mkdirs()
                val rishDst = File(binDir, "rish")
                val dexDst = File(binDir, "rish_shizuku.dex")
                val wrapperDst = File(binDir, "shell_wrapper.sh")

                // Copy wrapper script from assets
                try {
                    ctx.assets.open("shell_wrapper.sh").use { input ->
                        wrapperDst.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    wrapperDst.setExecutable(true, false)
                    wrapperDst.setReadable(true, false)
                } catch (_: Exception) {}

                // Copy rish from /data/local/tmp if available
                if (!rishDst.exists()) {
                    try {
                        File("/data/local/tmp/rish").copyTo(rishDst, overwrite = true)
                        File("/data/local/tmp/rish_shizuku.dex").copyTo(dexDst, overwrite = true)
                        rishDst.setExecutable(true, false)
                        rishDst.setReadable(true, false)
                        dexDst.setReadable(true, false)
                        dexDst.setExecutable(false, false)
                    } catch (_: Exception) {}
                }

                // Use wrapper script if available, otherwise fallback
                val shellPath = if (wrapperDst.exists() && wrapperDst.canExecute()) {
                    wrapperDst.absolutePath
                } else {
                    "/system/bin/sh"
                }

                val env = arrayOf(
                    "TERM=xterm-256color",
                    "HOME=$homeDir",
                    "PATH=/system/bin:/system/xbin",
                    "LANG=en_US.UTF-8",
                    "TMPDIR=$homeDir/tmp",
                    "RISH_APPLICATION_ID=com.termux.lite"
                )

                val sessionClient = object : TerminalSessionClient {
                    override fun onTextChanged(changedSession: TerminalSession) {
                        tv.onScreenUpdated()
                    }
                    override fun onTitleChanged(changedSession: TerminalSession) {}
                    override fun onSessionFinished(finishedSession: TerminalSession) {
                        // If rish exited immediately, fall back to /system/bin/sh
                        if (shellPath != "/system/bin/sh") {
                            val fallbackSession = TerminalSession(
                                "/system/bin/sh", homeDir,
                                arrayOf("/system/bin/sh"), env, 5000, this
                            )
                            tv.attachSession(fallbackSession)
                        }
                    }
                    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("terminal", text))
                    }
                    override fun onPasteTextFromClipboard(session: TerminalSession) {
                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        val clip = cm.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val text = clip.getItemAt(0).coerceToText(ctx).toString()
                            session.write(text)
                        }
                    }
                    override fun onBell(session: TerminalSession) {}
                    override fun onColorsChanged(session: TerminalSession) {}
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

                val s = TerminalSession(shellPath, homeDir, arrayOf(shellPath), env, 5000, sessionClient)
                tv.attachSession(s)
                onSessionReady(s)

                // Request focus and show keyboard
                tv.requestFocus()
                tv.post {
                    val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(tv, 0)
                }
            }

            tv
        }
    )
}
