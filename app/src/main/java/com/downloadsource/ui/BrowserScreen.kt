package com.downloadsource.ui

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.downloadsource.web.SafeWebViewClient

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var urlInput by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("https://www.bing.com") }
    var progress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    var engine by remember { mutableStateOf("Bing") }
    val engines = listOf("Bing", "Baidu", "Google")
    var showEngineMenu by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        // 地址栏
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                placeholder = { Text("搜索或输入网址") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (urlInput.isNotEmpty()) {
                        IconButton(onClick = { urlInput = "" }) { Icon(Icons.Default.Close, null) }
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    val q = urlInput.trim()
                    if (q.isNotEmpty()) {
                        val target = if (q.startsWith("http")) q else {
                            when (engine) {
                                "Baidu" -> "https://www.baidu.com/s?wd=$q"
                                "Google" -> "https://www.google.com/search?q=$q"
                                else -> "https://www.bing.com/search?q=$q"
                            }
                        }
                        currentUrl = target
                        webView?.loadUrl(target)
                        urlInput = target
                    }
                })
            )
            // 搜索引擎切换
            Box {
                TextButton(onClick = { showEngineMenu = true }) {
                    Text(engine, color = palette.onSurface)
                }
                DropdownMenu(expanded = showEngineMenu, onDismissRequest = { showEngineMenu = false }) {
                    engines.forEach { e ->
                        DropdownMenuItem(text = { Text(e) }, onClick = {
                            engine = e
                            showEngineMenu = false
                        })
                    }
                }
            }
        }

        // 浏览器操作栏
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row {
                IconButton(onClick = { webView?.goBack() }, enabled = canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = if (canGoBack) palette.onSurface else palette.onSurfaceDim)
                }
                IconButton(onClick = { webView?.goForward() }, enabled = canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = if (canGoForward) palette.onSurface else palette.onSurfaceDim)
                }
                IconButton(onClick = { webView?.reload() }) {
                    Icon(Icons.Default.Refresh, null, tint = palette.onSurface)
                }
            }
            IconButton(onClick = {
                currentUrl = "https://www.bing.com"
                webView?.loadUrl(currentUrl)
                urlInput = ""
            }) {
                Icon(Icons.Default.Home, null, tint = palette.onSurface)
            }
        }

        if (progress in 0.01f..0.99f) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        } else {
            Spacer(Modifier.height(4.dp))
        }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : SafeWebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            if (url != null) currentUrl = url
                            urlInput = url ?: ""
                        }
                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            canGoBack = view?.canGoBack() ?: false
                            canGoForward = view?.canGoForward() ?: false
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress / 100f
                        }
                    }
                    loadUrl(currentUrl)
                    webView = this
                }
            },
            update = { webView = it },
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(palette.surface)
        )
    }
}
