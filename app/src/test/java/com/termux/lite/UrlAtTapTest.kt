package com.termux.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlAtTapTest {
    @Test
    fun httpsUrl() {
        val line = "see https://example.com/path for docs"
        assertEquals("https://example.com/path", UrlAtTap.urlAt(line, 10))
    }

    @Test
    fun httpUrl() {
        val line = "http://example.org"
        assertEquals("http://example.org", UrlAtTap.urlAt(line, 0))
    }

    @Test
    fun wwwBecomesHttps() {
        val line = "visit www.example.com today"
        assertEquals("https://www.example.com", UrlAtTap.urlAt(line, 8))
    }

    @Test
    fun stripsTrailingPunctuation() {
        val line = "https://example.com."
        assertEquals("https://example.com", UrlAtTap.urlAt(line, 3))
    }

    @Test
    fun ftpAndMailto() {
        assertEquals("ftp://files.example.com/a", UrlAtTap.urlAt("ftp://files.example.com/a", 0))
        assertEquals("mailto:hi@example.com", UrlAtTap.urlAt("email mailto:hi@example.com please", 8))
    }

    @Test
    fun markdownLabelOpensHref() {
        val line = "See [the docs](https://example.com/docs) now"
        assertEquals("https://example.com/docs", UrlAtTap.urlAt(line, 6))
        assertEquals("https://example.com/docs", UrlAtTap.urlAt(line, 20))
    }

    @Test
    fun angleBracketAndParens() {
        assertEquals("https://example.com/x", UrlAtTap.urlAt("<https://example.com/x>", 2))
        assertEquals("https://example.com/x", UrlAtTap.urlAt("go (https://example.com/x) next", 8))
    }

    @Test
    fun hostedPathsGetHttps() {
        assertEquals("https://github.com/termux/termux-app", UrlAtTap.urlAt("clone github.com/termux/termux-app", 8))
        assertEquals("https://x.com/foo", UrlAtTap.urlAt("x.com/foo", 0))
    }

    @Test
    fun singleUrlOnLineOpensFromAnywhere() {
        val line = "  https://example.com/agent-pr  "
        assertEquals("https://example.com/agent-pr", UrlAtTap.urlAt(line, 0))
        assertEquals("https://example.com/agent-pr", UrlAtTap.urlAt(line, line.lastIndex))
    }

    @Test
    fun wrappedJoinLooksLikeOneUrl() {
        val joined = "https://example.com/very/long/" + "path/that/wrapped"
        assertEquals(
            "https://example.com/very/long/path/that/wrapped",
            UrlAtTap.urlAt(joined, joined.length - 3)
        )
    }

    @Test
    fun rejectsNonUrls() {
        assertNull(UrlAtTap.urlAt("just some text", 4))
        assertNull(UrlAtTap.urlAt("", 0))
        assertTrue(UrlAtTap.findUrls("no links here").isEmpty())
        assertNull(UrlAtTap.normalize("not-a-host.example/path"))
    }

    @Test
    fun utf8ByteOffsetMatchesAsciiAndMultibyte() {
        assertEquals(0, UrlAtTap.utf8ByteOffsetOfCharIndex("abc", 0))
        assertEquals(3, UrlAtTap.utf8ByteOffsetOfCharIndex("abc", 3))
        assertEquals(2, UrlAtTap.utf8ByteOffsetOfCharIndex("éx", 1))
        val line = "café https://example.com"
        assertEquals("https://example.com", UrlAtTap.urlAt(line, line.indexOf('h')))
    }
}
