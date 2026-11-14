package com.downloadsource.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (url: String, name: String?, threads: Int) -> Unit,
) {
    val ctx = LocalContext.current
    var url by remember { mutableStateOf(getClipboardUrl(ctx) ?: "") }
    var name by remember { mutableStateOf("") }
    var threads by remember { mutableFloatStateOf(8f) }
    val palette = LocalPalette.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(palette.surface) // 半透明
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.1f))
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("新建下载任务", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = palette.onSurface)

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("下载链接") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("文件名 (选填)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                placeholder = { Text("留空则自动识别") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (url.isNotBlank()) onConfirm(url, name, threads.toInt()) })
            )

            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("下载线程数", color = palette.onSurface)
                    Text("${threads.toInt()}", color = palette.primary.first(), fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = threads,
                    onValueChange = { threads = it },
                    valueRange = 1f..16f,
                    steps = 15
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("取消", color = palette.onSurfaceDim)
                }
                Button(
                    onClick = { if (url.isNotBlank()) onConfirm(url, name, threads.toInt()) },
                    modifier = Modifier.weight(1f),
                    enabled = url.isNotBlank()
                ) {
                    Text("开始下载")
                }
            }
        }
    }
}

private fun getClipboardUrl(ctx: Context): String? {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
    return if (text?.startsWith("http") == true) text else null
}
