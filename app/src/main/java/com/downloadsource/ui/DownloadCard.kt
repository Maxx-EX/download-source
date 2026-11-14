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
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) +
                slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(300, easing = FastOutSlowInEasing)),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.surface)
                .padding(14.dp),
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
                        TaskStatus.Completed -> "已完成"
                        TaskStatus.Paused -> "已暂停 · ${task.sizeText}"
                        else -> {
                            val base = task.sizeText
                            if (task.speedText.isNotEmpty()) "$base · ${task.speedText}" else base
                        }
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
}

@Composable
private fun ProgressRing(
    progress: Float,
    running: Boolean,
    completed: Boolean,
    colors: List<Color>,
) {
    val palette = LocalPalette.current
    val transition = rememberInfiniteTransition(label = "ring")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "spin",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.85f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
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
        val scale = if (running) pulse else 1f

        // 底环
        drawArc(
            color = palette.onSurface.copy(alpha = 0.12f),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = topLeft, size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )

        if (completed) {
            val checkBrush = Brush.sweepGradient(colors)
            drawArc(
                brush = checkBrush, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        } else {
            val brush = Brush.sweepGradient(colors)
            drawArc(
                brush = brush,
                startAngle = if (running) rotation else -90f,
                sweepAngle = (animatedProgress * 360f).coerceAtLeast(4f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                alpha = scale,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}
