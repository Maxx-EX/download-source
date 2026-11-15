package com.downloadsource

import android.content.Context
import com.downloadsource.utils.PasswordCrypto

object ThemeStore {
    const val FOLLOW_SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2

    private const val PREF = "theme_store"
    private const val KEY_MODE = "mode"
    private const val KEY_STYLE = "style"
    private const val KEY_IGNORE_VER = "ignore_version"
    private const val KEY_LOCK_PWD_HASH = "lock_pwd_hash"

    fun get(context: Context): Int = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY_MODE, FOLLOW_SYSTEM)
    fun set(context: Context, value: Int) { context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt(KEY_MODE, value).apply() }

    fun getThemeStyle(context: Context): Int = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY_STYLE, 0)
    fun setThemeStyle(context: Context, value: Int) { context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt(KEY_STYLE, value).apply() }

    fun getIgnoreVersion(context: Context): String? = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_IGNORE_VER, null)
    fun setIgnoreVersion(context: Context, version: String) { context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_IGNORE_VER, version).apply() }

    fun getLockPassword(context: Context): String? = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_LOCK_PWD_HASH, null)
    fun setLockPassword(context: Context, password: String?) {
        val hash = if (password != null) PasswordCrypto.hashPassword(password) else null
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_LOCK_PWD_HASH, hash).apply()
    }
    fun verifyLockPassword(context: Context, input: String): Boolean = PasswordCrypto.verifyPassword(input, getLockPassword(context))
}
