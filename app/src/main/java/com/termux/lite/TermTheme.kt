package com.termux.lite

import androidx.compose.ui.graphics.Color
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView

@Suppress("ArrayInDataClass")
data class TerminalTheme(
    val id: String,
    val label: String,
    val isLight: Boolean,
    val bg: Int,
    val fg: Int,
    val cursor: Int,
    val chrome: Int,
    val key: Int,
    val keyActive: Int,
    val accent: Int,
    val ansi: IntArray
) {
    val bgColor: Color get() = color(bg)
    val fgColor: Color get() = color(fg)
    val chromeColor: Color get() = color(chrome)
    val keyColor: Color get() = color(key)
    val keyActiveColor: Color get() = color(keyActive)
    val accentColor: Color get() = color(accent)
}

object TermThemes {
    const val DEFAULT_ID = "paper"

    val Paper = theme(
        id = "paper",
        label = "Paper",
        light = true,
        bg = 0xFFFFFFFF.toInt(),
        fg = 0xFF000000.toInt(),
        cursor = 0xFF000000.toInt(),
        chrome = 0xFFEEEEEE.toInt(),
        key = 0xFFF5F5F5.toInt(),
        keyActive = 0xFFBDBDBD.toInt(),
        accent = 0xFF006E1C.toInt(),
        ansi = intArrayOf(
            0xFF000000.toInt(), 0xFFB00020.toInt(), 0xFF006E1C.toInt(), 0xFF7A5800.toInt(),
            0xFF0055BB.toInt(), 0xFF7A1FA2.toInt(), 0xFF006A6A.toInt(), 0xFF424242.toInt(),
            0xFF757575.toInt(), 0xFFD32F2F.toInt(), 0xFF2E7D32.toInt(), 0xFFB8860B.toInt(),
            0xFF1565C0.toInt(), 0xFF8E24AA.toInt(), 0xFF00838F.toInt(), 0xFF212121.toInt()
        )
    )

    val Dark = theme(
        id = "dark",
        label = "Dark",
        light = false,
        bg = 0xFF000000.toInt(),
        fg = 0xFFFFFFFF.toInt(),
        cursor = 0xFFFFFFFF.toInt(),
        chrome = 0xFF161616.toInt(),
        key = 0xFF242424.toInt(),
        keyActive = 0xFF3E3E3E.toInt(),
        accent = 0xFF64FFDA.toInt(),
        ansi = intArrayOf(
            0xFF5A5A5A.toInt(), 0xFFFF6B68.toInt(), 0xFF5AF78E.toInt(), 0xFFF3F99D.toInt(),
            0xFF57C7FF.toInt(), 0xFFFF6AC1.toInt(), 0xFF9AEDFE.toInt(), 0xFFF1F1F0.toInt(),
            0xFF787878.toInt(), 0xFFFF8B88.toInt(), 0xFF7AFF9E.toInt(), 0xFFFFFFB5.toInt(),
            0xFF8AD8FF.toInt(), 0xFFFF8AD1.toInt(), 0xFFB4FFFF.toInt(), 0xFFFFFFFF.toInt()
        )
    )

    val SolarizedDark = theme(
        id = "solarized-dark",
        label = "Solarized Dark",
        light = false,
        bg = 0xFF002B36.toInt(),
        fg = 0xFFFDF6E3.toInt(),
        cursor = 0xFFFDF6E3.toInt(),
        chrome = 0xFF073642.toInt(),
        key = 0xFF0E4C5C.toInt(),
        keyActive = 0xFF268BD2.toInt(),
        accent = 0xFF2AA198.toInt(),
        ansi = intArrayOf(
            0xFF586E75.toInt(), 0xFFE05252.toInt(), 0xFF859900.toInt(), 0xFFB58900.toInt(),
            0xFF4FA8E8.toInt(), 0xFFD33682.toInt(), 0xFF2AA198.toInt(), 0xFFEEE8D5.toInt(),
            0xFF657B83.toInt(), 0xFFCB4B16.toInt(), 0xFF99C700.toInt(), 0xFFD9A600.toInt(),
            0xFF83C5F7.toInt(), 0xFF6C71C4.toInt(), 0xFF35CABF.toInt(), 0xFFFDF6E3.toInt()
        )
    )

