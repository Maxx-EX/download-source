package com.downloadsource.web

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class SafeWebViewClient : WebViewClient() {

    private val blockedSchemes = setOf("baiduboxapp", "baiduboxlite", "intent", "market", "tbopen")
    private val blockedHosts = setOf(
        "cpro.baidu.com", "pos.baidu.com", "hm.baidu.com",
        "s.click.taobao.com", "uland.taobao.com"
    )

    override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
        val uri = req.url
        val scheme = uri.scheme?.lowercase()
        if (scheme in blockedSchemes) return true
        if (blockedHosts.any { uri.host?.endsWith(it) == true }) return true
        if (scheme == "http" || scheme == "https") return false
        return try {
            view.context.startActivity(Intent(Intent.ACTION_VIEW, uri)); true
        } catch (_: Exception) { true }
    }
}
