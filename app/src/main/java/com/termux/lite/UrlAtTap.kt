package com.termux.lite

import android.view.MotionEvent
import com.termux.view.TerminalView

data class UrlHit(val start: Int, val end: Int, val url: String)

object UrlAtTap {
    private val ANGLE = Regex("""<(https?://[^>\s]+)>""")
    private val SCHEME = Regex(
        """(?i)\b((?:[a-z][a-z0-9+.-]{1,31})://[^\s<>"'\]\}>]+|mailto:[^\s<>"'\]\}>]+|magnet:\?[^\s<>"'\]\}>]+)"""
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
        """(?i)(?:[a-z][a-z0-9+.-]{1,31})://\S*$|www\.\S*$|(?:github|gitlab|bitbucket)\.com/\S*$|(?:x|twitter)\.com/\S*$|\]\(\S*$"""
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

        val indexInTapped = col.coerceIn(0, tappedSegment.length.coerceAtLeast(0))
        var index = baseIndex + indexInTapped

        // Hard-wrapped output (explicit '\n' instead of LineWrap) splits URLs
        // across rows; heal both directions so tapping a tweet-id tail still
        // opens the full status URL, and so `[label](` + next-row href works.
        val healedBack = healHardWrappedBackward(line, prevRow = { i -> rowText(y1 - 1 - i) })
        line = healedBack.first
        index += healedBack.second
        line = healHardWrapped(line, nextRow = { i -> rowText(y2 + 1 + i)?.trimStart() })

        return urlAt(line, index)
    }

    /**
     * If the line ends with what looks like a truncated URL, append following
     * rows (up to [maxRows]) while they continue it. Returns the healed line.
     */
    internal fun healHardWrapped(line: String, nextRow: (Int) -> String?, maxRows: Int = 8): String {
        var current = line
        repeat(maxRows) {
            val tail = current.substringAfterLast(' ')
            if (!needsForwardHeal(tail, current)) return current
            val continuation = nextRow(it)?.trimStart() ?: return current
            val token = continuation.substringBefore(' ').trimEnd()
            if (!isUrlContinuation(token, tail)) return current
            current += token
        }
        return current
    }

    /**
     * Prepend previous hard-wrapped rows when the tap landed on a URL tail
     * (tweet id digits, hyphenated path, markdown href, domain suffix). Returns healed line
     * and how many characters were prepended (for tap-index adjustment).
     */
    internal fun healHardWrappedBackward(
        line: String,
        prevRow: (Int) -> String?,
        maxRows: Int = 8
    ): Pair<String, Int> {
        var current = line
        var prepended = 0
        repeat(maxRows) {
            val head = current.trimStart().substringBefore(' ')
            val prev = prevRow(it) ?: return current to prepended
            val prevTail = prev.substringAfterLast(' ')
            if (!canJoinBackward(head, prevTail)) return current to prepended
            val trimmedHead = current.trimStart()
            val droppedSpaces = current.length - trimmedHead.length
            current = prev + trimmedHead
            prepended += prev.length - droppedSpaces
        }
        return current to prepended
    }

    private fun needsForwardHeal(tail: String, full: String): Boolean {
        if (tail.isEmpty()) return false
        val last = tail.last()
        if (full.endsWith("](") || tail.endsWith("](")) return true
        if (unclosedMarkdown(full)) return true
        return CUT_OFF_URL.containsMatchIn(tail) &&
            (last.isLetterOrDigit() || last in "/-_?&=%#@+(:;.~,")
    }

    private fun unclosedMarkdown(s: String): Boolean {
        val open = s.lastIndexOf("](")
        if (open < 0) return false
        return s.indexOf(')', open + 2) < 0
    }

    private fun canJoinBackward(head: String, prevTail: String): Boolean {
        if (head.isEmpty() || prevTail.isEmpty()) return false
        if (isUrlContinuation(head, prevTail)) return true
        if (!looksLikeMidUrlFragment(head)) return false
        return looksLikeTruncatedUrl(prevTail) ||
            looksLikeMidUrlFragment(prevTail) ||
            prevTail.endsWith("](") ||
            prevTail.contains("](")
    }

