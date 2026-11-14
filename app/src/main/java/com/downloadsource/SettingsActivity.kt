package com.downloadsource

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.downloadsource.core.DownloadManager
import com.downloadsource.storage.DirStore
import com.downloadsource.ui.*

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isDark = rememberDark()
            val palette = if (isDark) DarkPalette else LightPalette
            GradientBackground(palette) {
                MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                    SettingsScreen(onClose = { finish() })
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    var theme by remember { mutableIntStateOf(ThemeStore.get(ctx)) }

    val channel = BuildConfig.CHANNEL.replaceFirstChar { it.uppercase() }
    val pkg = ctx.packageName

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item { TopBar(title = "设置", onBack = onClose) }

        item { SectionLabel("应用信息") }
        item {
            InfoCard(
                rows = listOf(
                    "名称" to "Download source",
                    "通道" to channel,
                    "版本" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    "构建时间" to BuildConfig.BUILD_TIME,
                    "包名" to pkg,
                )
            )
        }

        item { SectionLabel("外观") }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(palette.surface).padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Brightness4, null, tint = palette.onSurface)
                    Spacer(Modifier.width(10.dp))
                    Text("主题", color = palette.onSurface, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChoice("跟随系统", theme == ThemeStore.FOLLOW_SYSTEM) {
                        theme = ThemeStore.FOLLOW_SYSTEM; ThemeStore.set(ctx, theme)
                    }
                    ThemeChoice("浅色", theme == ThemeStore.LIGHT) {
                        theme = ThemeStore.LIGHT; ThemeStore.set(ctx, theme)
                    }
                    ThemeChoice("深色", theme == ThemeStore.DARK) {
                        theme = ThemeStore.DARK; ThemeStore.set(ctx, theme)
                    }
                }
            }
        }

        item { SectionLabel("下载") }
        item {
            NavItem(
                icon = Icons.Default.Folder,
                title = "下载目录",
                subtitle = DirStore.resolveDownloadDir(ctx).absolutePath,
            ) { /* 目录由主页选择，避免重复请求 SAF */ }
        }

        item { SectionLabel("更多") }
        item {
            NavItem(Icons.Default.Language, "内置浏览器", "搜索 / 打开链接") {
                ctx.startActivity(Intent(ctx, SearchActivity::class.java))
            }
        }
        item {
            NavItem(Icons.Default.Link, "镜像源", "管理 GitHub 加速前缀") {
                ctx.startActivity(Intent(ctx, SourceManageActivity::class.java))
            }
        }
        item {
            NavItem(Icons.Default.Refresh, "清空所有任务", "不会删除已下载的文件") {
                val tasks = DownloadManager.tasks.value
                tasks.forEach { DownloadManager.remove(ctx, it.id) }
                Toast.makeText(ctx, "已清空 ${tasks.size} 个任务", Toast.LENGTH_SHORT).show()
            }
        }

        item { SectionLabel("关于") }
        item {
            NavItem(Icons.Default.Info, "开源许可", "Apache-2.0 / MIT / BSD") {
                AlertDialogSimple(
                    title = "开源许可",
                    body = "Zero-core 引擎与 Download source 主体使用 Apache-2.0。\n" +
                        "OkHttp: Apache-2.0\n" +
                        "Jetpack Compose: Apache-2.0\n" +
                        "Gson: Apache-2.0",
                )
            }
        }
        item {
            NavItem(Icons.AutoMirrored.Filled.KeyboardArrowRight, "项目主页", "github.com") {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onBack) { Text("← 返回", color = LocalPalette.current.onSurface) }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = LocalPalette.current.onSurfaceDim,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = 6.dp, top = 8.dp)
    )
}

@Composable
private fun InfoCard(rows: List<Pair<String, String>>) {
    val palette = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(palette.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { (k, v) ->
            Row {
                Text(k, color = palette.onSurfaceDim, modifier = Modifier.width(80.dp), style = MaterialTheme.typography.bodySmall)
                Text(v, color = palette.onSurface, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(palette.surface).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = palette.onSurface)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.onSurface, fontWeight = FontWeight.Medium)
            Text(subtitle, color = palette.onSurfaceDim, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

@Composable
private fun ThemeChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val bg = if (selected) palette.primary.first().copy(alpha = 0.85f) else palette.onSurface.copy(alpha = 0.06f)
    val fg = if (selected) palette.onSurface.copy(alpha = 0.95f) else palette.onSurface
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(label, color = fg, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun AlertDialogSimple(title: String, body: String) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            TextButton(onClick = { (ctx as? ComponentActivity)?.let { /* 关闭由系统管理 */ } }) { Text("好") }
        },
        title = { Text(title) },
        text = { Text(body) },
    )
}
