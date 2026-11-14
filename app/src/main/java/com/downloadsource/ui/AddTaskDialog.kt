package com.downloadsource.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    initialUrl: String = "",
    onDismiss: () -> Unit,
    onConfirm: (url: String, name: String?, threads: Int, speedLimitKb: Long, headers: Map<String, String>) -> Unit,
) {
    val ctx = LocalContext.current
    var url by remember { mutableStateOf(if (initialUrl.isNotBlank()) initialUrl else getClipboardUrl(ctx) ?: "") }
    var name by remember { mutableStateOf("") }
    var threads by remember { mutableFloatStateOf(8f) }
    var speedLimit by remember { mutableFloatStateOf(0f) }
    var referer by remember { mutableStateOf("") }
    var userAgent by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    val palette = LocalPalette.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(palette.surface)
                .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.1f))), RoundedCornerShape(28.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("新建下载任务", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = palette.onSurface)

            OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("下载链接 / 分享文本") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("文件名 (选填)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("留空自动解析") })

            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("线程数", color = palette.onSurface, style = MaterialTheme.typography.bodyMedium)
                    Text("${threads.toInt()}", color = palette.primary.first(), fontWeight = FontWeight.Bold)
                }
                Slider(value = threads, onValueChange = { threads = it }, valueRange = 1f..16f, steps = 15)
            }
            
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("限速 (0为不限)", color = palette.onSurface, style = MaterialTheme.typography.bodyMedium)
                    Text(if (speedLimit == 0f) "∞" else "${speedLimit.toLong()} KB/s", color = palette.primary.first(), fontWeight = FontWeight.Bold)
                }
                Slider(value = speedLimit, onValueChange = { speedLimit = it }, valueRange = 0f..2048f, steps = 63)
            }

            // 高级选项 (防 403 防盗链)
            Row(Modifier.fillMaxWidth().clickable { showAdvanced = !showAdvanced }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("高级选项 (防403防盗链)", color = palette.onSurfaceDim, style = MaterialTheme.typography.bodySmall)
                Icon(Icons.Default.ExpandMore, null, tint = palette.onSurfaceDim, modifier = Modifier.size(16.dp))
            }
            if (showAdvanced) {
                OutlinedTextField(value = referer, onValueChange = { referer = it }, label = { Text("Referer (选填)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = userAgent, onValueChange = { userAgent = it }, label = { Text("User-Agent (选填)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消", color = palette.onSurfaceDim) }
                Button(onClick = { 
                    if (url.isNotBlank()) {
                        val h = mutableMapOf<String, String>()
                        if (referer.isNotBlank()) h["Referer"] = referer
                        if (userAgent.isNotBlank()) h["User-Agent"] = userAgent
                        onConfirm(url, name, threads.toInt(), speedLimit.toLong(), h)
                    } 
                }, modifier = Modifier.weight(1f), enabled = url.isNotBlank()) { Text("下载") }
            }
        }
    }
}

private fun getClipboardUrl(ctx: Context): String? {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
    return if (text?.startsWith("http") == true || text?.contains("pan.") == true || text?.contains("lanzou") == true) text else null
}
