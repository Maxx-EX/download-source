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
import com.downloadsource.storage.BookmarkStore
import com.downloadsource.storage.BrowserHistoryStore
import com.downloadsource.storage.BrowserPrefs
import com.downloadsource.storage.DirStore
import com.downloadsource.web.SafeWebViewClient
import java.io.File
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(onPlayVideo: (File) -> Unit, onPlayStream: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(BrowserHistoryStore.getSession(ctx)) }
    var urlInput by remember { mutableStateOf(currentUrl) }
    var progress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    
    var showDownloadDialog by remember { mutableStateOf(false) }
    var pendingDownloadUrl by remember { mutableStateOf("") }
    val sniffedUrls = remember { mutableStateListOf<String>() }
    
    var showMenuSheet by remember { mutableStateOf(false) }
    var showSniffList by remember { mutableStateOf(false) }
    var showHistoryList by remember { mutableStateOf(false) }
    var showBookmarkList by remember { mutableStateOf(false) }
    
    var isDarkMode by remember { mutableStateOf(false) }

    // 销毁时释放 WebView 防止内存泄漏闪退
    DisposableEffect(Unit) {
        onDispose { webView?.destroy() }
    }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = urlInput, onValueChange = { urlInput = it }, placeholder = { Text("搜索或输入网址") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (urlInput.isNotEmpty()) IconButton(onClick = { urlInput = "" }) { Icon(Icons.Default.Close, null) } },
                    singleLine = true, modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.1f))), RoundedCornerShape(24.dp)),
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
                Row {
                    IconButton(onClick = { if (currentUrl.isNotBlank()) { BookmarkStore.add(ctx, currentUrl); android.widget.Toast.makeText(ctx, "已加入书签", android.widget.Toast.LENGTH_SHORT).show() } }) { Icon(Icons.Default.Star, null, tint = if (BookmarkStore.get(ctx).contains(currentUrl)) Color(0xFFFFD700) else palette.onSurface) }
                    IconButton(onClick = { showMenuSheet = true }) { Icon(Icons.Default.MoreVert, null, tint = palette.onSurface) }
                }
            }

            if (progress in 0.01f..0.99f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))

            AndroidView(
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            settings.forceDark = if (isDarkMode) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
                        }

                        webViewClient = object : SafeWebViewClient(
                            onDownloadRequest = { url -> pendingDownloadUrl = url; showDownloadDialog = true },
                            onPlayM3u8 = { url -> onPlayStream(url) }
                        ) {
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                val url = request.url.toString()
                                val ext = url.substringAfterLast('.', "").lowercase().substringBefore('?')
                                if (ext in listOf("mp4", "mp3", "m3u8", "apk", "zip", "ts", "mkv", "avi") && !url.contains("favicon")) {
                                    if (!sniffedUrls.contains(url)) sniffedUrls.add(url)
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                if (url != null && !url.startsWith("javascript")) { urlInput = url; currentUrl = url; BrowserHistoryStore.saveSession(ctx, url) }
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (url != null) BrowserHistoryStore.addHistory(ctx, url)
                            }
                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                            }
                        }
                        setDownloadListener { url, _, _, _, _ -> pendingDownloadUrl = url; showDownloadDialog = true }
                        webChromeClient = object : WebChromeClient() { override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress / 100f } }
                        loadUrl(currentUrl)
                        webView = this
                    }
                },
                update = { 
                    webView = it 
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        it.settings.forceDark = if (isDarkMode) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
                    }
                },
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(palette.surface)
            )
        }

        AnimatedVisibility(visible = sniffedUrls.isNotEmpty(), enter = scaleIn(), exit = scaleOut(), modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            FloatingActionButton(onClick = { showSniffList = true }, containerColor = palette.primary.first(), shape = RoundedCornerShape(16.dp)) {
                Box { Icon(Icons.Default.Layers, "Sniffed", tint = Color.White); Box(Modifier.size(10.dp).background(Color.Red, RoundedCornerShape(50)).align(Alignment.TopEnd)) }
            }
        }
    }

    if (showMenuSheet) {
        ModalBottomSheet(onDismissRequest = { showMenuSheet = false }, containerColor = palette.surface) {
            Column(Modifier.padding(16.dp)) {
                Text("浏览器操作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.onSurface, modifier = Modifier.padding(bottom = 12.dp))
                BrowserMenuItem("回到主页", Icons.Default.Home, palette.onSurface) { webView?.loadUrl("https://www.bing.com"); showMenuSheet = false }
                
                // 功能：提取网页所有图片
                BrowserMenuItem("提取网页图片", Icons.Default.Image, palette.onSurface) {
                    webView?.evaluateJavascript("""
                        (function() {
                            var imgs = document.getElementsByTagName('img');
                            var urls = [];
                            for (var i=0; i<imgs.length; i++) {
                                if (imgs[i].src && imgs[i].width > 100) urls.push(imgs[i].src);
                            }
                            window.location.href = 'downloadsource_pic://' + JSON.stringify(urls);
                        })();
                    """.trimIndent()) { _ -> }
                    showMenuSheet = false
                    android.widget.Toast.makeText(ctx, "正在提取图片...", android.widget.Toast.LENGTH_SHORT).show()
                }

                BrowserMenuItem("历史记录", Icons.Default.History, palette.onSurface) { showHistoryList = true; showMenuSheet = false }
                BrowserMenuItem("我的书签", Icons.Default.Bookmarks, palette.onSurface) { showBookmarkList = true; showMenuSheet = false }
                BrowserMenuItem(if (isDarkMode) "关闭夜间模式" else "强制夜间模式", Icons.Default.DarkMode, palette.onSurface) { isDarkMode = !isDarkMode; showMenuSheet = false }
                BrowserMenuItem("分享当前网页", Icons.Default.Share, palette.onSurface) { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, currentUrl) }, "分享网页")); showMenuSheet = false }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showSniffList) {
        Dialog(onDismissRequest = { showSniffList = false }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).clip(RoundedCornerShape(24.dp)).background(palette.surface).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("抓取到的媒体", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.onSurface)
                    TextButton(onClick = { sniffedUrls.clear(); showSniffList = false }) { Text("清空") }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sniffedUrls) { url ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.onSurface.copy(alpha = 0.05f)).clickable { pendingDownloadUrl = url; showDownloadDialog = true; showSniffList = false }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val ext = url.substringAfterLast('.', "").lowercase().substringBefore('?')
                            Icon(when(ext) { "mp4", "mkv", "avi" -> Icons.Default.VideoFile; "mp3" -> Icons.Default.AudioFile; "apk" -> Icons.Default.Android; else -> Icons.Default.InsertDriveFile }, null, tint = palette.primary.first())
                            Spacer(Modifier.width(12.dp))
                            Text(url.substringAfterLast('/').substringBefore('?'), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.onSurface, style = MaterialTheme.typography.bodySmall)
                            if (url.contains(".m3u8") || url.contains(".mp4")) {
                                TextButton(onClick = { onPlayStream(url); showSniffList = false }) { Text("播放") }
                            }
                        }
                    }
                }
            }
        }
    }

    // 历史记录
    if (showHistoryList) {
        val history = BrowserHistoryStore.getHistory(ctx)
        Dialog(onDismissRequest = { showHistoryList = false }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).clip(RoundedCornerShape(24.dp)).background(palette.surface).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("历史记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.onSurface)
                    TextButton(onClick = { BrowserHistoryStore.clearHistory(ctx); showHistoryList = false }) { Text("清空") }
                }
                if (history.isEmpty()) Text("暂无历史记录", modifier = Modifier.padding(16.dp), color = palette.onSurfaceDim)
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(history) { url -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { webView?.loadUrl(url); showHistoryList = false }.padding(12.dp)) { Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.onSurface, style = MaterialTheme.typography.bodySmall) } } }
            }
        }
    }

    // 书签
    if (showBookmarkList) {
        val bookmarks = BookmarkStore.get(ctx)
        Dialog(onDismissRequest = { showBookmarkList = false }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).clip(RoundedCornerShape(24.dp)).background(palette.surface).padding(16.dp)) {
                Text("我的书签", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.onSurface)
                Spacer(Modifier.height(8.dp))
                if (bookmarks.isEmpty()) Text("暂无书签", modifier = Modifier.padding(16.dp), color = palette.onSurfaceDim)
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(bookmarks) { url -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { webView?.loadUrl(url); showBookmarkList = false }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(url, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.onSurface, style = MaterialTheme.typography.bodySmall); IconButton(onClick = { BookmarkStore.remove(ctx, url); showBookmarkList = false }) { Icon(Icons.Default.Delete, null, tint = palette.onSurfaceDim) } } } }
            }
        }
    }

    if (showDownloadDialog) {
        AddTaskDialog(initialUrl = pendingDownloadUrl, onDismiss = { showDownloadDialog = false }) { url, name, threads, speed ->
            DownloadManager.add(ctx, url, name, threads, speed, DirStore.resolveDownloadDir(ctx))
            showDownloadDialog = false
        }
    }
}

@Composable
fun BrowserMenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 14.dp, horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.width(16.dp))
        Text(text, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}
