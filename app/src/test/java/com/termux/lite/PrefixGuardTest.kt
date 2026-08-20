package com.termux.lite

import org.junit.Assert.assertEquals
import org.junit.Test

class PrefixGuardTest {
    @Test
    fun loginReadyUsesLoginEvenIfPopulated() {
        assertEquals(
            PrefixGuard.Decision.UseLogin,
            PrefixGuard.decide(loginReady = true, bashReady = true, populated = true)
        )
    }

    @Test
    fun populatedWithoutLoginFallsBackToBash() {
        assertEquals(
            PrefixGuard.Decision.UseBash,
            PrefixGuard.decide(loginReady = false, bashReady = true, populated = true)
        )
    }

    @Test
    fun populatedWithoutShellDoesNotWipe() {
        assertEquals(
            PrefixGuard.Decision.KeepAndError,
            PrefixGuard.decide(loginReady = false, bashReady = false, populated = true)
        )
    }

    @Test
    fun emptyPrefixMayWipeAndBootstrap() {
        assertEquals(
            PrefixGuard.Decision.WipeAndBootstrap,
            PrefixGuard.decide(loginReady = false, bashReady = false, populated = false)
        )
    }

    @Test
    fun bashAloneOnEmptyPrefixIsWipeNotUseBash() {
        // bash file without a populated tree is treated as leftover extract
        assertEquals(
            PrefixGuard.Decision.WipeAndBootstrap,
            PrefixGuard.decide(loginReady = false, bashReady = true, populated = false)
        )
    }
}
