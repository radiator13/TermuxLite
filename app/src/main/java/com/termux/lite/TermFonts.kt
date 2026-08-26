package com.termux.lite

import android.content.Context
import android.graphics.Typeface

data class TerminalFont(
    val id: String,
    val label: String,
    val sample: String = "0O 1lI <>/",
    /** Asset path under `assets/`, or null for the Android system monospace. */
    val asset: String? = null
)

object TermFonts {
    const val DEFAULT_ID = "jetbrains-mono"

    val JetBrainsMono = TerminalFont(
        "jetbrains-mono",
        "JetBrains Mono",
        asset = "fonts/JetBrainsMonoNL-Regular.ttf"
    )
    val IntelOneMono = TerminalFont(
        "intel-one-mono",
        "Intel One Mono",
        asset = "fonts/IntelOneMono-Regular.ttf"
    )
    val Hack = TerminalFont("hack", "Hack", asset = "fonts/Hack-Regular.ttf")
    val Inconsolata = TerminalFont(
        "inconsolata",
        "Inconsolata",
        asset = "fonts/Inconsolata-Regular.ttf"
    )
    val FiraMono = TerminalFont("fira-mono", "Fira Mono", asset = "fonts/FiraMono-Regular.ttf")
    val SourceCodePro = TerminalFont(
        "source-code-pro",
        "Source Code Pro",
        asset = "fonts/SourceCodePro-Regular.ttf"
    )
    val UbuntuMono = TerminalFont(
        "ubuntu-mono",
        "Ubuntu Mono",
        asset = "fonts/UbuntuMono-Regular.ttf"
    )
    val IbmPlexMono = TerminalFont(
        "ibm-plex-mono",
        "IBM Plex Mono",
        asset = "fonts/IBMPlexMono-Regular.ttf"
    )
    val System = TerminalFont("system", "Android default")

    val all: List<TerminalFont> = listOf(
        JetBrainsMono,
        IntelOneMono,
        Hack,
        Inconsolata,
        FiraMono,
        SourceCodePro,
        UbuntuMono,
        IbmPlexMono,
        System
    )

    private val cache = HashMap<String, Typeface>()

    fun byId(id: String?): TerminalFont =
        all.firstOrNull { it.id == id } ?: JetBrainsMono

    fun typeface(context: Context, id: String?): Typeface {
        val font = byId(id)
        val asset = font.asset ?: return Typeface.MONOSPACE
        cache[font.id]?.let { return it }
        val loaded = try {
            Typeface.createFromAsset(context.applicationContext.assets, asset)
        } catch (_: Exception) {
            Typeface.MONOSPACE
        }
        cache[font.id] = loaded
        return loaded
    }
}
