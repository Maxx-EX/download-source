package com.downloadsource

import android.content.Context

object ThemeStore {
    const val FOLLOW_SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2

    private const val PREF = "theme_store"
    private const val KEY = "mode"

    fun get(context: Context): Int =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY, FOLLOW_SYSTEM)

    fun set(context: Context, value: Int) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putInt(KEY, value).apply()
    }
}
