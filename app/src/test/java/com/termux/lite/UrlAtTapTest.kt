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
    fun bareDomainsOpenWithoutScheme() {
        assertEquals("https://google.com", UrlAtTap.urlAt("search on google.com now", 10))
        assertEquals("https://example.io/docs?q=1", UrlAtTap.urlAt("see example.io/docs?q=1 today", 6))
        assertEquals("https://Google.COM", UrlAtTap.urlAt("visit Google.COM.", 8))
    }

    @Test
    fun bareDomainRejectsFilenamesVersionsAndWords() {
        assertNull(UrlAtTap.urlAt("bash agy-install.sh next", 7))
        assertNull(UrlAtTap.urlAt("read notes.txt here", 7))
        assertNull(UrlAtTap.urlAt("released v1.2.6 today", 11))
        assertNull(UrlAtTap.urlAt("e.g something", 2))
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
    fun hardWrappedUrlIsHealedAcrossRows() {
        val line = "docs at https://github.com/radiator13/"
        val next = "TermuxLite/issues now"
        val healed = UrlAtTap.healHardWrapped(line, nextRow = { i -> if (i == 0) next else null })
        assertEquals(
            "https://github.com/radiator13/TermuxLite/issues",
            UrlAtTap.urlAt(healed, line.length - 2)
        )
    }

    @Test
    fun hardWrappedMidTokenSplitHealsWhenFragmentHasSlash() {
        val line = "open https://example.com/docs/Ter"
        val next = "minal/page here"
        val healed = UrlAtTap.healHardWrapped(line, nextRow = { i -> if (i == 0) next else null })
        assertEquals(
            "https://example.com/docs/Terminal/page",
            UrlAtTap.urlAt(healed, 8)
        )
    }

    @Test
    fun completeUrlIsNotGluedToNextSentence() {
        val line = "visit https://example.com today"
        // Next row starts a new sentence; nothing may be appended.
        val healed = UrlAtTap.healHardWrapped(line, nextRow = { i -> if (i == 0) "and enjoy." else null })
        assertEquals("https://example.com", UrlAtTap.urlAt(healed, 9))
    }

    @Test
    fun nonUrlTailIsNotHealed() {
        val line = "plain text ending in word"
        assertEquals(line, UrlAtTap.healHardWrapped(line, nextRow = { _ -> "more stuff" }))
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

    @Test
    fun xStatusUrlOpensExactTweet() {
        val url = "https://x.com/KesariPunjab/status/2082454538771169297"
        assertEquals(url, UrlAtTap.urlAt(url, 0))
        assertEquals(url, UrlAtTap.urlAt(url, url.length - 2))
        assertEquals(
            "https://x.com/TheAshwani_12/status/2090113724669694247",
            UrlAtTap.urlAt("x.com/TheAshwani_12/status/2090113724669694247", 0)
        )
    }

    @Test
    fun wrappedTweetIdIsHealedForwardAndBackward() {
        val head = "open https://x.com/foo/status/20901137246"
        val tail = "69694247 now"
        val full = "https://x.com/foo/status/2090113724669694247"
        val healed = UrlAtTap.healHardWrapped(head, nextRow = { i -> if (i == 0) tail else null })
        assertEquals(full, UrlAtTap.urlAt(healed, head.length - 2))

        val back = UrlAtTap.healHardWrappedBackward(tail, prevRow = { i -> if (i == 0) head else null })
        assertEquals(full, UrlAtTap.urlAt(back.first, back.second + 1))
    }

    @Test
    fun hyphenatedPathContinuationHeals() {
        val head = "https://www.tribuneindia.com/news/punjab/2-gangsters-held"
        val tail = "-for-firing-at-nurpur-bedi-scan-centre-after-extortion-demand-refused/"
        val healed = UrlAtTap.healHardWrapped(head, nextRow = { i -> if (i == 0) tail else null })
        assertEquals(
            "https://www.tribuneindia.com/news/punjab/2-gangsters-held-for-firing-at-nurpur-bedi-scan-centre-after-extortion-demand-refused/",
            UrlAtTap.urlAt(healed, 8)
        )
    }

    @Test
    fun markdownLabelAndNestedParensOpenHref() {
        val line = "See ([The Tribune](https://www.tribuneindia.com/news/foo)) now"
        val label = line.indexOf("Tribune")
        val href = line.indexOf("https")
        assertEquals("https://www.tribuneindia.com/news/foo", UrlAtTap.urlAt(line, label))
        assertEquals("https://www.tribuneindia.com/news/foo", UrlAtTap.urlAt(line, href))
    }

    @Test
    fun unclosedMarkdownStillOpensFromLabel() {
        val line = "See [the docs](https://example.com/docs"
        assertEquals("https://example.com/docs", UrlAtTap.urlAt(line, line.indexOf("docs")))
        assertEquals("https://example.com/docs", UrlAtTap.urlAt(line, line.indexOf("https")))
    }

    @Test
    fun markdownHrefSplitAcrossRowsHeals() {
        val head = "See [The Tribune]("
        val tail = "https://www.tribuneindia.com/news/foo)"
        val healed = UrlAtTap.healHardWrapped(head, nextRow = { i -> if (i == 0) tail else null })
        assertEquals("https://www.tribuneindia.com/news/foo", UrlAtTap.urlAt(healed, 6))
        assertEquals("https://www.tribuneindia.com/news/foo", UrlAtTap.urlAt(healed, healed.indexOf("https")))
    }

    @Test
    fun parenUrlOpensFromAdjacentLabel() {
        val line = "The Tribune (https://example.com/x) extra"
        assertEquals("https://example.com/x", UrlAtTap.urlAt(line, 0))
        assertEquals("https://example.com/x", UrlAtTap.urlAt(line, line.indexOf('h')))
    }

    @Test
    fun wikiBalancedParensStayInUrl() {
        val url = "https://en.wikipedia.org/wiki/Foo_(bar)"
        assertEquals(url, UrlAtTap.urlAt(url, 0))
        assertEquals(url, UrlAtTap.normalize("$url)."))
    }

    @Test
    fun magnetAndCustomSchemes() {
        assertEquals(
            "magnet:?xt=urn:btih:abc",
            UrlAtTap.urlAt("get magnet:?xt=urn:btih:abc now", 5)
        )
        assertEquals(
            "ssh://git@example.com/repo.git",
            UrlAtTap.urlAt("ssh://git@example.com/repo.git", 0)
        )
    }

    @Test
    fun twoMarkdownLinksTapTheRightLabel() {
        val line = "[one](https://one.example/a) then [two](https://two.example/b)"
        assertEquals("https://one.example/a", UrlAtTap.urlAt(line, line.indexOf("one")))
        assertEquals("https://two.example/b", UrlAtTap.urlAt(line, line.indexOf("two")))
    }

    @Test
    fun flipkartMultilineUrlWithDotAndIndentationHealsForwardAndBackward() {
        val head = "Flipkart https://www.flipkart."
        val tail = "  com/search?q=Omron+HEM-7156"
        val expected = "https://www.flipkart.com/search?q=Omron+HEM-7156"

        val healedForward = UrlAtTap.healHardWrapped(head, nextRow = { i -> if (i == 0) tail else null })
        assertEquals(expected, UrlAtTap.urlAt(healedForward, head.indexOf("https")))

        val back = UrlAtTap.healHardWrappedBackward(tail, prevRow = { i -> if (i == 0) head else null })
        assertEquals(expected, UrlAtTap.urlAt(back.first, back.second + 4))
    }

    @Test
    fun multilineQueryParamWithPlusHeals() {
        val head = "https://www.flipkart.com/search?q=Omron+"
        val tail = "  HEM-7156"
        val expected = "https://www.flipkart.com/search?q=Omron+HEM-7156"

        val healedForward = UrlAtTap.healHardWrapped(head, nextRow = { i -> if (i == 0) tail else null })
        assertEquals(expected, UrlAtTap.urlAt(healedForward, 0))

        val back = UrlAtTap.healHardWrappedBackward(tail, prevRow = { i -> if (i == 0) head else null })
        assertEquals(expected, UrlAtTap.urlAt(back.first, back.second + 3))
    }
}
