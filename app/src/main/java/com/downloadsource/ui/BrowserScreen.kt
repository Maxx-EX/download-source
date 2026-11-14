package com.downloadsource.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
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
    var sniffedUrls = remember { mutableStateListOf<String>() }
    
    var showDownloadDialog by remember { mutableStateOf(false) }
    var pendingDownloadUrl by remember { mutableStateOf("") }
    var showSniffList by remember { mutableStateOf(false) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var isDarkMode by remember { mutableStateOf(false) }

    // 抽离搜索/访问逻辑，供回车键和搜索按钮共用
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
            // 提交后隐藏键盘
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(webView?.windowToken, 0)
        }
    }

    DisposableEffect(Unit) { onDispose { webView?.destroy() } }
    BackHandler(enabled = webView?.canGoBack() == true) { webView?.goBack() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Via 风格顶栏
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { webView?.goBack() }, enabled = webView?.canGoBack() == true) { 
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = if (webView?.canGoBack() == true) palette.onSurface else palette.onSurfaceDim) 
                }
                IconButton(onClick = { webView?.goForward() }, enabled = webView?.canGoForward() == true) { 
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = if (webView?.canGoForward() == true) palette.onSurface else palette.onSurfaceDim) 
                }
                
                // 极简地址栏
                Box(Modifier.weight(1f).padding(horizontal = 4.dp).clip(RoundedCornerShape(20.dp)).background(palette.surface).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    BasicTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        textStyle = TextStyle(color = palette.onSurface, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                        cursorBrush = SolidColor(palette.primary.first()),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), // 将键盘右下角改为"前往"
                        keyboardActions = KeyboardActions(onGo = { doSearch() }), // 绑定回车事件
                        decorationBox = { innerTextField ->
                            if (urlInput.isEmpty()) Text("搜索或输入网址", color = palette.onSurfaceDim)
                            innerTextField()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                IconButton(onClick = { doSearch() }) { 
                    Icon(Icons.Default.Search, null, tint = palette.primary.first()) 
                }
                IconButton(onClick = { webView?.reload() }) { 
                    Icon(Icons.Default.Refresh, null, tint = palette.onSurface) 
                }
                IconButton(onClick = { showMenuSheet = true }) { 
                    Icon(Icons.Default.MoreVert, null, tint = palette.onSurface) 
                }
            }

            AndroidView(
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        
                        webViewClient = object : SafeWebViewClient(
                            onDownloadRequest = { url -> pendingDownloadUrl = url; showDownloadDialog = true },
                            onPlayM3u8 = { url -> onPlayStream(url) }
                        ) {
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                val url = request.url.toString()
                                if (url.contains("captcha") || url.contains("verify")) return null
                                val ext = url.substringAfterLast('.', "").lowercase().substringBefore('?')
                                if (ext in listOf("mp4", "mp3", "m3u8", "apk", "zip") && !url.contains("favicon")) {
                                    if (!sniffedUrls.contains(url) && sniffedUrls.size < 50) sniffedUrls.add(url)
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                if (url != null) { urlInput = url; currentUrl = url; BrowserHistoryStore.saveSession(ctx, url) }
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

        AnimatedVisibility(visible = sniffedUrls.isNotEmpty(), enter = scaleIn(), exit = scaleOut(), modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            FloatingActionButton(onClick = { showSniffList = true }, containerColor = palette.primary.first()) {
                Icon(Icons.Default.Layers, null, tint = Color.White)
            }
        }
    }

    if (showMenuSheet) {
        ModalBottomSheet(onDismissRequest = { showMenuSheet = false }, containerColor = palette.surface) {
            Column(Modifier.padding(16.dp)) {
                BrowserMenuItem("回到主页", Icons.Default.Home, palette.onSurface) { webView?.loadUrl("https://www.bing.com"); showMenuSheet = false }
                BrowserMenuItem(if (isDarkMode) "关闭夜间模式" else "强制夜间模式", Icons.Default.DarkMode, palette.onSurface) { isDarkMode = !isDarkMode; webView?.reload(); showMenuSheet = false }
                BrowserMenuItem("分享当前网页", Icons.Default.Share, palette.onSurface) { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, currentUrl) }, "分享网页")); showMenuSheet = false }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showSniffList) {
        Dialog(onDismissRequest = { showSniffList = false }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 400.dp).clip(RoundedCornerShape(16.dp)).background(palette.surface).padding(16.dp)) {
                LazyColumn {
                    items(sniffedUrls) { url ->
                        Row(Modifier.fillMaxWidth().clickable { pendingDownloadUrl = url; showDownloadDialog = true; showSniffList = false }.padding(12.dp)) {
                            Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.onSurface)
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

@Composable
fun BrowserMenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 14.dp, horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.width(16.dp))
        Text(text, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}
