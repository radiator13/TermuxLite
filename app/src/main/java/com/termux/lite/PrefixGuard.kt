package com.termux.lite

/**
 * Decide whether an existing usr/ may be used, repaired via bash, or wiped.
 * Never wipe a prefix that already looks like a Termux install.
 */
object PrefixGuard {
    enum class Decision {
        UseLogin,
        UseBash,
        WipeAndBootstrap,
        KeepAndError
    }

    const val KEEP_ERROR =
        "Existing prefix has no usable login/bash.\n" +
            "Will not wipe usr/.\n" +
            "Rename usr/ from a file manager, then Retry."

    fun decide(loginReady: Boolean, bashReady: Boolean, populated: Boolean): Decision {
        if (loginReady) return Decision.UseLogin
        if (populated) {
            return if (bashReady) Decision.UseBash else Decision.KeepAndError
        }
        return Decision.WipeAndBootstrap
    }
}
