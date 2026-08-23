package com.termux.lite

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLifecycleTest {
    @Test
    fun lastSessionAlwaysAutoCloses() {
        assertTrue(shouldAutoCloseFinishedSession(0, 1))
        assertTrue(shouldAutoCloseFinishedSession(1, 1))
        assertTrue(shouldAutoCloseFinishedSession(137, 1))
        assertTrue(shouldAutoCloseFinishedSession(0, 0))
    }

    @Test
    fun extraSessionsAutoCloseCleanOrCtrlC() {
        assertTrue(shouldAutoCloseFinishedSession(0, 2))
        assertTrue(shouldAutoCloseFinishedSession(130, 3))
    }

    @Test
    fun extraSessionsKeepCrashesUntilEnter() {
        assertFalse(shouldAutoCloseFinishedSession(1, 2))
        assertFalse(shouldAutoCloseFinishedSession(137, 2))
        assertFalse(shouldAutoCloseFinishedSession(-9, 4))
    }
}
