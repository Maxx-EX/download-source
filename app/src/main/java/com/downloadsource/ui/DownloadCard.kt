package com.downloadsource.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressRing(
            progress = task.fraction,
            running = task.status == TaskStatus.Running,
            completed = task.status == TaskStatus.Completed,
            colors = palette.primary,
        )
        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                task.fileName,
                color = palette.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when (task.status) {
                    TaskStatus.Failed -> "失败：${task.error ?: ""}"
                    TaskStatus.Completed -> "已完成 (${task.threads} 线程)"
                    TaskStatus.Paused -> "已暂停 · ${task.sizeText}"
                    else -> "${task.sizeText} · ${task.threads}线程 ${if (task.speedText.isNotEmpty()) "· ${task.speedText}" else ""}"
                },
                color = palette.onSurfaceDim,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = {
            when (task.status) {
                TaskStatus.Running -> onPause()
                TaskStatus.Paused, TaskStatus.Failed -> onResume()
                else -> {}
            }
        }, enabled = task.status != TaskStatus.Completed) {
            Icon(
                when (task.status) {
                    TaskStatus.Running -> Icons.Default.Pause
                    TaskStatus.Completed -> Icons.Default.Check
                    else -> Icons.Default.PlayArrow
                },
                contentDescription = null,
                tint = palette.onSurface.copy(alpha = 0.8f),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = palette.onSurfaceDim)
        }
    }
}

@Composable
private fun ProgressRing(progress: Float, running: Boolean, completed: Boolean, colors: List<Color>) {
    val palette = LocalPalette.current
    val transition = rememberInfiniteTransition(label = "ring")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "spin",
    )
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "p",
    )

    Canvas(Modifier.size(54.dp)) {
        val stroke = 5.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)

        // 明确的底环
        drawArc(
            color = palette.onSurface.copy(alpha = 0.15f),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = topLeft, size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )

        if (completed) {
            drawArc(
                brush = Brush.sweepGradient(colors), startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        } else if (animatedProgress > 0.01f) {
            val brush = Brush.sweepGradient(colors)
            drawArc(
                brush = brush,
                startAngle = if (running) rotation else -90f,
                sweepAngle = (animatedProgress * 360f).coerceAtLeast(4f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        } else if (running) {
            // 初始加载时的旋转指示
            drawArc(
                color = colors.first(),
                startAngle = rotation,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}