    private fun looksLikeTruncatedUrl(token: String): Boolean {
        if (token.endsWith("](") || token.contains("](")) return true
        if (token.isEmpty()) return false
        val last = token.last()
        return CUT_OFF_URL.containsMatchIn(token) &&
            (last.isLetterOrDigit() || last in "/-_?&=%#@+(:;.~,")
    }

    private fun looksLikeMidUrlFragment(token: String): Boolean {
        if (token.isEmpty()) return false
        if (!token.all { it in URL_CONTINUATION_CHARS }) return false
        if (token.contains("://") || token.startsWith("www.", true)) return true
        val c = token[0]
        if (c in "/-?&#%+") return true
        if (token.contains('/')) return true
        if (c.isDigit() && token.takeWhile { it.isDigit() }.length >= 3) return true
        if (token.contains('-') && token.any { it.isLetter() }) return true
        if (token.contains('.')) return true
        val domainPrefix = token.substringBefore('/')
        if (BARE_TLDS.contains(domainPrefix.lowercase())) return true
        return false
    }

    private fun isUrlContinuation(token: String, previousUrl: String): Boolean {
        if (token.isEmpty() || previousUrl.isEmpty()) return false
        if (token.endsWith(".") && !token.contains('/') && !token.contains('?')) return false
        if (!token.all { it in URL_CONTINUATION_CHARS }) return false
        val completingMarkdown = previousUrl.endsWith("](") || previousUrl.endsWith("(")
        if (token.contains("://") || token.startsWith("www.", true)) return completingMarkdown
        val prevIsUrl = previousUrl.contains("://") ||
            previousUrl.startsWith("www.", true) ||
            HOSTED_PREFIXES.any { previousUrl.contains(it, true) } ||
            completingMarkdown
        if (!prevIsUrl) return false
        val last = previousUrl.last()
        if (last in "/-_?&=%#@+(:;.~,") {
            return token[0].isLetterOrDigit() || token[0] in "-._~/%#?&=+("
        }
        if (token[0] in "/-?&#%+") return true
        if (token.any { it in "/?&=%#@+" }) return true
        val digitRun = previousUrl.takeLastWhile { it.isDigit() }.length
        if (digitRun >= 4 && token[0].isDigit()) return true
        return false
    }

    internal fun urlAt(line: String, col: Int): String? {
        if (line.isEmpty()) return null
        val i = col.coerceIn(0, line.lastIndex)
        val hits = findUrls(line)
        hits.firstOrNull { i in it.start until it.end }?.let { return it.url }

        // Native scanner is a fallback only. Kotlin hits include markdown
        // labels and wrap-healed tweet ids that the native path can truncate.
        val nativeHit = NativeBridge.findUrlAt(line, utf8ByteOffsetOfCharIndex(line, i))
        if (nativeHit != null) {
            hits.map { it.url }
                .filter { it.startsWith(nativeHit) || nativeHit.startsWith(it) }
                .maxByOrNull { it.length }
                ?.let { return if (it.length >= nativeHit.length) it else nativeHit }
            return nativeHit
        }
        if (hits.isEmpty()) return null
        val nearby = hits.minBy { dist(i, it) }
        if (dist(i, nearby) <= 3) return nearby.url
        return hits.singleOrNull()?.url
    }

