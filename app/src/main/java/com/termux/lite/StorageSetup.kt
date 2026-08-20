package com.termux.lite

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.system.Os
import androidx.core.content.ContextCompat
import java.io.File

object StoragePermission {
    val RUNTIME = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    fun needsAllFilesPage(): Boolean = Build.VERSION.SDK_INT >= 30

    fun hasLegacyWrite(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun has(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager()
        }
        return hasLegacyWrite(context)
    }

    fun manageAppAllFilesIntent(context: Context): Intent {
        val uri = Uri.parse("package:${context.packageName}")
        return Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
    }

    fun manageAllFilesListIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }

    fun appDetailsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
    }
}

/**
 * Official termux-setup-storage layout: rebuild ~/storage as symlinks.
 * Child deletes are non-recursive so shared storage is never wiped.
 */
object StorageSetup {
    fun setup(context: Context): Result<File> {
        return try {
            val home = File(context.filesDir, "home")
            if (!home.exists() && !home.mkdirs()) {
                return Result.failure(IllegalStateException("could not create ${home.absolutePath}"))
            }
            val storageDir = File(home, "storage")
            if (storageDir.exists()) {
                storageDir.listFiles()?.forEach { child ->
                    child.delete()
                }
            }
            if (!storageDir.exists() && !storageDir.mkdirs()) {
                return Result.failure(IllegalStateException("could not create ${storageDir.absolutePath}"))
            }

            val shared = Environment.getExternalStorageDirectory()
            link(shared, File(storageDir, "shared"))
            publicLink(Environment.DIRECTORY_DOCUMENTS, File(storageDir, "documents"))
            publicLink(Environment.DIRECTORY_DOWNLOADS, File(storageDir, "downloads"))
            publicLink(Environment.DIRECTORY_DCIM, File(storageDir, "dcim"))
            publicLink(Environment.DIRECTORY_PICTURES, File(storageDir, "pictures"))
            publicLink(Environment.DIRECTORY_MUSIC, File(storageDir, "music"))
            publicLink(Environment.DIRECTORY_MOVIES, File(storageDir, "movies"))
            publicLink(Environment.DIRECTORY_PODCASTS, File(storageDir, "podcasts"))
            if (Build.VERSION.SDK_INT >= 29) {
                publicLink(Environment.DIRECTORY_AUDIOBOOKS, File(storageDir, "audiobooks"))
            }

            context.getExternalFilesDirs(null)?.forEachIndexed { i, dir ->
                if (dir != null) link(dir, File(storageDir, "external-$i"))
            }
            context.externalMediaDirs?.forEachIndexed { i, dir ->
                if (dir != null) link(dir, File(storageDir, "media-$i"))
            }

            // So `cd ~/sdcard` works even if the OS /sdcard node is picky.
            link(shared, File(home, "sdcard"))

            Result.success(storageDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun probeMessage(): String {
        val sd = File("/sdcard")
        val em = File("/storage/emulated/0")
        return "cd /sdcard " + readable(sd) +
            "  /storage/emulated/0 " + readable(em)
    }

    private fun readable(dir: File): String {
        return if (dir.exists() && dir.canRead()) "ok" else "denied"
    }

    private fun publicLink(type: String, link: File) {
        link(Environment.getExternalStoragePublicDirectory(type), link)
    }

    private fun link(target: File, link: File) {
        if (link.exists()) {
            link.delete()
        }
        Os.symlink(target.absolutePath, link.absolutePath)
    }
}
