package com.downloadsource.ui

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Rational
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.downloadsource.MainActivity
import com.downloadsource.core.DownloadManager
import com.downloadsource.storage.BrowserHistoryStore
import com.downloadsource.storage.DirStore
import com.downloadsource.web.SafeWebViewClient
import com.downloadsource.web.WebInterceptor
import java.io.File
import kotlin.math.abs

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    onPlayVideo: (File) -> Unit, onPlayStream: (String) -> Unit,
    triggerSniff: Boolean, onSniffHandled: () -> Unit,
    triggerHistory: Boolean, onHistoryHandled: () -> Unit,
    externalUrlToLoad: String?, onUrlLoaded: () -> Unit
) {
    val ctx = LocalContext.current
    val activity = ctx as? MainActivity
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 80.dp.toPx() }
    val palette = LocalPalette.current

    val webView = remember {
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36"
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }
    }
    
    var currentUrl by remember { mutableStateOf(BrowserHistoryStore.getSession(ctx)) }
    var urlInput by remember { mutableStateOf(currentUrl) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    
    // 嗅探相关状态
    var pendingDownloadUrl by remember { mutableStateOf("") }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showSniffList by remember { mutableStateOf(false) }
    val sniffedUrls = remember { mutableStateListOf<WebInterceptor.SniffedMedia>() }
    val webInterceptor = remember { WebInterceptor() }

    LaunchedEffect(externalUrlToLoad) {
        if (externalUrlToLoad != null) {
            webView.loadUrl(externalUrlToLoad)
            onUrlLoaded()
        }
    }

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
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                canGoBack = view?.canGoBack() ?: false
                canGoForward = view?.canGoForward() ?: false
                
                val tampermonkeyJs = ctx.getSharedPreferences("tampermonkey", Context.MODE_PRIVATE).getString("js", "")
                if (!tampermonkeyJs.isNullOrBlank()) view?.evaluateJavascript(tampermonkeyJs, null)
                view?.evaluateJavascript(webInterceptor.injectionScript, null)
            }
        }
        webView.loadUrl(currentUrl)
        
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 高级质感搜索栏
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { webView.goBack() }, enabled = canGoBack) { 
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = if (canGoBack) palette.onSurface else palette.onSurfaceDim) 
                }
                
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(palette.surface)
                        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.1f))), RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    BasicTextField(
                        value = urlInput, onValueChange = { urlInput = it },
                        textStyle = TextStyle(color = palette.onSurface, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                        cursorBrush = SolidColor(palette.primary.first()), singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { webView.loadUrl(urlInput) }),
                        decorationBox = { inner -> if (urlInput.isEmpty()) Text("搜索或输入网址", color = palette.onSurfaceDim); inner() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                IconButton(onClick = { 
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
                    }
                }) { Icon(Icons.Default.PictureInPicture, null, tint = palette.onSurface) }
            }
            
            // WebView 与 手势
            AndroidView(
                factory = { webView },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                            onDragEnd = {
                                if (abs(totalDrag) > swipeThresholdPx) {
                                    if (totalDrag > 0) { if (webView.canGoBack()) webView.goBack() } 
                                    else { if (webView.canGoForward()) webView.goForward() }
                                }
                            }
                        )
                    }
            )

            // 底部悬浮操作栏
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).clip(RoundedCornerShape(24.dp)).background(palette.surface).padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { webView.goBack() }, enabled = canGoBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "后退", tint = if (canGoBack) palette.onSurface else palette.onSurfaceDim) }
                IconButton(onClick = { webView.goForward() }, enabled = canGoForward) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "前进", tint = if (canGoForward) palette.onSurface else palette.onSurfaceDim) }
                IconButton(onClick = { webView.loadUrl("https://www.bing.com") }) { Icon(Icons.Default.Home, "主页", tint = palette.onSurface) }
                IconButton(onClick = { webView.reload() }) { Icon(Icons.Default.Refresh, "刷新", tint = palette.onSurface) }
            }
        }

        // 资源嗅探悬浮球（修复：加回来了）
        AnimatedVisibility(
            visible = sniffedUrls.isNotEmpty(),
            enter = scaleIn(), exit = scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 100.dp)
        ) {
            FloatingActionButton(onClick = { showSniffList = true }, containerColor = palette.primary.first()) {
                BadgedBox(badge = { Badge { Text(sniffedUrls.size.toString()) } }) {
                    Icon(Icons.Default.Layers, "资源嗅探", tint = Color.White)
                }
            }
        }
    }

    // 嗅探列表弹窗
    if (showSniffList) {
        Dialog(onDismissRequest = { showSniffList = false }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).clip(RoundedCornerShape(16.dp)).background(palette.surface).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("已抓取资源 (${sniffedUrls.size})", fontWeight = FontWeight.Bold, color = palette.onSurface)
                    TextButton(onClick = { sniffedUrls.clear(); showSniffList = false }) { Text("清空") }
                }
                LazyColumn(Modifier.fillMaxSize()) {
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
