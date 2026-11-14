package com.downloadsource.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.downloadsource.core.DownloadTask
import com.downloadsource.core.TaskStatus

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
    Row(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.surface)
            .clickable(enabled = task.status == TaskStatus.Completed) { onOpen() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressRing(task.fraction, task.status == TaskStatus.Running, task.status == TaskStatus.Completed, palette.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(task.fileName, color = palette.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                when (task.status) {
                    TaskStatus.Failed -> "失败：${task.error ?: ""}"
                    TaskStatus.Completed -> "已完成 (点击打开)"
                    TaskStatus.Paused -> "已暂停 · ${task.sizeText}"
                    else -> "${task.sizeText} · ${task.threads}线程 ${if (task.speedText.isNotEmpty()) "· ${task.speedText}" else ""}"
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
}

@Composable
private fun ProgressRing(progress: Float, running: Boolean, completed: Boolean, colors: List<Color>) {
    val palette = LocalPalette.current
    val transition = rememberInfiniteTransition(label = "ring")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "spin")
    val animatedProgress by animateFloatAsState(progress, tween(400, easing = FastOutSlowInEasing), label = "p")
    Canvas(Modifier.size(54.dp)) {
        val stroke = 5.dp.toPx(); val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        drawArc(palette.onSurface.copy(alpha = 0.15f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        if (completed) drawArc(Brush.sweepGradient(colors), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        else if (animatedProgress > 0.01f) drawArc(Brush.sweepGradient(colors), if (running) rotation else -90f, (animatedProgress * 360f).coerceAtLeast(4f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        else if (running) drawArc(colors.first(), rotation, 90f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
