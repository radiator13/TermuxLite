package com.termux.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TermFontsTest {
    @Test
    fun defaultIsJetBrainsMono() {
        assertEquals("jetbrains-mono", TermFonts.DEFAULT_ID)
        assertEquals(TermFonts.JetBrainsMono, TermFonts.byId(null))
        assertEquals(TermFonts.JetBrainsMono, TermFonts.byId(""))
        assertEquals(TermFonts.JetBrainsMono, TermFonts.byId("nope"))
    }

    @Test
    fun catalogHasUniqueIdsAndEmbeddedRegularFaces() {
        val ids = TermFonts.all.map { it.id }
        assertEquals(ids.distinct(), ids)
        assertTrue(TermFonts.all.size >= 8)
        val bundled = TermFonts.all.filter { it.asset != null }
        assertTrue(bundled.size >= 7)
        bundled.forEach { font ->
            assertTrue(font.asset!!.startsWith("fonts/"))
            assertTrue(font.asset!!.endsWith(".ttf"))
            assertNotNull(font.label)
        }
        assertNull(TermFonts.System.asset)
        assertEquals("system", TermFonts.byId("system").id)
    }
}
