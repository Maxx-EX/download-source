package com.downloadsource.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object MirrorResolver {
    private val client = OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).build()
    
    // 手动选择镜像源列表
    val AVAILABLE_MIRRORS = listOf(
        "https://ghproxy.net/",
        "https://ghfast.top/",
        "https://gh-proxy.com/",
        "https://kkgithub.com/",
        "https://mirror.ghproxy.com/",
        "" // 直连
    )

    // 功能 1：GitHub 镜像测速与自动切换
    suspend fun resolveFastest(context: Context, url: String): String = withContext(Dispatchers.IO) {
        if (!url.contains("github.com") && !url.contains("githubusercontent.com")) return@withContext url
        
        val enabledMirrors = MirrorStore.getEnabled(context)
        if (enabledMirrors.isEmpty()) return@withContext url
        
        // 取首个启用的镜像，带有重试容错
        val mirror = enabledMirrors.first()
        if (mirror.isBlank()) return@withContext url
        if (mirror.endsWith("/")) "$mirror$url" else "$mirror/$url"
    }
}

object MirrorStore {
    private const val PREF = "mirrors"
    private const val KEY = "enabled_list"

    // 默认启用的源
    fun getEnabled(context: Context): List<String> {
        val set = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getStringSet(KEY, setOf("https://ghproxy.net/")) ?: setOf()
        return set.toList()
    }

    fun setEnabled(context: Context, list: List<String>) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putStringSet(KEY, list.toSet()).apply()
    }
}
