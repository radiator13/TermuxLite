package com.termux.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TermThemeTest {
    @Test
    fun byIdReturnsExpectedThemes() {
        assertEquals("paper", TermThemes.byId("paper").id)
        assertEquals("dark", TermThemes.byId("dark").id)
        assertEquals("solarized-dark", TermThemes.byId("solarized-dark").id)
        assertEquals("solarized-light", TermThemes.byId("solarized-light").id)
        assertEquals("dracula", TermThemes.byId("dracula").id)
    }

    @Test
    fun byIdDefaultsToPaper() {
        assertEquals("paper", TermThemes.byId(null).id)
        assertEquals("paper", TermThemes.byId("").id)
        assertEquals("paper", TermThemes.byId("non-existent-theme").id)
    }

    @Test
    fun allThemesHaveSixteenAnsiColors() {
        assertEquals(5, TermThemes.all.size)
        for (theme in TermThemes.all) {
            assertEquals(16, theme.ansi.size)
            assertNotNull(theme.bgColor)
            assertNotNull(theme.fgColor)
            assertNotNull(theme.chromeColor)
            assertNotNull(theme.accentColor)
        }
    }
}
