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
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled")
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
    var showDownloadDialog by remember { mutableStateOf(false) }
    var pendingDownloadUrl by remember { mutableStateOf("") }
    
    val sniffedUrls = remember { mutableStateListOf<String>() }
    var showSniffList by remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = urlInput, onValueChange = { urlInput = it }, placeholder = { Text("搜索或输入网址") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (urlInput.isNotEmpty()) IconButton(onClick = { urlInput = "" }) { Icon(Icons.Default.Close, null) } },
                    singleLine = true, modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        var q = urlInput.trim()
                        if (q.isNotEmpty()) {
                            val engine = BrowserPrefs.getEngine(ctx)
                            val target = if (q.startsWith("http")) q else {
                                q = URLEncoder.encode(q, "UTF-8")
                                when (engine) { "Baidu" -> "https://www.baidu.com/s?wd=$q"; "Google" -> "https://www.google.com/search?q=$q"; else -> "https://www.bing.com/search?q=$q" }
                            }
                            webView?.loadUrl(target)
                            urlInput = target
                        }
                    })
                )
            }

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
                        settings.mediaPlaybackRequiresUserGesture = false
                        // 允许 JS 注入弹窗
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        
                        webViewClient = object : WebViewClient() {
                            // 1. 嗅探拦截（解决无法捕捉深层请求问题）
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                val url = request?.url.toString()
                                val ext = url.substringAfterLast('.', "").lowercase().substringBefore('?')
                                if (ext in listOf("mp4", "mp3", "m3u8", "apk", "zip", "ts", "mkv", "avi") && !url.contains("favicon")) {
                                    if (!sniffedUrls.contains(url)) sniffedUrls.add(url)
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                            
                            // 2. 网页发生点击等操作时的 JS 拦截桥接
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url.toString()
                                if (url.contains(".m3u8") || url.contains(".apk") || url.contains(".mp4")) {
                                    pendingDownloadUrl = url
                                    showDownloadDialog = true
                                    return true // 拦截导航，转为下载
                                }
                                return false
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                if (url != null && !url.startsWith("javascript")) { urlInput = url; currentUrl = url }
                            }
                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                // 3. 注入终极抓取脚本 (抓取网页隐藏的下载链接和 Blob)
                                view?.evaluateJavascript("""
                                    javascript:(function(){
                                        document.addEventListener('click', function(e){
                                            var el = e.target.closest('a, button');
                                            if(el && el.href) {
                                                var href = el.href;
                                                if(href.includes('.m3u8') || href.includes('.mp4') || href.includes('.apk')){
                                                    window.location.href = href;
                                                }
                                            }
                                        }, true);
                                    })();
                                """.trimIndent(), null)
                            }
                        }
                        
                        // 4. HTML5 内的下载按钮 (点击直接触发)
                        setDownloadListener { url, _, _, _, _ -> 
                            pendingDownloadUrl = url
                            showDownloadDialog = true 
                        }
                        
                        webChromeClient = object : WebChromeClient() { override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress / 100f } }
                        loadUrl(currentUrl)
                        webView = this
                    }
                },
                update = { webView = it },
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(palette.surface)
            )
        }

        AnimatedVisibility(visible = sniffedUrls.isNotEmpty(), enter = scaleIn(), exit = scaleOut(), modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            FloatingActionButton(onClick = { showSniffList = true }, containerColor = palette.primary.first(), shape = RoundedCornerShape(16.dp)) {
                Box { Icon(Icons.Default.Layers, "Sniffed", tint = Color.White); Box(Modifier.size(10.dp).background(Color.Red, RoundedCornerShape(50)).align(Alignment.TopEnd)) }
            }
        }
    }

    if (showDownloadDialog) {
        AddTaskDialog(initialUrl = pendingDownloadUrl, onDismiss = { showDownloadDialog = false }) { url, name, threads ->
            DownloadManager.add(ctx, url, name, threads, DirStore.resolveDownloadDir(ctx))
            showDownloadDialog = false
        }
    }

    if (showSniffList) {
        Dialog(onDismissRequest = { showSniffList = false }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).clip(RoundedCornerShape(24.dp)).background(palette.surface).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("抓取到的媒体 (已去重)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.onSurface)
                    TextButton(onClick = { sniffedUrls.clear(); showSniffList = false }) { Text("清空") }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sniffedUrls) { url ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.onSurface.copy(alpha = 0.05f)).clickable { pendingDownloadUrl = url; showDownloadDialog = true; showSniffList = false }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val ext = url.substringAfterLast('.', "").lowercase().substringBefore('?')
                            Icon(when(ext) { "mp4", "mkv", "avi" -> Icons.Default.VideoFile; "mp3" -> Icons.Default.AudioFile; "apk" -> Icons.Default.Android; else -> Icons.Default.InsertDriveFile }, null, tint = palette.primary.first())
                            Spacer(Modifier.width(12.dp))
                            Text(url.substringAfterLast('/').substringBefore('?'), maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.onSurface, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
