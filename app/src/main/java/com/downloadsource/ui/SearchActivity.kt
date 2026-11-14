package com.downloadsource.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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

class SearchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isDark = rememberDark()
            val palette = if (isDark) DarkPalette else LightPalette
            GradientBackground(palette) {
                MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                    SearchScreen(onClose = { finish() })
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onClose: () -> Unit) {
    val palette = LocalPalette.current
    var query by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("") }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        if (webView?.canGoBack() == true) webView?.goBack() else onClose()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("输入网址或关键词") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    val u = normalize(query)
                    if (u.isNotEmpty()) {
                        currentUrl = u
                        webView?.loadUrl(u)
                    }
                })
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = {
                val u = normalize(query)
                if (u.isNotEmpty()) {
                    currentUrl = u
                    webView?.loadUrl(u)
                }
            }) { Icon(Icons.Default.Search, null) }
        }

        Spacer(Modifier.height(10.dp))

        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(palette.surface)
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = SafeWebViewClient()
                        webView = this
                    }
                },
                update = { wv -> webView = wv },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun normalize(input: String): String {
    val q = input.trim()
    if (q.isEmpty()) return ""
    return if (q.startsWith("http://") || q.startsWith("https://")) q
    else if (q.contains('.') && !q.contains(' ')) "https://$q"
    else "https://www.bing.com/search?q=${java.net.URLEncoder.encode(q, "UTF-8")}"
}
