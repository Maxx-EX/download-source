package com.downloadsource.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.downloadsource.core.DownloadManager
import com.downloadsource.core.NetDiskParser
import com.downloadsource.core.parser.QuarkParser
import com.downloadsource.storage.DirStore
import kotlinx.coroutines.launch

class NetDiskActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val palette = rememberPalette()
            GradientBackground(palette) {
                MaterialTheme { NetDiskScreen(onBack = { finish() }) }
            }
        }
    }
}

@Composable
fun NetDiskScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    
    var rawText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var parsedResults by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = palette.onSurface) }
            Text("内测·网盘解析中心", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = palette.onSurface)
        }
        
        Text("直接粘贴网盘分享文案或链接，支持提取码自动识别。", color = palette.onSurfaceDim, modifier = Modifier.padding(vertical = 12.dp))
        
        OutlinedTextField(
            value = rawText,
            onValueChange = { rawText = it },
            label = { Text("分享链接或文本") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(Modifier.height(16.dp))
        
        Button(
            onClick = {
                if (rawText.isBlank()) return@Button
                isLoading = true
                errorMsg = null
                parsedResults = emptyList()
                
                scope.launch {
                    try {
                        val shareInfo = NetDiskParser.detect(rawText)
                        if (shareInfo.type == NetDiskParser.DiskType.UNKNOWN) {
                            errorMsg = "无法识别该网盘链接"
                        } else if (shareInfo.type == NetDiskParser.DiskType.QUARK) {
                            parsedResults = QuarkParser.parse(ctx, shareInfo.url, shareInfo.pwd)
                        } else {
                            errorMsg = "当前仅深入适配了夸克，其他网盘解析开发中..."
                        }
                    } catch (e: Exception) {
                        errorMsg = e.message ?: "解析失败"
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text("立即解析")
        }

        if (errorMsg != null) {
            Text(errorMsg!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
        }

        Spacer(Modifier.height(16.dp))

        parsedResults.forEach { (name, url) ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.Bold, color = palette.onSurface, maxLines = 1)
                        Text("直链已提取", color = palette.primary.first(), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = {
                        DownloadManager.add(ctx, url, name, 8, 0, emptyMap(), DirStore.resolveDownloadDir(ctx))
                    }) {
                        Icon(Icons.Default.Download, "Download", tint = palette.primary.first())
                    }
                }
            }
        }
    }
}
