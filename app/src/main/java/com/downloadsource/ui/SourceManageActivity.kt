package com.downloadsource.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.downloadsource.web.MirrorResolver

class SourceManageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isDark = rememberDark()
            val palette = if (isDark) DarkPalette else LightPalette
            GradientBackground(palette) {
                MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                    SourceScreen()
                }
            }
        }
    }
}

@Composable
fun SourceScreen() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val palette = LocalPalette.current
    var list by remember { mutableStateOf(MirrorResolver.load(ctx)) }
    var input by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("镜像源管理", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("GitHub 链接会自动套用首个镜像前缀；点击删除可移除。", color = palette.onSurfaceDim)
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("https://ghproxy.com/") },
                singleLine = true,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val v = input.trim()
                if (v.isNotEmpty()) {
                    list = list + v
                    MirrorResolver.save(ctx, list)
                    input = ""
                }
            }) { Text("添加") }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list) { m ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (m.isBlank()) "直连（无前缀）" else m,
                        modifier = Modifier.weight(1f),
                        color = palette.onSurface
                    )
                    IconButton(onClick = {
                        list = list - m
                        MirrorResolver.save(ctx, list)
                    }) { Icon(Icons.Default.Delete, null) }
                }
            }
        }
    }
}
