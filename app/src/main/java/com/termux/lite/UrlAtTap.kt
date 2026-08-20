package com.termux.lite

import android.view.MotionEvent
import com.termux.view.TerminalView

data class UrlHit(val start: Int, val end: Int, val url: String)

object UrlAtTap {
    private val MARKDOWN = Regex("""\[([^\]]+)\]\((https?://[^\s)]+)\)""")
    private val ANGLE = Regex("""<(https?://[^>\s]+)>""")
    private val SCHEME = Regex(
        """(?i)\b((?:https?|ftp|ftps|file)://[^\s<>"'\]\}>]+|mailto:[^\s<>"'\]\}>]+)"""
    )
    private val WWW = Regex("""(?i)\b(www\.[^\s<>"'\]\}>]+)""")
    private val HOSTED = Regex(
        """(?i)\b((?:github|gitlab|bitbucket)\.com/[^\s<>"'\]\}>]+|(?:x|twitter)\.com/[^\s<>"'\]\}>]+)"""
    )

    fun find(tv: TerminalView, event: MotionEvent): String? {
        val emulator = tv.mEmulator ?: return null
        val cr = tv.getColumnAndRow(event, true)
        val col = cr[0]
        val row = cr[1]
        val screen = emulator.screen
        val minRow = -screen.activeTranscriptRows
        val maxRow = emulator.mRows - 1
        if (row < minRow || row > maxRow) return null

        var y1 = row
        var y2 = row
        try {
            while (y1 > minRow && screen.getLineWrap(y1 - 1)) y1--
            while (y2 < maxRow && screen.getLineWrap(y2)) y2++
        } catch (_: Exception) {
            y1 = row
            y2 = row
        }

        val line = try {
            emulator.getSelectedText(0, y1, emulator.mColumns, y2)
        } catch (_: Exception) {
            return null
        }
        if (line.isNullOrEmpty()) return null

        var index = 0
        try {
            for (r in y1 until row) {
                index += emulator.getSelectedText(0, r, emulator.mColumns, r).length
            }
        } catch (_: Exception) {
        }
        index += col.coerceAtLeast(0)
        return urlAt(line, index)
    }

    internal fun urlAt(line: String, col: Int): String? {
        if (line.isEmpty()) return null
        val nativeHit = NativeBridge.findUrlAt(line, col)
        if (nativeHit != null) return nativeHit

        val hits = findUrls(line)
        if (hits.isEmpty()) return null
        val i = col.coerceIn(0, line.lastIndex)
        hits.firstOrNull { i in it.start until it.end }?.let { return it.url }
        val nearby = hits.minBy { dist(i, it) }
        if (dist(i, nearby) <= 3) return nearby.url
        return hits.singleOrNull()?.url
    }

    internal fun findUrls(line: String): List<UrlHit> {
        val hits = ArrayList<UrlHit>()
        fun add(range: IntRange, raw: String) {
            val url = normalize(raw) ?: return
            val start = range.first
            val end = range.last + 1
            if (hits.any { start < it.end && end > it.start && it.url == url }) return
            hits.add(UrlHit(start, end, url))
        }
        MARKDOWN.findAll(line).forEach { add(it.range, it.groupValues[2]) }
        ANGLE.findAll(line).forEach { add(it.range, it.groupValues[1]) }
        SCHEME.findAll(line).forEach { add(it.groups[1]!!.range, it.groupValues[1]) }
        WWW.findAll(line).forEach { add(it.groups[1]!!.range, it.groupValues[1]) }
        HOSTED.findAll(line).forEach { add(it.groups[1]!!.range, it.groupValues[1]) }
        hits.sortBy { it.start }
        return hits
    }

    internal fun normalize(raw: String): String? {
        var token = raw.trim()
        while (token.isNotEmpty() && token.last() in ".,;:!?)]}>\"'") {
            token = token.dropLast(1)
        }
        while (token.isNotEmpty() && token.first() in "([{\"'<") {
            token = token.drop(1)
        }
        if (token.length < 4) return null
        val url = when {
            token.startsWith("https://", ignoreCase = true) -> token
            token.startsWith("http://", ignoreCase = true) -> token
            token.startsWith("ftp://", ignoreCase = true) -> token
            token.startsWith("ftps://", ignoreCase = true) -> token
            token.startsWith("file://", ignoreCase = true) -> token
            token.startsWith("mailto:", ignoreCase = true) -> token
            token.startsWith("www.", ignoreCase = true) -> "https://$token"
            token.contains("://") -> token
            else -> "https://$token"
        }
        if (url.startsWith("http", ignoreCase = true) && url.length < 10) return null
        return url
    }

    private fun dist(i: Int, hit: UrlHit): Int {
        return when {
            i < hit.start -> hit.start - i
            i >= hit.end -> i - (hit.end - 1)
            else -> 0
        }
    }
}
