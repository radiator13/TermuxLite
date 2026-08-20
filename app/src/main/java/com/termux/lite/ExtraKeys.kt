package com.termux.lite

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ExtraAction {
    Esc, Slash, Minus, Home, Up, End, PgUp,
    Tab, Ctrl, Alt, Left, Down, Right, PgDn,
    Keyboard, CtrlC, Paste, Del
}

private val COMPACT_KEYS = arrayOf(
    ExtraAction.Esc, ExtraAction.Ctrl, ExtraAction.Alt, ExtraAction.Tab,
    ExtraAction.Left, ExtraAction.Up, ExtraAction.Down, ExtraAction.Right,
    ExtraAction.CtrlC, ExtraAction.Keyboard
)

@Composable
fun ExtraKeysPad(
    ctrl: ModState,
    alt: ModState,
    theme: TerminalTheme,
    onAction: (ExtraAction, Boolean) -> Unit
) {
    KeyRow(
        ctrl = ctrl,
        alt = alt,
        theme = theme,
        keys = COMPACT_KEYS,
        onAction = onAction,
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .background(theme.chromeColor)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

@Composable
private fun KeyRow(
    ctrl: ModState,
    alt: ModState,
    theme: TerminalTheme,
    keys: Array<ExtraAction>,
    onAction: (ExtraAction, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        keys.forEach { key ->
            val state = when (key) {
                ExtraAction.Ctrl -> ctrl
                ExtraAction.Alt -> alt
                else -> ModState.Off
            }
            ExtraKey(
                label = key.label(state),
                isActive = state.isActive(),
                locked = state == ModState.Locked,
                theme = theme,
                modifier = Modifier.weight(1f),
                onClick = { onAction(key, false) },
                onLongClick = if (key.hasLongPress()) {
                    { onAction(key, true) }
                } else {
                    null
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExtraKey(
    label: String,
    isActive: Boolean,
    locked: Boolean,
    theme: TerminalTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val bg = when {
        locked -> theme.accentColor
        isActive -> theme.keyActiveColor
        else -> theme.keyColor
    }
    val fg = theme.fgColor
    val clickMod = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Box(
        modifier = modifier
            .height(28.dp)
            .clipToBounds()
            .background(bg)
            .then(clickMod)
            .padding(horizontal = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 48.dp)
                .clipToBounds()
        )
    }
}

fun ExtraAction.hasLongPress(): Boolean = when (this) {
    ExtraAction.Ctrl, ExtraAction.Alt,
    ExtraAction.Tab, ExtraAction.Left, ExtraAction.Up, ExtraAction.Down, ExtraAction.Right,
    ExtraAction.CtrlC, ExtraAction.Keyboard -> true
    else -> false
}

fun ExtraAction.label(state: ModState = ModState.Off): String = when (this) {
    ExtraAction.Esc -> "ESC"
    ExtraAction.Slash -> "/"
    ExtraAction.Minus -> "-"
    ExtraAction.Home -> "HM"
    ExtraAction.Up -> "▲"
    ExtraAction.End -> "END"
    ExtraAction.PgUp -> "PU"
    ExtraAction.Tab -> "TAB"
    ExtraAction.Ctrl -> if (state == ModState.Locked) "CTRL*" else "CTRL"
    ExtraAction.Alt -> if (state == ModState.Locked) "ALT*" else "ALT"
    ExtraAction.Left -> "◀"
    ExtraAction.Down -> "▼"
    ExtraAction.Right -> "▶"
    ExtraAction.PgDn -> "PD"
    ExtraAction.Keyboard -> "KB"
    ExtraAction.CtrlC -> "^C"
    ExtraAction.Paste -> "PASTE"
    ExtraAction.Del -> "DEL"
}

fun ExtraAction.toKeyCode(): Int? = when (this) {
    ExtraAction.Esc -> KeyEvent.KEYCODE_ESCAPE
    ExtraAction.Home -> KeyEvent.KEYCODE_MOVE_HOME
    ExtraAction.Up -> KeyEvent.KEYCODE_DPAD_UP
    ExtraAction.End -> KeyEvent.KEYCODE_MOVE_END
    ExtraAction.PgUp -> KeyEvent.KEYCODE_PAGE_UP
    ExtraAction.Tab -> KeyEvent.KEYCODE_TAB
    ExtraAction.Left -> KeyEvent.KEYCODE_DPAD_LEFT
    ExtraAction.Down -> KeyEvent.KEYCODE_DPAD_DOWN
    ExtraAction.Right -> KeyEvent.KEYCODE_DPAD_RIGHT
    ExtraAction.PgDn -> KeyEvent.KEYCODE_PAGE_DOWN
    ExtraAction.Del -> KeyEvent.KEYCODE_FORWARD_DEL
    ExtraAction.CtrlC -> KeyEvent.KEYCODE_C
    else -> null
}
