package com.downloadsource.web

import android.content.Context

object MirrorResolver {

    private val PREF = "mirrors"
    private val KEY_LIST = "list"

    // 默认镜像：GitHub 加速
    val DEFAULT = listOf(
        "https://ghproxy.com/",
        "https://gh-proxy.com/",
        "https://ghfast.top/",
        "https://kkgithub.com/",
        "" // 直连（放在末尾）
    )

    fun load(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_LIST, null)
        return if (raw.isNullOrBlank()) DEFAULT
        else raw.split('\n').filter { it.isNotBlank() || it == "" }
    }

    fun save(context: Context, list: List<String>) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_LIST, list.joinToString("\n")).apply()
    }

    /** GitHub 链接自动套加速前缀，其他直连 */
    fun resolve(context: Context, url: String): String {
        if (!url.contains("github.com") && !url.contains("githubusercontent.com")) return url
        val mirrors = load(context).filter { it.isNotBlank() }
        return mirrors.firstOrNull()?.let { m ->
            if (m.endsWith("/")) "$m$url" else "$m/$url"
        } ?: url
    }
}
