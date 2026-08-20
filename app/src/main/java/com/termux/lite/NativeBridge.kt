package com.termux.lite

import java.io.File

object NativeBridge {
    val isAvailable: Boolean = try {
        System.loadLibrary("termuxlite_native")
        nativeIsAvailable()
    } catch (_: Throwable) {
        false
    }

    fun findUrlAt(line: String, col: Int): String? {
        if (!isAvailable) return null
        return try {
            nativeFindUrlAt(line, col)
        } catch (_: Throwable) {
            null
        }
    }

    fun sha256File(file: File): String? {
        if (!isAvailable || !file.isFile) return null
        return try {
            nativeSha256File(file.absolutePath)
        } catch (_: Throwable) {
            null
        }
    }

    @JvmStatic
    private external fun nativeIsAvailable(): Boolean

    @JvmStatic
    private external fun nativeFindUrlAt(line: String, col: Int): String?

    @JvmStatic
    private external fun nativeSha256File(path: String): String?
}
