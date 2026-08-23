package com.termux.app

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Matches termux-tools `termux-open`, which broadcasts to
 * `com.termux/.app.TermuxOpenReceiver`. Files are exposed read-only under
 * `content://com.termux.files/`.
 */
class TermuxOpenReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val data = intent.data ?: return
        val contentTypeExtra = intent.getStringExtra("content-type")
        val useChooser = intent.getBooleanExtra("chooser", false)
        val action = when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_VIEW -> intent.action
            else -> Intent.ACTION_VIEW
        } ?: Intent.ACTION_VIEW

        val scheme = data.scheme
        if (scheme != null && !scheme.equals("file", ignoreCase = true)) {
            launchUri(context, action, data, contentTypeExtra, useChooser)
            return
        }

        val path = data.path
        if (path.isNullOrEmpty()) return
        val file = File(path)
        if (!file.isFile || !file.canRead()) return
        val canonical = try {
            file.canonicalPath
        } catch (_: IOException) {
            return
        }
        if (!allowedPath(context, canonical)) return

        val uri = Uri.Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .path(canonical)
            .build()
        val mime = contentTypeExtra ?: mimeFromName(file.name)
        val send = Intent(action).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        if (action == Intent.ACTION_SEND) {
            send.putExtra(Intent.EXTRA_STREAM, uri)
            send.type = mime
        } else {
            send.setDataAndType(uri, mime)
        }
        startExternal(context, if (useChooser) {
            Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            send
        })
    }

    private fun launchUri(
        context: Context,
        action: String,
        data: Uri,
        contentTypeExtra: String?,
        useChooser: Boolean
    ) {
        val urlIntent = Intent(action, data)
        if (action == Intent.ACTION_SEND) {
            urlIntent.putExtra(Intent.EXTRA_TEXT, data.toString())
            urlIntent.data = null
        } else if (contentTypeExtra != null) {
            urlIntent.setDataAndType(data, contentTypeExtra)
        }
        urlIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startExternal(
            context,
            if (useChooser) {
                Intent.createChooser(urlIntent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                urlIntent
            }
        )
    }

    private fun startExternal(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }

    class ContentProvider : android.content.ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            val file = File(uri.path ?: "")
            val cols = projection ?: arrayOf(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns._ID
            )
            val row = Array<Any?>(cols.size) { i ->
                when (cols[i]) {
                    MediaStore.MediaColumns.DISPLAY_NAME -> file.name
                    MediaStore.MediaColumns.SIZE -> file.length().toInt()
                    MediaStore.MediaColumns._ID -> 1
                    else -> null
                }
            }
            val cursor = MatrixCursor(cols)
            cursor.addRow(row)
            return cursor
        }

        override fun getType(uri: Uri): String? {
            val name = uri.lastPathSegment ?: return null
            return mimeFromName(name)
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = 0

        @Throws(FileNotFoundException::class)
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val raw = uri.path ?: throw FileNotFoundException("empty path")
            val file = File(raw)
            val path = try {
                file.canonicalPath
            } catch (e: IOException) {
                throw FileNotFoundException(e.message)
            }
            val ctx = context ?: throw FileNotFoundException("no context")
            if (!allowedPath(ctx, path)) {
                throw FileNotFoundException("path not allowed")
            }
            return ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
        }
    }

    companion object {
        const val AUTHORITY = "com.termux.files"

        fun allowedPath(context: Context, canonical: String): Boolean {
            val files = try {
                context.filesDir.canonicalPath
            } catch (_: IOException) {
                return false
            }
            val storage = try {
                Environment.getExternalStorageDirectory().canonicalPath
            } catch (_: IOException) {
                null
            }
            return under(canonical, files) || (storage != null && under(canonical, storage))
        }

        fun under(path: String, root: String): Boolean {
            return path == root || path.startsWith("$root/")
        }

        fun mimeFromName(name: String): String {
            val dot = name.lastIndexOf('.')
            if (dot >= 0 && dot < name.lastIndex) {
                val ext = name.substring(dot + 1).lowercase()
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                if (mime != null) return mime
            }
            return "application/octet-stream"
        }
    }
}
