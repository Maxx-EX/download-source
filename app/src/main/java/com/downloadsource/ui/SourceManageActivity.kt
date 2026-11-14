package com.downloadsource.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.downloadsource.core.MirrorResolver
import com.downloadsource.core.MirrorStore

class SourceManageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val palette = rememberPalette()
            GradientBackground(palette) { MaterialTheme { SourceScreen() } }
        }
    }
}

@Composable
fun SourceScreen() {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    var enabled by remember { mutableStateOf(MirrorStore.getEnabled(ctx)) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("镜像源选择", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = palette.onSurface)
        Text("勾选你需要的镜像源，下载 GitHub 时会自动使用。", color = palette.onSurfaceDim, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MirrorResolver.AVAILABLE_MIRRORS) { m ->
                val isChecked = enabled.contains(m)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { 
                        val newList = if (isChecked) enabled - m else enabled + m
                        enabled = newList
                        MirrorStore.setEnabled(ctx, newList)
                    }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = isChecked, onCheckedChange = null)
                    Spacer(Modifier.width(12.dp))
                    Text(if (m.isBlank()) "原生直连 (不加速)" else m, color = palette.onSurface, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
