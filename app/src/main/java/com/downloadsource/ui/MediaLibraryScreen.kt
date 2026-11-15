package com.downloadsource.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
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
import androidx.core.content.FileProvider
import com.downloadsource.storage.DirStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Composable
fun MediaLibraryScreen(onPlayVideo: (File) -> Unit) {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf("全部") }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedFiles = remember { mutableStateListOf<File>() }

    val filters = listOf("全部", "视频", "音频", "安装包")

    LaunchedEffect(selectedFilter) {
        withContext(Dispatchers.IO) {
            val dir = DirStore.resolveDownloadDir(ctx)
            files = dir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.sortedByDescending { it.lastModified() } ?: emptyList()
        }
    }

    // 大功能：一键打包 ZIP
    val zipAndShare = {
        val targetFiles = if (isSelectionMode) selectedFiles.toList() else files
        if (targetFiles.isNotEmpty()) {
            val zipFile = File(ctx.cacheDir, "batch_${System.currentTimeMillis()}.zip")
            try {
                ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
                    targetFiles.forEach { f ->
                        zos.putNextEntry(ZipEntry(f.name))
                        f.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", zipFile)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(Intent.createChooser(intent, "分享打包文件"))
            } catch (e: Exception) {}
            isSelectionMode = false
            selectedFiles.clear()
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("文件库", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = palette.onSurface, modifier = Modifier.weight(1f))
            if (isSelectionMode) {
                IconButton(onClick = zipAndShare) { Icon(Icons.Default.Share, "打包", tint = palette.primary.first()) }
                IconButton(onClick = {
                    selectedFiles.forEach { it.delete() }
                    files = files - selectedFiles.toSet()
                    isSelectionMode = false
                    selectedFiles.clear()
                }) { Icon(Icons.Default.Delete, "删除", tint = Color.Red) }
                TextButton(onClick = { isSelectionMode = false; selectedFiles.clear() }) { Text("取消") }
            } else {
                TextButton(onClick = { isSelectionMode = true }) { Text("选择") }
            }
        }
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
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.surface)
                            .clickable {
                                if (isSelectionMode) {
                                    if (selectedFiles.contains(file)) selectedFiles.remove(file) else selectedFiles.add(file)
                                } else {
                                    if (file.name.endsWith(".mp4", true) || file.name.endsWith(".mkv", true)) onPlayVideo(file)
                                }
                            }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelectionMode) {
                            Checkbox(checked = selectedFiles.contains(file), onCheckedChange = { 
                                if (it) selectedFiles.add(file) else selectedFiles.remove(file) 
                            })
                            Spacer(Modifier.width(8.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(file.name, color = palette.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${file.length() / 1024 / 1024} MB", color = palette.onSurfaceDim, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!isSelectionMode) {
                            IconButton(onClick = { if (file.delete()) files = files - file }) { Icon(Icons.Default.Delete, null, tint = palette.onSurfaceDim) }
                        }
                    }
                }
            }
        }
    }
}
