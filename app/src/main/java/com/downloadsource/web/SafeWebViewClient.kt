package com.downloadsource.web

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

// 修复：加上 open 关键字，允许被匿名对象继承
open class SafeWebViewClient(
    private val onDownloadRequest: (String) -> Unit = {}
) : WebViewClient() {

    private val blockedSchemes = setOf("baiduboxapp", "baiduboxlite", "intent", "market", "tbopen")
    private val blockedHosts = setOf("cpro.baidu.com", "pos.baidu.com", "hm.baidu.com", "s.click.taobao.com", "uland.taobao.com")
    private val downloadExtensions = listOf(".mp4", ".mkv", ".mp3", ".m3u8", ".zip", ".rar", ".apk", ".pdf", ".exe")

    override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
        val uri = req.url
        val scheme = uri.scheme?.lowercase()
        val url = uri.toString()

        if (scheme in blockedSchemes) return true
        if (blockedHosts.any { uri.host?.endsWith(it) == true }) return true

        if (downloadExtensions.any { url.contains(it, ignoreCase = true) }) {
            onDownloadRequest(url)
            return true 
        }

        if (scheme == "http" || scheme == "https") return false
        return try {
            view.context.startActivity(Intent(Intent.ACTION_VIEW, uri)); true
        } catch (_: Exception) { true }
    }
}
