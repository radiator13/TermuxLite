package com.termux.lite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles `termux-setup-storage`, which broadcasts
 * action `com.termux.app.reload_style` with extra "storage".
 */
class StorageActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!wantsStorage(intent)) return

        val launch = Intent(context, TermuxLiteActivity::class.java).apply {
            action = TermuxLiteActivity.ACTION_SETUP_STORAGE
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        }
        try {
            context.startActivity(launch)
        } catch (_: Exception) {
            if (StoragePermission.has(context)) {
                Thread { StorageSetup.setup(context) }.start()
            }
        }
    }

    companion object {
        const val ACTION_RELOAD_STYLE = "com.termux.app.reload_style"

        fun wantsStorage(intent: Intent?): Boolean {
            if (intent == null) return false
            if (intent.action == TermuxLiteActivity.ACTION_SETUP_STORAGE) return true
            if (intent.action != ACTION_RELOAD_STYLE) return false
            return intent.getStringExtra(ACTION_RELOAD_STYLE) == "storage"
        }
    }
}
