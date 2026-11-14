package com.downloadsource.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.downloadsource.web.SafeWebViewClient
import java.io.File
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(onPlayVideo: (File) -> Unit, onPlayStream: (String) -> Unit) {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(BrowserHistoryStore.getSession(ctx)) }
    var urlInput by remember { mutableStateOf(currentUrl) }
    // 修复：使用 MutableSet 自动去重并限制上限
    val sniffedUrls = remember { mutableStateSetOf<String>() }
    
    var showDownloadDialog by remember { mutableStateOf(false) }
    var pendingDownloadUrl by remember { mutableStateOf("") }
    var showSniffList by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { webView?.destroy() } }

    BackHandler(enabled = webView?.canGoBack() == true) { webView?.goBack() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 顶部地址栏
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = urlInput, onValueChange = { urlInput = it }, modifier = Modifier.weight(1f),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = {
                        val q = urlInput.trim()
                        if (q.isNotEmpty()) {
                            val target = if (q.startsWith("http")) q else {
                                val engine = BrowserPrefs.getEngine(ctx)
                                val encoded = URLEncoder.encode(q, "UTF-8")
                                when (engine) {
                                    "Baidu" -> "https://www.baidu.com/s?wd=$encoded"
                                    "Google" -> "https://www.google.com/search?q=$encoded"
                                    else -> "https://www.bing.com/search?q=$encoded"
                                }
                            }
                            webView?.loadUrl(target)
                        }
                    })
                )
            }

            AndroidView(
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        // WebView 安全基线与多窗口支持
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.setSupportMultipleWindows(true)
                        
                        webViewClient = object : SafeWebViewClient(
                            onDownloadRequest = { url -> pendingDownloadUrl = url; showDownloadDialog = true },
                            onPlayM3u8 = { url -> onPlayStream(url) }
                        ) {
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                val url = request.url.toString()
                                // 修复：放行验证码
                                if (url.contains("captcha") || url.contains("verify")) return null
                                
                                val ext = url.substringAfterLast('.', "").lowercase().substringBefore('?')
                                if (ext in listOf("mp4", "mp3", "m3u8", "apk", "zip") && !url.contains("favicon")) {
                                    if (sniffedUrls.size < 50) sniffedUrls.add(url)
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                if (url != null) { urlInput = url; currentUrl = url; BrowserHistoryStore.saveSession(ctx, url) }
                            }
                        }
                        
                        // 支持文件选择
                        webChromeClient = object : WebChromeClient() {
                            override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
                                // 可接入 ActivityResultLauncher 实现文件选择，此处直接返回 false 放行默认（如果有）
                                return false 
                            }
                        }
                        
                        setDownloadListener { url, _, _, _, _ -> pendingDownloadUrl = url; showDownloadDialog = true }
                        loadUrl(currentUrl)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize().background(palette.surface)
            )
        }

        // 嗅探悬浮球
        AnimatedVisibility(visible = sniffedUrls.isNotEmpty(), enter = scaleIn(), exit = scaleOut(), modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            FloatingActionButton(onClick = { showSniffList = true }, containerColor = palette.primary.first()) {
                Icon(Icons.Default.Layers, null, tint = Color.White)
            }
        }
    }

    if (showSniffList) {
        Dialog(onDismissRequest = { showSniffList = false }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 400.dp).clip(RoundedCornerShape(16.dp)).background(palette.surface).padding(16.dp)) {
                LazyColumn {
                    items(sniffedUrls.toList()) { url ->
                        Row(Modifier.fillMaxWidth().clickable { pendingDownloadUrl = url; showDownloadDialog = true; showSniffList = false }.padding(12.dp)) {
                            Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    if (showDownloadDialog) {
        AddTaskDialog(initialUrl = pendingDownloadUrl, onDismiss = { showDownloadDialog = false }) { url, name, threads, speed, headers ->
            DownloadManager.add(ctx, url, name, threads, speed, headers, DirStore.resolveDownloadDir(ctx))
            showDownloadDialog = false
        }
    }
}

fun <T> mutableStateSetOf(): MutableSet<T> {
    return androidx.compose.runtime.mutableStateSetOf<T>()
}
