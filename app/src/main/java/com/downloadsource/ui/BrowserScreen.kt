package com.downloadsource.ui

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Rational
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.downloadsource.MainActivity
import com.downloadsource.core.DownloadManager
import com.downloadsource.storage.BrowserHistoryStore
import com.downloadsource.storage.DirStore
import com.downloadsource.web.SafeWebViewClient
import com.downloadsource.web.WebInterceptor
import java.io.File

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
    
    // 修复：将 WebView 实例存在 remember 中，防止重组时重建
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
    var pendingDownloadUrl by remember { mutableStateOf("") }
    var showDownloadDialog by remember { mutableStateOf(false) }
    val webInterceptor = remember { WebInterceptor() }

    DisposableEffect(Unit) {
        webView.addJavascriptInterface(webInterceptor, "AndroidBridge")
        webView.webViewClient = object : SafeWebViewClient(
            onDownloadRequest = { url -> pendingDownloadUrl = url; showDownloadDialog = true },
            onPlayM3u8 = { url -> onPlayStream(url) }
        ) {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val tampermonkeyJs = ctx.getSharedPreferences("tampermonkey", Context.MODE_PRIVATE).getString("js", "")
                if (!tampermonkeyJs.isNullOrBlank()) view?.evaluateJavascript(tampermonkeyJs, null)
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
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(value = urlInput, onValueChange = { urlInput = it }, modifier = Modifier.weight(1f))
                IconButton(onClick = { 
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
                    }
                }) { Icon(Icons.Default.PictureInPicture, null) }
            }
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize()
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
