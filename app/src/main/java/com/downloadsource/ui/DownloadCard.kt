package com.downloadsource.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.downloadsource.core.DownloadTask
import com.downloadsource.core.DownloadManager
import com.downloadsource.core.TaskStatus
import java.io.File

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DownloadCard(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val ctx = LocalContext.current
    var showSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(task.fileName) }

    // 小功能：下载速度平滑动画
    val animatedSpeed by animateFloatAsState(targetValue = task.speed.toFloat(), animationSpec = tween(500), label = "speed")

    Box {
        Row(
            modifier.fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(16.dp), ambientColor = palette.primary.first().copy(alpha = 0.1f))
                .clip(RoundedCornerShape(16.dp))
                .background(palette.surface)
                .combinedClickable(
                    onClick = { if (task.status == TaskStatus.Completed) onOpen() },
                    onLongClick = { showSheet = true }
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressRing(task.fraction, task.status == TaskStatus.Running, task.status == TaskStatus.Completed, palette.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(task.fileName, color = palette.onSurface, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                val speedStr = if (animatedSpeed > 0) String.format("%.1f KB/s", animatedSpeed / 1024) else ""
                Text(
                    when (task.status) {
                        TaskStatus.Failed -> "失败：${task.error ?: ""}"
                        TaskStatus.Completed -> "已完成 (点击打开)"
                        TaskStatus.Paused -> "已暂停 · ${task.sizeText}"
                        else -> "${task.sizeText} · ${task.threads}线程 ${if (speedStr.isNotEmpty()) "· $speedStr" else ""}"
                    },
                    color = palette.onSurfaceDim, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (task.status != TaskStatus.Completed) {
                IconButton(onClick = { if (task.status == TaskStatus.Running) onPause() else onResume() }) {
                    Icon(if (task.status == TaskStatus.Running) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = palette.onSurface)
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = palette.onSurfaceDim) }
        }

        if (showSheet) {
            ModalBottomSheet(onDismissRequest = { showSheet = false }, containerColor = palette.surface) {
                Column(Modifier.padding(16.dp)) {
                    Text("任务操作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.onSurface, modifier = Modifier.padding(bottom = 12.dp))
                    SheetItem("其它应用打开", Icons.Default.OpenInNew, palette.onSurface) {
                        val file = File(task.outputPath ?: "")
                        if (file.exists()) {
                            val uri = try { FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file) } catch (e: Exception) { Toast.makeText(ctx, "文件路径异常，无法打开", Toast.LENGTH_SHORT).show(); return@SheetItem }
                            val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "*/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK) }
                            try { ctx.startActivity(Intent.createChooser(intent, "打开文件")) } catch (_: Exception) { }
                        } else Toast.makeText(ctx, "文件不存在", Toast.LENGTH_SHORT).show()
                        showSheet = false
                    }
                    SheetItem("重命名", Icons.Default.Edit, palette.onSurface) { renameText = task.fileName; showRenameDialog = true; showSheet = false }
                    if (task.status == TaskStatus.Completed || task.status == TaskStatus.Failed) {
                        SheetItem("重新下载", Icons.Default.Refresh, palette.onSurface) { File(task.outputPath ?: "").delete(); DownloadManager.start(ctx, task.id); showSheet = false }
                    }
                    SheetItem("复制下载链接", Icons.Default.ContentCopy, palette.onSurface) {
                        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("URL", task.url))
                        Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show(); showSheet = false
                    }
                    SheetItem("分享", Icons.Default.Share, palette.onSurface) {
                        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, task.url) }
                        ctx.startActivity(Intent.createChooser(intent, "分享链接")); showSheet = false
                    }
                    SheetItem("删除", Icons.Default.Delete, Color.Red) { onDelete(); showSheet = false }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        if (showRenameDialog) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("重命名") },
                text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true) },
                confirmButton = { TextButton(onClick = { if (renameText.isNotBlank()) DownloadManager.rename(ctx, task.id, renameText); showRenameDialog = false }) { Text("确定") } },
                dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("取消") } }
            )
        }
    }
}

@Composable
fun SheetItem(text: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 14.dp, horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.width(16.dp))
        Text(text, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ProgressRing(progress: Float, running: Boolean, completed: Boolean, colors: List<Color>) {
    val transition = rememberInfiniteTransition(label = "ring")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "spin")
    val animatedProgress by animateFloatAsState(progress, tween(400, easing = FastOutSlowInEasing), label = "p")
    Canvas(Modifier.size(48.dp)) {
        val stroke = 4.dp.toPx(); val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        drawArc(Color.White.copy(alpha = 0.2f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        if (completed) drawArc(Brush.sweepGradient(colors), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        else if (animatedProgress > 0.01f) drawArc(Brush.sweepGradient(colors), if (running) rotation else -90f, (animatedProgress * 360f).coerceAtLeast(4f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        else if (running) drawArc(colors.first(), rotation, 90f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
