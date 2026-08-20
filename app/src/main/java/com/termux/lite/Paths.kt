package com.termux.lite

import android.content.Context
import java.io.File

/**
 * Native Termux layout. applicationId is com.termux, so filesDir is
 * /data/data/com.termux/files and official packages land in the right prefix.
 */
class Paths(context: Context) {
    val files: File = context.filesDir
    val home: File = File(files, "home")
    val prefix: File = File(files, "usr")
    val staging: File = File(files, "usr-staging")
    val tmp: File = File(prefix, "tmp")
    val login: File = File(prefix, "bin/login")
    val bash: File = File(prefix, "bin/bash")
    val apt: File = File(prefix, "bin/apt")
    val dpkg: File = File(prefix, "var/lib/dpkg")
    val marker: File = File(prefix, MARKER_NAME)
    val secondStage: File = File(prefix, "etc/termux/bootstrap/termux-bootstrap-second-stage.sh")
    val zipCache: File = File(context.cacheDir, "bootstrap-aarch64.zip")

    fun ensureBaseDirs() {
        files.mkdirs()
        home.mkdirs()
    }

    fun isLoginReady(): Boolean = login.isFile && login.canExecute()

    fun isBashReady(): Boolean = bash.isFile && bash.canExecute()

    fun isPopulated(): Boolean = bash.isFile || apt.isFile || dpkg.isDirectory

    fun isUsable(): Boolean = isLoginReady() || isBashReady()

    fun guestShell(): String {
        return if (isLoginReady()) GUEST_LOGIN else GUEST_BASH
    }

    fun isReady(): Boolean {
        return marker.isFile && isUsable()
    }

    fun markReady() {
        home.mkdirs()
        tmp.mkdirs()
        if (!marker.isFile) {
            marker.writeText("ok\n")
        }
    }

    companion object {
        const val MARKER_NAME = ".termux-lite-ok"
        const val GUEST_FILES = "/data/data/com.termux/files"
        const val GUEST_HOME = "/data/data/com.termux/files/home"
        const val GUEST_PREFIX = "/data/data/com.termux/files/usr"
        const val GUEST_TMP = "/data/data/com.termux/files/usr/tmp"
        const val GUEST_LOGIN = "/data/data/com.termux/files/usr/bin/login"
        const val GUEST_BASH = "/data/data/com.termux/files/usr/bin/bash"
    }
}
