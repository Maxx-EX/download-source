package com.downloadsource.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
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
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    onPlayVideo: (File) -> Unit, 
    onPlayStream: (String) -> Unit,
    triggerSniff: Boolean,
    onSniffHandled: () -> Unit,
    triggerHistory: Boolean,
    onHistoryHandled: () -> Unit
) {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(BrowserHistoryStore.getSession(ctx)) }
    var urlInput by remember { mutableStateOf(currentUrl) }
    var sniffedUrls = remember { mutableStateListOf<WebInterceptor.SniffedMedia>() }
    
    var isReadingMode by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var pendingDownloadUrl by remember { mutableStateOf("") }
    var pendingFileName by remember { mutableStateOf<String?>(null) }
    var showSniffList by remember { mutableStateOf(false) }
    var showHistoryList by remember { mutableStateOf(false) }
    var showMenuSheet by remember { mutableStateOf(false) }

    val webInterceptor = remember { WebInterceptor() }

    LaunchedEffect(triggerSniff) { if (triggerSniff) { showSniffList = true; onSniffHandled() } }
    LaunchedEffect(triggerHistory) { if (triggerHistory) { showHistoryList = true; onHistoryHandled() } }

    LaunchedEffect(Unit) {
        webInterceptor.sniffedData.collect { data ->
            if (sniffedUrls.none { it.url == data.url } && sniffedUrls.size < 100) sniffedUrls.add(data)
        }
    }

    val doSearch = {
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
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(webView?.windowToken, 0)
        }
    }

    DisposableEffect(Unit) { onDispose { webView?.destroy() } }
    BackHandler(enabled = webView?.canGoBack() == true) { webView?.goBack() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { webView?.goBack() }, enabled = webView?.canGoBack() == true) { 
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = if (webView?.canGoBack() == true) palette.onSurface else palette.onSurfaceDim) 
                }
                IconButton(onClick = { webView?.goForward() }, enabled = webView?.canGoForward() == true) { 
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = if (webView?.canGoForward() == true) palette.onSurface else palette.onSurfaceDim) 
                }
                Box(Modifier.weight(1f).padding(horizontal = 4.dp).clip(RoundedCornerShape(20.dp)).background(palette.surface).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    BasicTextField(
                        value = urlInput, onValueChange = { urlInput = it }, textStyle = TextStyle(color = palette.onSurface, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                        cursorBrush = SolidColor(palette.primary.first()), singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { doSearch() }),
                        decorationBox = { innerTextField -> if (urlInput.isEmpty()) Text("搜索或输入网址", color = palette.onSurfaceDim); innerTextField() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                IconButton(onClick = { doSearch() }) { Icon(Icons.Default.Search, null, tint = palette.primary.first()) }
                IconButton(onClick = { showMenuSheet = true }) { Icon(Icons.Default.MoreVert, null, tint = palette.onSurface) }
            }

            AndroidView(
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36"
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        
                        addJavascriptInterface(webInterceptor, "AndroidBridge")
                        
                        webViewClient = object : SafeWebViewClient(
                            onDownloadRequest = { url -> pendingDownloadUrl = url; pendingFileName = null; showDownloadDialog = true },
                            onPlayM3u8 = { url -> onPlayStream(url) }
                        ) {
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                val url = request.url.toString()
                                // 方向二：基础广告拦截拦截
                                if (url.contains("doubleclick.net") || url.contains("googlesyndication") || url.contains("cpro.baidu.com")) {
                                    return WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
                                }
                                if (url.contains("captcha") || url.contains("verify")) return null
                                val ext = url.substringAfterLast('.', "").lowercase().substringBefore('?')
                                if (ext in listOf("mp4", "mp3", "m3u8", "apk", "zip") && !url.contains("favicon")) {
                                    if (sniffedUrls.none { it.url == url } && sniffedUrls.size < 100) {
                                        sniffedUrls.add(WebInterceptor.SniffedMedia(url, null, false))
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                if (url != null) { urlInput = url; currentUrl = url; BrowserHistoryStore.saveSession(ctx, url) }
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                view?.evaluateJavascript(webInterceptor.injectionScript, null)
                                if (isReadingMode) injectReaderMode(view)
                            }
                        }
                        setDownloadListener { url, _, _, _, _ -> pendingDownloadUrl = url; pendingFileName = null; showDownloadDialog = true }
                        loadUrl(currentUrl)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize().background(palette.surface)
            )
        }

        AnimatedVisibility(visible = sniffedUrls.isNotEmpty(), enter = scaleIn(), exit = scaleOut(), modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            FloatingActionButton(onClick = { showSniffList = true }, containerColor = palette.primary.first()) {
                BadgedBox(badge = { Badge { Text(sniffedUrls.size.toString()) } }) {
                    Icon(Icons.Default.Layers, null, tint = Color.White)
                }
            }
        }
    }

    if (showMenuSheet) {
        ModalBottomSheet(onDismissRequest = { showMenuSheet = false }, containerColor = palette.surface) {
            Column(Modifier.padding(16.dp)) {
                BrowserMenuItem("网页长截图", Icons.Default.Crop, palette.onSurface) {
                    // 长截图思路：目前 WebView 只能截取可视区域。
                    // 实现长截图需要利用 Chrome DevTools Protocol 或者 JS 截取整个 body。
                    // 简易版：仅截取当前屏幕并保存到相册
                    webView?.let { wv ->
                        try {
                            val bitmap = android.graphics.Bitmap.createBitmap(wv.width, wv.height, android.graphics.Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bitmap)
                            wv.draw(canvas)
                            val file = File(DirStore.resolveDownloadDir(ctx), "screenshot_${System.currentTimeMillis()}.png")
                            java.io.FileOutputStream(file).use { out -> bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
                            android.widget.Toast.makeText(ctx, "截图已保存", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {}
                    }
                    showMenuSheet = false
                }
                BrowserMenuItem(if (isReadingMode) "退出阅读模式" else "阅读模式", Icons.Default.MenuBook, palette.onSurface) {
                    isReadingMode = !isReadingMode
                    if (isReadingMode) injectReaderMode(webView) else webView?.reload()
                    showMenuSheet = false
                }
                BrowserMenuItem("历史记录", Icons.Default.History, palette.onSurface) { showHistoryList = true; showMenuSheet = false }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showSniffList) {
        Dialog(onDismissRequest = { showSniffList = false }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).clip(RoundedCornerShape(16.dp)).background(palette.surface).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("极限嗅探结果 (${sniffedUrls.size})", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = palette.onSurface)
                    TextButton(onClick = { sniffedUrls.clear(); showSniffList = false }) { Text("清空") }
                }
                LazyColumn {
                    items(sniffedUrls) { media ->
                        Row(Modifier.fillMaxWidth().clickable { 
                            pendingDownloadUrl = media.url; pendingFileName = null; showDownloadDialog = true; showSniffList = false 
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

    if (showHistoryList) {
        val history = BrowserHistoryStore.getHistory(ctx)
        Dialog(onDismissRequest = { showHistoryList = false }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 400.dp).clip(RoundedCornerShape(16.dp)).background(palette.surface).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("历史记录", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    TextButton(onClick = { BrowserHistoryStore.clearHistory(ctx) }) { Text("清空") }
                }
                LazyColumn {
                    items(history) { url ->
                        Row(Modifier.fillMaxWidth().clickable { webView?.loadUrl(url); showHistoryList = false }.padding(12.dp)) {
                            Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.onSurface)
                        }
                    }
                }
            }
        }
    }

    if (showDownloadDialog) {
        AddTaskDialog(initialUrl = pendingDownloadUrl, initialName = pendingFileName ?: "", onDismiss = { showDownloadDialog = false }) { url, name, threads, speed, headers ->
            DownloadManager.add(ctx, url, name, threads, speed, headers, DirStore.resolveDownloadDir(ctx))
            showDownloadDialog = false
        }
    }
}

// 注入阅读模式 CSS
fun injectReaderMode(webView: WebView?) {
    val css = """
        javascript:(function() {
            var article = document.querySelector('article') || document.querySelector('.content') || document.body;
            var html = '<html><head><meta name="viewport" content="width=device-width, initial-scale=1"><style>body{max-width:800px;margin:0 auto;padding:20px;font-size:18px;line-height:1.6;color:#333;background:#fff;} img{max-width:100%;height:auto;}</style></head><body>' + article.innerHTML + '</body></html>';
            document.open(); document.write(html); document.close();
        })();
    """.trimIndent()
    webView?.evaluateJavascript(css, null)
}

@Composable
fun BrowserMenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 14.dp, horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.width(16.dp))
        Text(text, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}