    val SolarizedLight = theme(
        id = "solarized-light",
        label = "Solarized Light",
        light = true,
        bg = 0xFFFDF6E3.toInt(),
        fg = 0xFF657B83.toInt(),
        cursor = 0xFF586E75.toInt(),
        chrome = 0xFFEEE8D5.toInt(),
        key = 0xFFF5EFD9.toInt(),
        keyActive = 0xFF93A1A1.toInt(),
        accent = 0xFF268BD2.toInt(),
        ansi = intArrayOf(
            0xFF073642.toInt(), 0xFFDC322F.toInt(), 0xFF859900.toInt(), 0xFFB58900.toInt(),
            0xFF268BD2.toInt(), 0xFFD33682.toInt(), 0xFF2AA198.toInt(), 0xFFEEE8D5.toInt(),
            0xFF002B36.toInt(), 0xFFCB4B16.toInt(), 0xFF586E75.toInt(), 0xFF657B83.toInt(),
            0xFF839496.toInt(), 0xFF6C71C4.toInt(), 0xFF93A1A1.toInt(), 0xFFFDF6E3.toInt()
        )
    )

    val Dracula = theme(
        id = "dracula",
        label = "Dracula",
        light = false,
        bg = 0xFF282A36.toInt(),
        fg = 0xFFFFFFFF.toInt(),
        cursor = 0xFFFFFFFF.toInt(),
        chrome = 0xFF1E1F29.toInt(),
        key = 0xFF383A59.toInt(),
        keyActive = 0xFF6272A4.toInt(),
        accent = 0xFFBD93F9.toInt(),
        ansi = intArrayOf(
            0xFF44475A.toInt(), 0xFFFF5555.toInt(), 0xFF50FA7B.toInt(), 0xFFF1FA8C.toInt(),
            0xFFBD93F9.toInt(), 0xFFFF79C6.toInt(), 0xFF8BE9FD.toInt(), 0xFFF8F8F2.toInt(),
            0xFF6272A4.toInt(), 0xFFFF6E6E.toInt(), 0xFF69FF94.toInt(), 0xFFFFFFA5.toInt(),
            0xFFD6ACFF.toInt(), 0xFFFF92DF.toInt(), 0xFFA4FFFF.toInt(), 0xFFFFFFFF.toInt()
        )
    )

    val Nord = theme(
        id = "nord",
        label = "Nord",
        light = false,
        bg = 0xFF2E3440.toInt(),
        fg = 0xFFECEFF4.toInt(),
        cursor = 0xFFECEFF4.toInt(),
        chrome = 0xFF242933.toInt(),
        key = 0xFF3B4252.toInt(),
        keyActive = 0xFF5E81AC.toInt(),
        accent = 0xFF88C0D0.toInt(),
        ansi = intArrayOf(
            0xFF4C566A.toInt(), 0xFFBF616A.toInt(), 0xFFA3BE8C.toInt(), 0xFFEBCB8B.toInt(),
            0xFF81A1C1.toInt(), 0xFFB48EAD.toInt(), 0xFF88C0D0.toInt(), 0xFFE5E9F0.toInt(),
            0xFF616E88.toInt(), 0xFFD06F79.toInt(), 0xFFB1D199.toInt(), 0xFFF0D399.toInt(),
            0xFF94B5D8.toInt(), 0xFFC69EBF.toInt(), 0xFF9FC6D2.toInt(), 0xFFECEFF4.toInt()
        )
    )

