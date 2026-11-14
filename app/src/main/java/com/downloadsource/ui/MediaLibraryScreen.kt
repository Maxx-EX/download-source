package com.downloadsource.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.downloadsource.storage.DirStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun MediaLibraryScreen(onPlayVideo: (File) -> Unit) {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf("全部") }
    val filters = listOf("全部", "视频", "音频", "安装包")

    // 关键修复：加入 Dispatchers.IO 和 Null Safety 避免空指针闪退
    LaunchedEffect(selectedFilter) {
        withContext(Dispatchers.IO) {
            try {
                val dir = DirStore.resolveDownloadDir(ctx)
                val list = dir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") } ?: emptyList()
                files = list.sortedByDescending { it.lastModified() }
            } catch (e: Exception) {
                files = emptyList()
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("文件库", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = palette.onSurface)
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEach { f ->
                val selected = f == selectedFilter
                Box(Modifier.clip(RoundedCornerShape(50)).background(if (selected) palette.primary.first() else palette.surface).clickable { selectedFilter = f }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(f, color = if (selected) Color.White else palette.onSurface, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        val filteredFiles = files.filter {
            when (selectedFilter) {
                "视频" -> it.name.endsWith(".mp4", true) || it.name.endsWith(".mkv", true) || it.name.endsWith(".ts", true)
                "音频" -> it.name.endsWith(".mp3", true) || it.name.endsWith(".m4a", true)
                "安装包" -> it.name.endsWith(".apk", true)
                else -> true
            }
        }

        if (filteredFiles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("没有文件 (￣▽￣)", color = palette.onSurfaceDim) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filteredFiles) { file ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.surface).clickable { if (file.name.endsWith(".mp4", true) || file.name.endsWith(".mkv", true)) onPlayVideo(file) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(file.name, color = palette.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${file.length() / 1024 / 1024} MB", color = palette.onSurfaceDim, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { 
                            if (file.delete()) {
                                files = files.toMutableList().apply { remove(file) }
                            } 
                        }) { Icon(Icons.Default.Delete, null, tint = palette.onSurfaceDim) }
                    }
                }
            }
        }
    }
}
