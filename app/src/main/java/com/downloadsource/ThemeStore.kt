package com.downloadsource

import android.content.Context

object ThemeStore {
    const val FOLLOW_SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2
    
    private const val PREF = "theme_store"
    private const val KEY_MODE = "mode"
    private const val KEY_STYLE = "style"

    fun get(context: Context): Int = 
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY_MODE, FOLLOW_SYSTEM)
        
    fun set(context: Context, value: Int) { 
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt(KEY_MODE, value).apply() 
    }

    // 新增：主题风格的读取与保存
    fun getThemeStyle(context: Context): Int = 
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY_STYLE, 0)
        
    fun setThemeStyle(context: Context, value: Int) { 
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt(KEY_STYLE, value).apply() 
    }
}
