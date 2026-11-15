package com.downloadsource.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.downloadsource.core.DownloadManager
import com.downloadsource.storage.BrowserHistoryStore
import com.downloadsource.storage.BrowserPrefs
import com.downloadsource.storage.DirStore
import com.downloadsource.web.SafeWebViewClient
import com.downloadsource.web.WebInterceptor
import java.io.File
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    onPlayVideo: (File) -> Unit, onPlayStream: (String) -> Unit,
    triggerSniff: Boolean, onSniffHandled: () -> Unit,
    triggerHistory: Boolean, onHistoryHandled: () -> Unit,
    externalUrlToLoad: String?, onUrlLoaded: () -> Unit
) {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    val webView = remember {
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36"
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }
    }
    
    var currentUrl by remember { mutableStateOf(BrowserHistoryStore.getSession(ctx)) }
    var urlInput by remember { mutableStateOf(currentUrl) }
    var pendingDownloadUrl by remember { mutableStateOf("") }
    var showDownloadDialog by remember { mutableStateOf(false) }
    
    // 嗅探相关
    val sniffedUrls = remember { mutableStateListOf<WebInterceptor.SniffedMedia>() }
    var showSniffList by remember { mutableStateOf(false) }
    val webInterceptor = remember { WebInterceptor() }

    // 监听 JS 抓取结果
    LaunchedEffect(Unit) {
        webInterceptor.sniffedData.collect { data ->
            if (sniffedUrls.none { it.url == data.url } && sniffedUrls.size < 100) {
                sniffedUrls.add(data)
            }
        }
    }

    DisposableEffect(Unit) {
        webView.addJavascriptInterface(webInterceptor, "AndroidBridge")
        webView.webViewClient = object : SafeWebViewClient(
            onDownloadRequest = { url -> pendingDownloadUrl = url; showDownloadDialog = true },
            onPlayM3u8 = { url -> onPlayStream(url) }
        ) {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                val ext = url.substringAfterLast('.', "").lowercase().substringBefore('?')
                // 底层拦截兜底
                if (ext in listOf("mp4", "mp3", "m3u8", "apk", "zip", "ts", "mkv", "avi", "txt", "json", "pdf") && !url.contains("favicon")) {
                    if (sniffedUrls.none { it.url == url } && sniffedUrls.size < 100) {
                        sniffedUrls.add(WebInterceptor.SniffedMedia(url, null, false))
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 修复：确保页面加载完后注入终极嗅探脚本
                view?.evaluateJavascript(webInterceptor.injectionScript, null)
            }
        }
        webView.loadUrl(currentUrl)
        onDispose { webView.destroy() }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = urlInput, onValueChange = { urlInput = it },
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)),
                    placeholder = { Text("搜索或输入网址 (●'◡'●)") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
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
                            webView.loadUrl(target)
                            urlInput = target
                        }
                    })
                )
            }

            AndroidView(factory = { webView }, modifier = Modifier.weight(1f).fillMaxWidth())
            
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).clip(RoundedCornerShape(24.dp)).background(palette.surface).padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { webView.goBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "后退 (￣▽￣)", tint = palette.onSurface) }
                IconButton(onClick = { webView.goForward() }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "前进 (●'◡'●)", tint = palette.onSurface) }
                IconButton(onClick = { webView.loadUrl("https://www.bing.com") }) { Icon(Icons.Default.Home, "主页", tint = palette.onSurface) }
                IconButton(onClick = { webView.reload() }) { Icon(Icons.Default.Refresh, "刷新 (★ ω ★)", tint = palette.onSurface) }
            }
        }

        // 修复：让嗅探球永久显示在右下角（不再隐藏）
        FloatingActionButton(
            onClick = { 
                if (sniffedUrls.isEmpty()) {
                    android.widget.Toast.makeText(ctx, "暂未抓取到资源，请播放视频或刷新页面 (￣▽￣)", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    showSniffList = true 
                }
            },
            containerColor = if (sniffedUrls.isEmpty()) palette.onSurfaceDim.copy(alpha = 0.5f) else palette.primary.first(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 100.dp)
        ) {
            BadgedBox(badge = { 
                if (sniffedUrls.isNotEmpty()) Badge { Text(sniffedUrls.size.toString()) }
            }) {
                Icon(Icons.Default.Layers, "资源嗅探", tint = Color.White)
            }
        }
    }

    // 嗅探结果弹窗
    if (showSniffList) {
        Dialog(onDismissRequest = { showSniffList = false }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).clip(RoundedCornerShape(16.dp)).background(palette.surface).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("已抓取资源 (${sniffedUrls.size}) (★ ω ★)", fontWeight = FontWeight.Bold, color = palette.onSurface)
                    TextButton(onClick = { sniffedUrls.clear(); showSniffList = false }) { Text("清空 (￣▽￣)") }
                }
                LazyColumn {
                    items(sniffedUrls) { media ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { 
                            pendingDownloadUrl = media.url; showDownloadDialog = true; showSniffList = false 
                        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (media.isBlob) Icons.Default.BubbleChart else Icons.Default.PlayCircle, null, tint = palette.primary.first())
                            Spacer(Modifier.width(12.dp))
                            Text(if (media.isBlob) "加密/动态流 (Blob)" else media.url, maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.onSurface)
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
