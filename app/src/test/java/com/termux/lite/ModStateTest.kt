package com.termux.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModStateTest {
    @Test
    fun tapArmsThenClears() {
        assertEquals(ModState.Once, ModState.Off.tap())
        assertEquals(ModState.Off, ModState.Once.tap())
        assertEquals(ModState.Off, ModState.Locked.tap())
    }

    @Test
    fun longPressLocks() {
        assertEquals(ModState.Locked, ModState.Off.lock())
        assertEquals(ModState.Locked, ModState.Once.lock())
    }

    @Test
    fun consumeClearsOnceOnly() {
        assertEquals(ModState.Off, ModState.Once.consume())
        assertEquals(ModState.Locked, ModState.Locked.consume())
        assertEquals(ModState.Off, ModState.Off.consume())
    }

    @Test
    fun activeMeansOnceOrLocked() {
        assertFalse(ModState.Off.isActive())
        assertTrue(ModState.Once.isActive())
        assertTrue(ModState.Locked.isActive())
    }
}
