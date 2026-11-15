package com.downloadsource

import android.content.Context
import com.downloadsource.utils.PasswordCrypto

object ThemeStore {
    private const val PREF = "theme_store"
    private const val KEY_LOCK_PWD_HASH = "lock_pwd_hash"

    fun getLockPassword(context: Context): String? = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_LOCK_PWD_HASH, null)
    
    // 保存时自动加密
    fun setLockPassword(context: Context, password: String?) {
        val hash = if (password != null) PasswordCrypto.hashPassword(password) else null
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_LOCK_PWD_HASH, hash).apply()
    }

    fun verifyLockPassword(context: Context, input: String): Boolean {
        val storedHash = getLockPassword(context)
        return PasswordCrypto.verifyPassword(input, storedHash)
    }
    
    // (保留之前的主题方法...)
    fun get(context: Context): Int = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt("mode", 0)
    fun set(context: Context, value: Int) { context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt("mode", value).apply() }
    fun getThemeStyle(context: Context): Int = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt("style", 0)
    fun setThemeStyle(context: Context, value: Int) { context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt("style", value).apply() }
    fun getIgnoreVersion(context: Context): String? = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("ignore_version", null)
    fun setIgnoreVersion(context: Context, version: String) { context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("ignore_version", version).apply() }
}
