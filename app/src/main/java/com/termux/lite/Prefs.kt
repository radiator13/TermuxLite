package com.termux.lite

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val NAME = "termuxlite"
    const val KEY_TEXT_SIZE = "text_size"
    const val KEY_THEME = "theme_id"
    const val KEY_FONT = "font_id"
    const val KEY_EXTRA_KEYS = "extra_keys"
    const val KEY_KEEP_SCREEN_ON = "keep_screen_on"

    const val DEFAULT_TEXT_SIZE = 14
    const val MIN_TEXT_SIZE = 6
    const val MAX_TEXT_SIZE = 40

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        if (!::sp.isInitialized) {
            sp = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        }
    }

    var textSize: Int
        get() = sp.getInt(KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE).coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)
        set(value) {
            sp.edit().putInt(KEY_TEXT_SIZE, value.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)).apply()
        }

    var themeId: String
        get() = sp.getString(KEY_THEME, TermThemes.DEFAULT_ID) ?: TermThemes.DEFAULT_ID
        set(value) {
            sp.edit().putString(KEY_THEME, value).apply()
        }

    var fontId: String
        get() = TermFonts.byId(sp.getString(KEY_FONT, TermFonts.DEFAULT_ID)).id
        set(value) {
            sp.edit().putString(KEY_FONT, TermFonts.byId(value).id).apply()
        }

    var extraKeys: Boolean
        get() = sp.getBoolean(KEY_EXTRA_KEYS, true)
        set(value) {
            sp.edit().putBoolean(KEY_EXTRA_KEYS, value).apply()
        }

    var keepScreenOn: Boolean
        get() = sp.getBoolean(KEY_KEEP_SCREEN_ON, false)
        set(value) {
            sp.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()
        }
}
