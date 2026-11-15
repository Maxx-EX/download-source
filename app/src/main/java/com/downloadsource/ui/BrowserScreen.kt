package com.downloadsource.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.downloadsource.core.DownloadManager
import com.downloadsource.storage.BrowserHistoryStore
import com.downloadsource.storage.BrowserPrefs
import com.downloadsource.storage.DirStore
import com.downloadsource.utils.PyRuleEngine
import com.downloadsource.web.SafeWebViewClient
import com.downloadsource.web.WebInterceptor
import java.io.File
import java.io.ByteArrayInputStream

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    onPlayVideo: (File) -> Unit, 
    onPlayStream: (String) -> Unit,
    triggerSniff: Boolean,
    onSniffHandled: () -> Unit,
    triggerHistory: Boolean,
    onHistoryHandled: () -> Unit,
    externalUrlToLoad: String?,
    onUrlLoaded: () -> Unit
) {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    val snifferType = BrowserPrefs.getSnifferType(ctx) // 0 = JS 引擎, 1 = Native(Python)引擎

    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(BrowserHistoryStore.getSession(ctx)) }
    var urlInput by remember { mutableStateOf(currentUrl) }
    var sniffedUrls = remember { mutableStateListOf<WebInterceptor.SniffedMedia>() }

    LaunchedEffect(externalUrlToLoad) {
        if (externalUrlToLoad != null) {
            webView?.loadUrl(externalUrlToLoad)
            onUrlLoaded()
        }
    }

    var showDownloadDialog by remember { mutableStateOf(false) }
    var pendingDownloadUrl by remember { mutableStateOf("") }
    var showSniffList by remember { mutableStateOf(false) }
    val webInterceptor = remember { WebInterceptor() }

    LaunchedEffect(Unit) {
        webInterceptor.sniffedData.collect { data -> 
            if (sniffedUrls.none { it.url == data.url } && sniffedUrls.size < 100) sniffedUrls.add(data) 
        }
    }

    DisposableEffect(Unit) { onDispose { webView?.destroy() } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 顶部栏 (简化)
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { webView?.goBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = palette.onSurface) }
                IconButton(onClick = { webView?.goForward() }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = palette.onSurface) }
                TextField(
                    value = urlInput, onValueChange = { urlInput = it },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).clip(RoundedCornerShape(20.dp)),
                    singleLine = true,
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = {
                        webView?.loadUrl(urlInput)
                    })
                )
                IconButton(onClick = { webView?.reload() }) { Icon(Icons.Default.Refresh, null, tint = palette.onSurface) }
            }

            AndroidView(
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // 修复网页打不开：允许混合内容
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        
                        addJavascriptInterface(webInterceptor, "AndroidBridge")

                        webViewClient = object : SafeWebViewClient(
                            onDownloadRequest = { url -> pendingDownloadUrl = url; showDownloadDialog = true },
                            onPlayM3u8 = { url -> onPlayStream(url) }
                        ) {
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                val url = request.url.toString().lowercase()
                                
                                // 核心：基于 Python 规则引擎的拦截
                                if (PyRuleEngine.shouldBlock(url)) {
                                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                                }
                                if (snifferType == 1 && PyRuleEngine.shouldSniff(url)) {
                                    if (sniffedUrls.none { it.url == url }) {
                                        sniffedUrls.add(WebInterceptor.SniffedMedia(url, null, false))
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                // JS 引擎
                                if (snifferType == 0) {
                                    view?.evaluateJavascript(webInterceptor.injectionScript, null)
                                }
                            }
                        }
                        loadUrl(currentUrl)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize().background(palette.surface)
            )
        }
    }

    if (showDownloadDialog) {
        AddTaskDialog(initialUrl = pendingDownloadUrl, onDismiss = { showDownloadDialog = false }) { url, name, threads, speed, headers ->
            DownloadManager.add(ctx, url, name, threads, speed, headers, DirStore.resolveDownloadDir(ctx))
            showDownloadDialog = false
        }
    }
}
