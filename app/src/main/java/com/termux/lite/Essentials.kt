package com.termux.lite

import android.view.ContextMenu
import android.view.KeyEvent
import android.view.Menu
import android.view.View

/**
 * Context-menu and recents-key chrome stripping. Sessions, settings, and
 * theme live in the Compose UI instead of the system options menu.
 */
object Essentials {
    fun stripMenus(menu: Menu?) {
        menu?.clear()
    }

    fun stripContextMenu(menu: ContextMenu?) {
        menu?.clear()
        menu?.close()
    }

    fun swallowChromeKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_APP_SWITCH
    }

    fun disableContextMenu(view: View) {
        view.setOnCreateContextMenuListener { menu, _, _ ->
            stripContextMenu(menu)
        }
        view.isLongClickable = true
    }
}
