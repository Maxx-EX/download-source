package com.downloadsource

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate // 修复：加入正确的 rotate 导入
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.downloadsource.core.DownloadManager
import com.downloadsource.storage.DirStore
import com.downloadsource.ui.*

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val themeMode = ThemeStore.get(this)
            val isDark = when (themeMode) {
                ThemeStore.LIGHT -> false
                ThemeStore.DARK -> true
                else -> systemDark
            }
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
    var showDonate by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Back", tint = palette.onSurface, modifier = Modifier.rotate(180f)) // 修复：使用 Modifier.rotate
            }
            Text("设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            SettingsGroup {
                SettingsRow("应用名称", "Download source")
                HorizontalDivider(color = palette.onSurface.copy(alpha = 0.1f), modifier = Modifier.padding(start = 16.dp)) // 修复：替换为 HorizontalDivider
                SettingsRow("版本通道", BuildConfig.CHANNEL.replaceFirstChar { it.uppercase() })
                HorizontalDivider(color = palette.onSurface.copy(alpha = 0.1f), modifier = Modifier.padding(start = 16.dp))
                SettingsRow("版本号", "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            }

            SettingsGroup {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("深色模式", color = palette.onSurface, modifier = Modifier.weight(1f))
                    Switch(
                        checked = theme == ThemeStore.DARK,
                        onCheckedChange = {
                            theme = if (it) ThemeStore.DARK else ThemeStore.LIGHT
                            ThemeStore.set(ctx, theme)
                            Toast.makeText(ctx, "重启应用生效 (๑•̀ㅂ•́)و✧", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                HorizontalDivider(color = palette.onSurface.copy(alpha = 0.1f), modifier = Modifier.padding(start = 16.dp))
                SettingsRow("下载目录", DirStore.resolveDownloadDir(ctx).name) { }
            }

            SettingsGroup {
                SettingsRow("镜像源管理", "GitHub 加速", Icons.Default.Link) {
                    ctx.startActivity(Intent(ctx, SourceManageActivity::class.java))
                }
                HorizontalDivider(color = palette.onSurface.copy(alpha = 0.1f), modifier = Modifier.padding(start = 16.dp))
                // 新增：清理缓存
                SettingsRow("清理缓存", "清理失败残留分片", Icons.Default.DeleteSweep) {
                    val dir = DirStore.resolveDownloadDir(ctx)
                    var count = 0
                    dir.listFiles()?.filter { it.name.startsWith(".") && it.isDirectory }?.forEach { 
                        it.deleteRecursively()
                        count++
                    }
                    Toast.makeText(ctx, "已清理 $count 个临时缓存 (~˘▾˘)~", Toast.LENGTH_SHORT).show()
                }
                HorizontalDivider(color = palette.onSurface.copy(alpha = 0.1f), modifier = Modifier.padding(start = 16.dp))
                SettingsRow("清空任务", "不删本地文件", Icons.Default.Delete) {
                    DownloadManager.tasks.value.forEach { DownloadManager.remove(ctx, it.id) }
                    Toast.makeText(ctx, "任务已清空 (๑>؂<๑)", Toast.LENGTH_SHORT).show()
                }
                HorizontalDivider(color = palette.onSurface.copy(alpha = 0.1f), modifier = Modifier.padding(start = 16.dp))
                SettingsRow("请我喝杯咖啡", "感谢赞助", Icons.Default.Favorite) {
                    showDonate = true
                }
            }

            Text(
                "Zero-core Engine • Made with ❤️\n(●'◡'●) 祝你下载愉快",
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                textAlign = TextAlign.Center,
                color = palette.onSurfaceDim,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (showDonate) {
        Dialog(onDismissRequest = { showDonate = false }) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(palette.surface).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("感谢赞助 (๑•̀ㅂ•́)و✧", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = palette.onSurface)
                Spacer(Modifier.height(16.dp))
                Image(
                    painter = painterResource(id = R.drawable.donate_qr),
                    contentDescription = "Donate QR",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(8.dp)
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { showDonate = false }, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
            }
        }
    }
}

@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.surface), content = content)
}

@Composable
fun SettingsRow(title: String, subtitle: String, icon: ImageVector? = null, onClick: (() -> Unit)? = null) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable { onClick() } else it }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = palette.onSurface, modifier = Modifier.padding(end = 12.dp))
        }
        Text(title, color = palette.onSurface, modifier = Modifier.weight(1f))
        Text(subtitle, color = palette.onSurfaceDim, style = MaterialTheme.typography.bodyMedium)
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = palette.onSurfaceDim, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
