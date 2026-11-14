package com.downloadsource.storage

import android.content.Context

object BrowserPrefs {
    private const val PREF = "browser_prefs"
    private const val KEY_ENGINE = "search_engine"

    fun getEngine(ctx: Context): String = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_ENGINE, "Bing") ?: "Bing"
    fun setEngine(ctx: Context, engine: String) { ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_ENGINE, engine).apply() }
    
    // 新增：无痕模式
    fun isIncognito(ctx: Context): Boolean = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("incognito", false)
    fun setIncognito(ctx: Context, b: Boolean) { ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean("incognito", b).apply() }
}