    val Gruvbox = theme(
        id = "gruvbox",
        label = "Gruvbox",
        light = false,
        bg = 0xFF282828.toInt(),
        fg = 0xFFFBF1C7.toInt(),
        cursor = 0xFFFBF1C7.toInt(),
        chrome = 0xFF1D2021.toInt(),
        key = 0xFF3C3836.toInt(),
        keyActive = 0xFF504945.toInt(),
        accent = 0xFFB8BB26.toInt(),
        ansi = intArrayOf(
            0xFF665C54.toInt(), 0xFFFB4934.toInt(), 0xFFB8BB26.toInt(), 0xFFFABD2F.toInt(),
            0xFF83A598.toInt(), 0xFFD3869B.toInt(), 0xFF8EC07C.toInt(), 0xFFEBDBB2.toInt(),
            0xFF928374.toInt(), 0xFFFF6347.toInt(), 0xFFC9CC3B.toInt(), 0xFFFFCC40.toInt(),
            0xFF9DC4B5.toInt(), 0xFFE09BB0.toInt(), 0xFFA2D492.toInt(), 0xFFFBF1C7.toInt()
        )
    )

    val OneDark = theme(
        id = "one-dark",
        label = "One Dark",
        light = false,
        bg = 0xFF282C34.toInt(),
        fg = 0xFFFFFFFF.toInt(),
        cursor = 0xFF528BFF.toInt(),
        chrome = 0xFF1E2227.toInt(),
        key = 0xFF353B45.toInt(),
        keyActive = 0xFF4B5263.toInt(),
        accent = 0xFF61AFEF.toInt(),
        ansi = intArrayOf(
            0xFF5C6370.toInt(), 0xFFE06C75.toInt(), 0xFF98C379.toInt(), 0xFFE5C07B.toInt(),
            0xFF61AFEF.toInt(), 0xFFC678DD.toInt(), 0xFF56B6C2.toInt(), 0xFFABB2BF.toInt(),
            0xFF7F848E.toInt(), 0xFFF07178.toInt(), 0xFFA5D6A7.toInt(), 0xFFFFE082.toInt(),
            0xFF70BFFF.toInt(), 0xFFD88FEA.toInt(), 0xFF6EDCE8.toInt(), 0xFFFFFFFF.toInt()
        )
    )

    val all: List<TerminalTheme> = listOf(
        Paper, Dark, SolarizedDark, SolarizedLight, Dracula, Nord, Gruvbox, OneDark
    )

    fun byId(id: String?): TerminalTheme = all.firstOrNull { it.id == id } ?: Paper

    fun apply(
        session: TerminalSession?,
        view: TerminalView?,
        theme: TerminalTheme = AppState.theme
    ) {
        view?.setBackgroundColor(theme.bg)
        if (session == null) return
        try {
            val emulator = session.emulator ?: return
            val colors = emulator.mColors.mCurrentColors
            colors[TextStyle.COLOR_INDEX_FOREGROUND] = theme.fg
            colors[TextStyle.COLOR_INDEX_BACKGROUND] = theme.bg
            colors[TextStyle.COLOR_INDEX_CURSOR] = theme.cursor
            for (i in theme.ansi.indices) {
                colors[i] = theme.ansi[i]
            }
        } catch (_: Exception) {
            session.write(osc(10, theme.fg))
            session.write(osc(11, theme.bg))
            session.write(osc(12, theme.cursor))
        }
        view?.onScreenUpdated()
    }

    fun lock(session: TerminalSession?, view: TerminalView?) {
        if (view == null || session == null) return
        apply(session, view)
    }

    private fun osc(n: Int, argb: Int): String {
        return "\u001b]$n;${hex(argb)}\u0007"
    }

    private fun hex(argb: Int): String {
        return String.format("#%06X", argb and 0x00FFFFFF)
    }

    private fun theme(
        id: String,
        label: String,
        light: Boolean,
        bg: Int,
        fg: Int,
        cursor: Int,
        chrome: Int,
        key: Int,
        keyActive: Int,
        accent: Int,
        ansi: IntArray
    ) = TerminalTheme(id, label, light, bg, fg, cursor, chrome, key, keyActive, accent, ansi)
}

fun color(argb: Int): Color = Color(argb.toLong() and 0xFFFFFFFFL)
