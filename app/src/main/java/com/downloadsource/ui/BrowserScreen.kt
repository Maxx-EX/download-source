package com.downloadsource.ui

import android.annotation.SuppressLint
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.downloadsource.core.DownloadManager
import com.downloadsource.storage.BrowserPrefs
import com.downloadsource.storage.DirStore
import com.downloadsource.web.SafeWebViewClient
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var urlInput by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("https://www.bing.com") }
    var progress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    // 引擎切换：从本地读取
    var engine by remember { mutableStateOf(BrowserPrefs.getEngine(ctx)) }
    val engines = listOf("Bing", "Baidu", "Google")
    var showEngineMenu by remember { mutableStateOf(false) }

    var showDownloadDialog by remember { mutableStateOf(false) }
    var pendingDownloadUrl by remember { mutableStateOf("") }

    // 资源嗅探列表
    val sniffedUrls = remember { mutableStateListOf<String>() }
    var showSniffList by remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 地址栏
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = { Text("搜索或输入网址") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (urlInput.isNotEmpty()) IconButton(onClick = { urlInput = "" }) { Icon(Icons.Default.Close, null) } },
                    singleLine = true,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.1f))), RoundedCornerShape(24.dp)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        var q = urlInput.trim()
                        if (q.isNotEmpty()) {
                            val target = if (q.startsWith("http")) q else {
                                // 修复引擎拼接 Bug
                                q = URLEncoder.encode(q, "UTF-8")
                                when (engine) {
                                    "Baidu" -> "https://www.baidu.com/s?wd=$q"
                                    "Google" -> "https://www.google.com/search?q=$q"
                                    else -> "https://www.bing.com/search?q=$q"
                                }
                            }
                            webView?.loadUrl(target)
                            urlInput = target
                        }
                    })
                )
                Box {
                    TextButton(onClick = { showEngineMenu = true }) { Text(engine, color = palette.onSurface) }
                    DropdownMenu(expanded = showEngineMenu, onDismissRequest = { showEngineMenu = false }) {
                        engines.forEach { e -> 
                            DropdownMenuItem(text = { Text(e) }, onClick = { 
                                engine = e
                                BrowserPrefs.setEngine(ctx, e) // 持久化
                                showEngineMenu = false 
                            }) 
                        }
                    }
                }
            }

            // 操作栏
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row {
                    IconButton(onClick = { webView?.goBack() }, enabled = canGoBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = if (canGoBack) palette.onSurface else palette.onSurfaceDim) }
                    IconButton(onClick = { webView?.goForward() }, enabled = canGoForward) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = if (canGoForward) palette.onSurface else palette.onSurfaceDim) }
                    IconButton(onClick = { webView?.reload() }) { Icon(Icons.Default.Refresh, null, tint = palette.onSurface) }
                }
                IconButton(onClick = { webView?.loadUrl("https://www.bing.com"); urlInput = "" }) { Icon(Icons.Default.Home, null, tint = palette.onSurface) }
            }

            if (progress in 0.01f..0.99f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))

            AndroidView(
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false // 允许自动播放

                        // 1. 嗅探拦截所有媒体资源
                        webViewClient = object : SafeWebViewClient(onDownloadRequest = { url ->
                            pendingDownloadUrl = url
                            showDownloadDialog = true
                        }) {
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                val url = request?.url.toString()
                                val ext = url.substringAfterLast('.', "").lowercase().substringBefore('?')
                                if (ext in listOf("mp4", "mp3", "m3u8", "apk", "zip", "ts", "mkv", "avi")) {
                                    if (!sniffedUrls.contains(url)) sniffedUrls.add(url)
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                if (url != null && !url.startsWith("javascript")) urlInput = url
                            }
                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                            }
                        }

                        // 2. 拦截网页内部直接触发的 APK 或文件下载
                        setDownloadListener { url, _, _, mimetype, _ ->
                            if (url.startsWith("http")) {
                                pendingDownloadUrl = url
                                showDownloadDialog = true
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress / 100f }
                        }
                        loadUrl(currentUrl)
                        webView = this
                    }
                },
                update = { webView = it },
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(palette.surface)
            )
        }

        // 悬浮嗅探按钮
        AnimatedVisibility(
            visible = sniffedUrls.isNotEmpty(),
            enter = scaleIn(),
            exit = scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) {
            FloatingActionButton(
                onClick = { showSniffList = true },
                containerColor = palette.primary.first(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box {
                    Icon(Icons.Default.Layers, "Sniffed", tint = Color.White)
                    // 红点提示
                    Box(Modifier.size(10.dp).background(Color.Red, RoundedCornerShape(50)).align(Alignment.TopEnd))
                }
            }
        }
    }

    // 下载弹窗
    if (showDownloadDialog) {
        AddTaskDialog(initialUrl = pendingDownloadUrl, onDismiss = { showDownloadDialog = false }) { url, name, threads ->
            DownloadManager.add(ctx, url, name, threads, DirStore.resolveDownloadDir(ctx))
            showDownloadDialog = false
            android.widget.Toast.makeText(ctx, "已加入下载队列 (๑•̀ㅂ•́)و✧", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // 嗅探结果弹窗
    if (showSniffList) {
        Dialog(onDismissRequest = { showSniffList = false }) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 500.dp).clip(RoundedCornerShape(24.dp)).background(palette.surface).padding(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("嗅探到的资源", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.onSurface)
                    TextButton(onClick = { sniffedUrls.clear(); showSniffList = false }) { Text("清空") }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sniffedUrls) { url ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.onSurface.copy(alpha = 0.05f)).clickable {
                                pendingDownloadUrl = url
                                showDownloadDialog = true
                                showSniffList = false
                            }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val ext = url.substringAfterLast('.', "").lowercase().substringBefore('?')
                            Icon(
                                when(ext) { "mp4", "mkv", "avi" -> Icons.Default.VideoFile; "mp3" -> Icons.Default.AudioFile; "apk" -> Icons.Default.Android; else -> Icons.Default.InsertDriveFile },
                                null, tint = palette.primary.first()
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(url.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.onSurface, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
