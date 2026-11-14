package com.downloadsource.storage

import android.content.Context
import android.webkit.CookieManager

object CookieStore {
    private const val PREF = "cookie_store"

    fun save(ctx: Context, platform: String, cookie: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(platform, cookie).apply()
    }

    fun load(ctx: Context, platform: String): String? =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(platform, null)

    // 从内置 WebView 同步指定平台 Cookie（登录后调用）
    fun syncFromWebView(ctx: Context, platform: String, baseUrl: String) {
        val manager = CookieManager.getInstance()
        val cookie = manager.getCookie(baseUrl)
        if (!cookie.isNullOrBlank()) save(ctx, platform, cookie)
    }
}
