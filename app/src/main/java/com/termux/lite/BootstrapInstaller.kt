package com.termux.lite

import android.system.Os
import android.util.Pair
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class BootstrapInstaller(
    private val paths: Paths,
    private val cancelled: () -> Boolean = { false },
    private val onState: (BootstrapState) -> Unit
) {
    fun ensureReady() {
        paths.ensureBaseDirs()
        when (
            PrefixGuard.decide(
                loginReady = paths.isLoginReady(),
                bashReady = paths.isBashReady(),
                populated = paths.isPopulated()
            )
        ) {
            PrefixGuard.Decision.UseLogin, PrefixGuard.Decision.UseBash -> {
                paths.markReady()
                onState(BootstrapState.Ready)
                return
            }
            PrefixGuard.Decision.KeepAndError -> {
                onState(BootstrapState.Error(PrefixGuard.KEEP_ERROR))
                return
            }
            PrefixGuard.Decision.WipeAndBootstrap -> {
                // empty / corrupt usr/ only — never a populated Termux prefix
            }
        }

        try {
            if (paths.staging.exists()) {
                paths.staging.deleteRecursively()
            }

            if (paths.prefix.exists()) {
                paths.prefix.deleteRecursively()
            }

            val zip = obtainZip()
            onState(BootstrapState.Extracting)
            extractToStaging(zip)
            if (!paths.staging.renameTo(paths.prefix)) {
                paths.staging.deleteRecursively()
                throw IOException("Could not move staging prefix into place")
            }

            paths.home.mkdirs()
            paths.tmp.mkdirs()

            if (paths.secondStage.isFile) {
                onState(BootstrapState.SecondStage)
                runSecondStage()
            }

            if (!paths.login.isFile || !paths.login.canExecute()) {
                throw IOException("bootstrap finished but ${paths.login} is not executable")
            }

            paths.marker.writeText("ok\n")
            zip.delete()
            onState(BootstrapState.Ready)
        } catch (e: Exception) {
            try {
                paths.staging.deleteRecursively()
                paths.marker.delete()
            } catch (_: Exception) {
            }
            onState(BootstrapState.Error(classify(e)))
        }
    }

    private fun checkCancelled() {
        if (cancelled()) throw IOException("cancelled")
    }

    private fun classify(e: Exception): String {
        val raw = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        return when {
            raw == "cancelled" -> "Download cancelled."
            raw.contains("checksum mismatch") ->
                "Checksum mismatch.\nThe file is corrupt or not the pinned bootstrap.\n$raw"
            raw.startsWith("HTTP") ||
                raw.contains("UnknownHost") ||
                raw.contains("Unable to resolve") ||
                raw.contains("Failed to connect") ||
                raw.contains("timeout", ignoreCase = true) ->
                "Network error.\nNeeds HTTPS to GitHub, once.\n$raw"
            else -> raw
        }
    }

    private fun obtainZip(): File {
        val cached = paths.zipCache
        if (cached.isFile && sha256(cached) == BootstrapConfig.SHA256) {
            return cached
        }
        cached.parentFile?.mkdirs()

        var lastError: Exception? = null
        repeat(BootstrapConfig.DOWNLOAD_RETRIES) { attempt ->
            try {
                if (attempt > 0) {
                    Thread.sleep(1000L * (1 shl (attempt - 1)))
                }
                download(cached)
                checkCancelled()
                val got = sha256(cached)
                if (got != BootstrapConfig.SHA256) {
                    cached.delete()
                    throw IOException("checksum mismatch: expected ${BootstrapConfig.SHA256}, got $got")
                }
                return cached
            } catch (e: Exception) {
                lastError = e
                if (e.message == "cancelled") throw e
                cached.delete()
            }
        }
        throw lastError ?: IOException("download failed")
    }

    private fun download(dest: File) {
        val tmp = File(dest.absolutePath + ".part")
        var offset = if (tmp.isFile) tmp.length() else 0L
        val conn = openFollow(BootstrapConfig.URL, offset)
        try {
            checkCancelled()
            val code = conn.responseCode
            val append = code == HttpURLConnection.HTTP_PARTIAL
            if (!append) {
                tmp.delete()
                offset = 0L
            }
            val remaining = conn.contentLengthLong
            val total = when {
                remaining > 0 && append -> offset + remaining
                remaining > 0 -> remaining
                else -> BootstrapConfig.SIZE_BYTES
            }
            onState(BootstrapState.Downloading(offset, total))
            conn.inputStream.use { input ->
                FileOutputStream(tmp, append).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var copied = offset
                    var lastUi = offset
                    while (true) {
                        checkCancelled()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        copied += n
                        if (copied - lastUi > 256 * 1024) {
                            lastUi = copied
                            onState(BootstrapState.Downloading(copied, total))
                        }
                    }
                    onState(BootstrapState.Downloading(copied, total))
                }
            }
        } finally {
            conn.disconnect()
        }
        checkCancelled()
        if (!tmp.renameTo(dest)) {
            dest.delete()
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        }
    }

    private fun openFollow(startUrl: String, rangeStart: Long = 0L): HttpURLConnection {
        var current = startUrl
        repeat(8) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("Accept", "application/octet-stream")
            conn.setRequestProperty("User-Agent", "TermuxLite/${BuildConfig.VERSION_NAME}")
            if (rangeStart > 0) {
                conn.setRequestProperty("Range", "bytes=$rangeStart-")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: throw IOException("redirect without Location")
                conn.disconnect()
                current = if (loc.startsWith("http")) loc else URL(URL(current), loc).toString()
                if (!current.startsWith("https://")) {
                    throw IOException("refusing non-HTTPS redirect")
                }
                return@repeat
            }
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                conn.disconnect()
                throw IOException("HTTP $code fetching bootstrap")
            }
            return conn
        }
        throw IOException("too many redirects")
    }

    private fun extractToStaging(zipFile: File) {
        if (paths.prefix.exists()) {
            throw IOException("refusing to extract over an existing prefix")
        }
        paths.staging.deleteRecursively()
        paths.staging.mkdirs()

        val symlinks = ArrayList<Pair<String, String>>(64)
        val buffer = ByteArray(8192)

        FileInputStream(zipFile).use { fileIn ->
            ZipInputStream(fileIn).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                var sawSymlinks = false
                while (entry != null) {
                    val name = entry.name
                    if (name.contains("..") || name.startsWith("/")) {
                        throw IOException("unsafe zip entry: $name")
                    }
                    if (name == "SYMLINKS.txt") {
                        sawSymlinks = true
                        readSymlinks(zip, symlinks)
                    } else {
                        val target = File(paths.staging, name)
                        val isDir = entry.isDirectory || name.endsWith("/")
                        val dir = if (isDir) target else target.parentFile
                        if (dir != null && !dir.exists() && !dir.mkdirs()) {
                            throw IOException("mkdir failed: $dir")
                        }
                        if (!isDir) {
                            FileOutputStream(target).use { out ->
                                while (true) {
                                    val n = zip.read(buffer)
                                    if (n < 0) break
                                    out.write(buffer, 0, n)
                                }
                            }
                            if (shouldBeExecutable(name)) {
                                Os.chmod(target.absolutePath, 448) // 0700
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                if (!sawSymlinks) {
                    throw IOException("bootstrap zip has no SYMLINKS.txt")
                }
            }
        }

        for (link in symlinks) {
            val parent = File(link.second).parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw IOException("mkdir failed for symlink parent: $parent")
            }
            Os.symlink(link.first, link.second)
        }
    }

    private fun readSymlinks(input: InputStream, out: MutableList<Pair<String, String>>) {
        val reader = BufferedReader(InputStreamReader(input))
        var line: String? = reader.readLine()
        while (line != null) {
            val parts = line.split("←")
            if (parts.size != 2) {
                throw IOException("malformed symlink line: $line")
            }
            val oldPath = parts[0]
            val rel = parts[1]
            if (rel.contains("..") || rel.startsWith("/")) {
                throw IOException("unsafe symlink dest: $rel")
            }
            out.add(Pair.create(oldPath, File(paths.staging, rel).absolutePath))
            line = reader.readLine()
        }
    }

    private fun shouldBeExecutable(name: String): Boolean {
        return name.startsWith("bin/") ||
            name.startsWith("libexec") ||
            name.startsWith("lib/apt/apt-helper") ||
            name.startsWith("lib/apt/methods")
    }

    private fun runSecondStage() {
        val bash = paths.bash
        if (!bash.canExecute()) {
            throw IOException("second-stage script present but bash is missing")
        }
        val pb = ProcessBuilder(bash.absolutePath, paths.secondStage.absolutePath)
        pb.directory(paths.home)
        pb.redirectErrorStream(true)
        val env = pb.environment()
        env["HOME"] = Paths.GUEST_HOME
        env["PREFIX"] = Paths.GUEST_PREFIX
        env["PATH"] = "${Paths.GUEST_PREFIX}/bin:/system/bin"
        env["TMPDIR"] = Paths.GUEST_TMP
        env["LD_LIBRARY_PATH"] = "${paths.prefix}/lib"
        env["TERM"] = "xterm-256color"
        env["LANG"] = "en_US.UTF-8"
        val proc = pb.start()
        val output = StringBuilder()
        proc.inputStream.bufferedReader().use { reader ->
            val deadline = System.currentTimeMillis() + BootstrapConfig.SECOND_STAGE_TIMEOUT_MS
            while (true) {
                if (System.currentTimeMillis() > deadline) {
                    proc.destroy()
                    throw IOException("second-stage timed out:\n${output.takeLast(800)}")
                }
                if (!reader.ready()) {
                    try {
                        if (proc.waitFor(100, TimeUnit.MILLISECONDS)) break
                    } catch (_: InterruptedException) {
                        proc.destroy()
                        throw IOException("second-stage interrupted")
                    }
                    continue
                }
                val ch = reader.read()
                if (ch < 0) break
                output.append(ch.toChar())
            }
        }
        val code = proc.waitFor()
        if (code != 0) {
            throw IOException("second-stage exit $code:\n${output.takeLast(800)}")
        }
    }

    companion object {
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { raw ->
                DigestInputStream(raw, digest).use { din ->
                    val buf = ByteArray(64 * 1024)
                    while (din.read(buf) >= 0) {
                        // digest updated by stream
                    }
                }
            }
            return digest.digest().joinToString("") { b -> "%02x".format(b) }
        }
    }
}
