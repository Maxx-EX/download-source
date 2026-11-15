package com.downloadsource

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip // 修复：补全 clip 导入
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.downloadsource.storage.BrowserPrefs
import com.downloadsource.ui.*

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val palette = rememberPalette()
            GradientBackground(palette) {
                MaterialTheme { SettingsScreen(onClose = { finish() }) }
            }
        }
    }
}

@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    var snifferType by remember { mutableIntStateOf(BrowserPrefs.getSnifferType(ctx)) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Back", tint = palette.onSurface, modifier = Modifier.rotate(180f)) }
            Text("高级设置 (v${BuildConfig.VERSION_NAME})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            
            SettingsGroup {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("嗅探引擎", color = palette.onSurface, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("JS深度注入", "Python底层拦截").forEachIndexed { index, name ->
                        Box(Modifier.clip(RoundedCornerShape(50)).background(if (snifferType == index) palette.primary.first() else palette.onSurface.copy(alpha = 0.1f)).clickable { snifferType = index; BrowserPrefs.setSnifferType(ctx, index) }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text(name, color = if (snifferType == index) Color.White else palette.onSurface, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            SettingsGroup {
                SettingsRow("防杀后台保护", "加入电池白名单", Icons.Default.BatteryChargingFull) {
                    val intent = Intent().apply {
                        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        data = Uri.parse("package:${ctx.packageName}")
                    }
                    ctx.startActivity(intent)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(LocalPalette.current.surface), content = content) }
@Composable fun SettingsRow(title: String, subtitle: String, icon: ImageVector? = null, onClick: (() -> Unit)? = null) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth().let { if (onClick != null) it.clickable { onClick() } else it }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, tint = palette.onSurface, modifier = Modifier.padding(end = 12.dp))
        Text(title, color = palette.onSurface, modifier = Modifier.weight(1f))
        Text(subtitle, color = palette.onSurfaceDim, style = MaterialTheme.typography.bodyMedium)
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = palette.onSurfaceDim, modifier = Modifier.padding(start = 8.dp))
    }
}