    internal fun findUrls(line: String): List<UrlHit> {
        val hits = ArrayList<UrlHit>()
        fun add(start: Int, end: Int, raw: String) {
            val url = normalize(raw) ?: return
            if (start >= end) return
            if (hits.any { start < it.end && end > it.start && it.url == url }) return
            hits.add(UrlHit(start, end, url))
        }

        scanMarkdown(line, ::add)
        ANGLE.findAll(line).forEach { add(it.range.first, it.range.last + 1, it.groupValues[1]) }
        SCHEME.findAll(line).forEach { m ->
            val range = m.groups[1]!!.range
            val start = labelStartBeforeParen(line, range.first)
            add(start, range.last + 1, m.groupValues[1])
        }
        WWW.findAll(line).forEach { add(it.groups[1]!!.range.first, it.groups[1]!!.range.last + 1, it.groupValues[1]) }
        HOSTED.findAll(line).forEach { add(it.groups[1]!!.range.first, it.groups[1]!!.range.last + 1, it.groupValues[1]) }
        BARE_DOMAIN.findAll(line).forEach { add(it.groups[1]!!.range.first, it.groups[1]!!.range.last + 1, it.groupValues[1]) }
        hits.sortBy { it.start }
        return hits
    }

    /**
     * `[label](url)` including unclosed hrefs and balanced parens in the URL
     * (`wiki/Foo_(bar)`). The hit covers the label so tapping the visible text
     * opens the href behind it.
     */
    private fun scanMarkdown(line: String, add: (Int, Int, String) -> Unit) {
        var i = 0
        while (i < line.length) {
            val lb = line.indexOf('[', i)
            if (lb < 0) break
            val rb = line.indexOf(']', lb + 1)
            if (rb < 0 || rb + 1 >= line.length || line[rb + 1] != '(') {
                i = if (lb + 1 > i) lb + 1 else i + 1
                continue
            }
            val urlStart = rb + 2
            var depth = 1
            var k = urlStart
            while (k < line.length && depth > 0) {
                when (line[k]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                if (depth == 0) break
                k++
            }
            val urlEnd = if (depth == 0) k else line.length
            val raw = line.substring(urlStart, urlEnd).trim().substringBefore(' ')
            val hitEnd = if (depth == 0) k + 1 else (urlStart + raw.length).coerceAtMost(urlEnd)
            add(lb, hitEnd, raw)
            i = hitEnd.coerceAtLeast(lb + 1)
        }
    }

    /**
     * For `The Tribune (https://…)` extend the hit over the adjacent label so
     * tapping the text in front of a parenthesized URL still opens it.
     */
    private fun labelStartBeforeParen(line: String, urlStart: Int): Int {
        var s = urlStart
        if (s > 0 && line[s] != '(' && line[s - 1] == '(') s--
        if (s <= 0 || line[s] != '(') return urlStart
        var i = s - 1
        while (i >= 0 && line[i] == ' ') i--
        if (i < 0) return urlStart
        // `[label](url)` is handled by scanMarkdown.
        if (line[i] == ']') return urlStart
        val labelEnd = i + 1
        while (i >= 0 && line[i] !in "\t()[]{}<>\"'") {
            if (line[i] == '.' && (i + 1 >= line.length || line[i + 1] == ' ')) break
            i--
        }
        val start = i + 1
        val label = line.substring(start, labelEnd).trim()
        return if (label.length in 2..80) start else urlStart
    }

    internal fun normalize(raw: String): String? {
        var token = raw.trim()
        while (token.isNotEmpty() && token.first() in "([{\"'<") {
            token = token.drop(1)
        }
        while (token.isNotEmpty() && token.last() in ".,;:!?)]}>\"'") {
            val last = token.last()
            if (last == ')' && token.count { it == '(' } > token.dropLast(1).count { it == ')' }) break
            token = token.dropLast(1)
        }
        if (token.length < 4) return null
        val url = when {
            token.startsWith("https://", ignoreCase = true) -> token
            token.startsWith("http://", ignoreCase = true) -> token
            token.startsWith("ftp://", ignoreCase = true) -> token
            token.startsWith("ftps://", ignoreCase = true) -> token
            token.startsWith("file://", ignoreCase = true) -> token
            token.startsWith("mailto:", ignoreCase = true) -> token
            token.startsWith("magnet:", ignoreCase = true) -> token
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
