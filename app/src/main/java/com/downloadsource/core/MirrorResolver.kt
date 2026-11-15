package com.downloadsource.core

import android.content.Context

object MirrorResolver {
    private const val PREF = "mirrors"
    private const val KEY_LIST = "enabled_list"

    // 全面恢复 20 个最常用的镜像源（包含各大高校和云厂商）
    val AVAILABLE_MIRRORS = listOf(
        "https://mirrors.tuna.tsinghua.edu.cn/github-release/",
        "https://mirrors.aliyun.com/github-release/",
        "https://mirrors.ustc.edu.cn/github-release/",
        "https://mirrors.huaweicloud.com/github-release/",
        "https://mirrors.cloud.tencent.com/github-release/",
        "https://ghproxy.com/",
        "https://gh-proxy.com/",
        "https://ghfast.top/",
        "https://ghproxy.net/",
        "https://ghps.cc/",
        "https://hub.fastgit.xyz/",
        "https://kkgithub.com/",
        "https://gitclone.com/",
        "https://gh.api.99988866.xyz/",
        "https://ghproxy.cc/",
        "https://gh-proxy.net/",
        "https://download.fastgit.org/",
        "https://github.com.cnpmjs.org/",
        "https://mirror.ghproxy.com/",
        "" // 直连
    )

    fun load(context: Context): List<String> {
        val set = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getStringSet(KEY_LIST, setOf("https://ghproxy.com/", "")) ?: setOf("https://ghproxy.com/", "")
        return set.toList()
    }

    fun save(context: Context, list: List<String>) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_LIST, list.toSet()).apply()
    }

    fun resolveFastest(context: Context, url: String): String {
        if (!url.contains("github.com") && !url.contains("githubusercontent.com")) return url
        val enabledMirrors = load(context).filter { it.isNotBlank() }
        if (enabledMirrors.isEmpty()) return url
        val mirror = enabledMirrors.first()
        return if (mirror.endsWith("/")) "$mirror$url" else "$mirror/$url"
    }
}
