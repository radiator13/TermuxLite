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

    // Bare domains worth linking WITHOUT a scheme (google.com, example.io/x).
    // Deliberately excludes extension-looking TLDs (.sh .py .rs .md .txt .apk)
    // so terminal filenames never turn into links.
    private val BARE_TLDS = setOf(
        "com", "org", "net", "edu", "gov", "mil", "int", "io", "ai", "app", "dev",
        "co", "me", "xyz", "info", "online", "site", "live", "club", "shop", "blog",
        "news", "cloud", "page", "tv", "cc", "gg", "fm", "is", "so", "to",
        "de", "fr", "uk", "us", "in", "ru", "jp", "cn", "kr", "br", "au", "ca",
        "nl", "se", "no", "es", "it", "pl", "ch", "at", "be", "dk", "fi", "pt",
        "cz", "eu", "nz", "za", "mx", "id", "th", "tr"
    )
    private val BARE_DOMAIN = Regex(
        """(?i)\b((?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:/[^\s<>"'\]\}>]*)?)"""
    )

    // Mirrors scanner.rs normalize_url: only these bare domains gain https://.
    private val HOSTED_PREFIXES = listOf(
        "github.com/",
        "gitlab.com/",
        "bitbucket.com/",
        "x.com/",
        "twitter.com/"
    )

    /** A URL cut off at end-of-line looks like an unfinished scheme URL. */
    private val CUT_OFF_URL = Regex(
        """(?i)(?:https?|ftps?|file)://\S+$|www\.\S+$|(?:github|gitlab|bitbucket)\.com/\S+$|(?:x|twitter)\.com/\S+$"""
    )
    private const val URL_CONTINUATION_CHARS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._~:/?#[]@!\$&'()*+,;%=-"

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

        fun rowText(r: Int): String? = try {
            emulator.getSelectedText(0, r, emulator.mColumns, r)?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }

        // Build the logical line from per-row extraction so we control joining
        // exactly (no hidden separators, no trimming asymmetry), and keep the
        // char offset of every row for precise tap-index math.
        val segments = ArrayList<String>(y2 - y1 + 1)
        var baseIndex = 0
        var haveTappedRow = false
        for (r in y1..y2) {
            val text = rowText(r) ?: run {
                segments.add("")
                continue
            }
            if (r < row) baseIndex += text.length
            if (r == row) haveTappedRow = true
            segments.add(text)
        }
        if (!haveTappedRow) return null
        var line = segments.joinToString("")
        val tappedSegment = segments.getOrNull(row - y1) ?: return null

        // Hard-wrapped output (explicit '\n' instead of LineWrap) splits URLs
        // across rows; heal by absorbing continuation rows while the tail of
        // the line still looks like an unfinished URL.
        line = healHardWrapped(line, nextRow = { i -> rowText(y2 + 1 + i)?.trimStart() })

        val indexInTapped = col.coerceIn(0, tappedSegment.length.coerceAtLeast(0))
        val index = baseIndex + indexInTapped
        return urlAt(line, index)
    }

    /**
     * If the line ends with what looks like a truncated URL, append following
     * rows (up to [maxRows]) while they continue it. Returns the healed line.
     */
    internal fun healHardWrapped(line: String, nextRow: (Int) -> String?, maxRows: Int = 4): String {
        var current = line
        repeat(maxRows) {
            val hits = findUrls(current)
            val tail = hits.lastOrNull()
            val candidate = tail?.takeIf { h ->
                h.end >= current.length - 1 &&
                    CUT_OFF_URL.containsMatchIn(h.url) &&
                    (h.url.last().isLetterOrDigit() || h.url.last() == '/')
            } ?: return current
            val continuation = nextRow(it)?.trimStart() ?: return current
            val token = continuation.substringBefore(' ').trimEnd()
            if (!isUrlContinuation(token, candidate.url)) return current
            current += token
        }
        return current
    }

    private fun isUrlContinuation(token: String, previousUrl: String): Boolean {
        if (token.isEmpty()) return false
        if (token.contains("://")) return false
        if (token.startsWith("www.", true)) return false
        if (!token[0].isLetterOrDigit()) return false
        if (token.endsWith(".")) return false
        if (!token.all { it in URL_CONTINUATION_CHARS }) return false
        // Only join when it plausibly continues a path: either the URL so far
        // ends at a directory boundary ('/') or the fragment itself carries one.
        return previousUrl.endsWith("/") || token.contains('/')
    }

    internal fun urlAt(line: String, col: Int): String? {
        if (line.isEmpty()) return null
        // The native scanner indexes spans in UTF-8 bytes; col is char-based.
        val nativeHit = NativeBridge.findUrlAt(line, utf8ByteOffsetOfCharIndex(line, col))
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
        BARE_DOMAIN.findAll(line).forEach { add(it.groups[1]!!.range, it.groupValues[1]) }
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
            HOSTED_PREFIXES.any { token.startsWith(it, ignoreCase = true) } -> "https://$token"
            isBareDomain(token) -> "https://$token"
            else -> return null
        }
        if (url.startsWith("http", ignoreCase = true) && url.length < 10) return null
        return url
    }

    /** True when the token's last host label (before any path) is a linkable TLD. */
    private fun isBareDomain(token: String): Boolean {
        val host = token.substringBefore('/').lowercase()
        if (!host.contains('.')) return false
        return host.substringAfterLast('.') in BARE_TLDS
    }

    internal fun utf8ByteOffsetOfCharIndex(line: String, charIndex: Int): Int {
        if (line.isEmpty() || charIndex <= 0) return 0
        val end = charIndex.coerceAtMost(line.length)
        return line.substring(0, end).toByteArray(Charsets.UTF_8).size
    }

    private fun dist(i: Int, hit: UrlHit): Int {
        return when {
            i < hit.start -> hit.start - i
            i >= hit.end -> i - (hit.end - 1)
            else -> 0
        }
    }
}
