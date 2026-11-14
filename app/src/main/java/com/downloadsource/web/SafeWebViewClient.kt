package com.downloadsource.web

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

open class SafeWebViewClient(
    private val onDownloadRequest: (String) -> Unit = {},
    private val onPlayM3u8: (String) -> Unit = {}
) : WebViewClient() {

    private val blockedSchemes = setOf("baiduboxapp", "baiduboxlite", "intent", "market", "tbopen")
    private val downloadExtensions = listOf(".mp4", ".mkv", ".mp3", ".m3u8", ".zip", ".rar", ".apk", ".pdf", ".exe")

    // 广告拦截黑名单
    private val adHosts = setOf(
        "doubleclick.net", "google-analytics.com", "googlesyndication.com",
        "cpro.baidu.com", "pos.baidu.com", "hm.baidu.com", "s.click.taobao.com",
        "uland.taobao.com", "cnzz.com", "umeng.com"
    )

    // 修复：参数使用非空类型，与父类一致
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString().lowercase()
        if (adHosts.any { url.contains(it) }) {
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
        val uri = req.url
        val scheme = uri.scheme?.lowercase()
        val url = uri.toString()

        if (scheme in blockedSchemes) return true
        if (downloadExtensions.any { url.contains(it, ignoreCase = true) }) {
            if (url.contains(".m3u8")) onPlayM3u8(url) else onDownloadRequest(url)
            return true 
        }

        if (scheme == "http" || scheme == "https") return false
        return try {
            view.context.startActivity(Intent(Intent.ACTION_VIEW, uri)); true
        } catch (_: Exception) { true }
    }
}
