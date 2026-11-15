package com.downloadsource

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.downloadsource.core.Aria2Manager
import com.downloadsource.core.LocalWebServer
import com.downloadsource.core.UpdateManager
import com.downloadsource.storage.BrowserPrefs
import com.downloadsource.storage.DirStore
import com.downloadsource.ui.*
import com.downloadsource.utils.PoemEasterEgg
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val palette = rememberPalette()
            GradientBackground(palette) { MaterialTheme { SettingsScreen(onClose = { finish() }) } }
        }
    }
}

@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var currentLock by remember { mutableStateOf(ThemeStore.getLockPassword(ctx)) }
    
    var versionClickCount by remember { mutableIntStateOf(0) }
    var showPoem by remember { mutableStateOf(false) }
    var poemText by remember { mutableStateOf("") }
    
    var showAria2Dialog by remember { mutableStateOf(false) }
    var showTmDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Back", tint = palette.onSurface, modifier = Modifier.rotate(180f)) }
            Text("设置 (v${BuildConfig.VERSION_NAME})", modifier = Modifier.clickable { 
                versionClickCount++
                if (versionClickCount >= 10) { poemText = PoemEasterEgg.getRandomPoem(); showPoem = true; versionClickCount = 0 }
            }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            SettingsGroup {
                SettingsRow("检查更新", if (checking) "检测中..." else "检查最新版", Icons.Default.Update) {
                    if (!checking) { checking = true; scope.launch { val info = UpdateManager.checkUpdate(BuildConfig.VERSION_NAME); checking = false; if (info != null) UpdateManager.showUpdateDialog(ctx, info) else Toast.makeText(ctx, "当前已是最新版", Toast.LENGTH_SHORT).show() } }
                }
            }
            SettingsGroup {
                SettingsRow("Aria2 远程下载", Aria2Manager.getServer(ctx), Icons.Default.Router) { showAria2Dialog = true }
                HorizontalDivider(color = palette.onSurface.copy(alpha = 0.1f))
                SettingsRow("油猴脚本", "注入自定义 JS", Icons.Default.Code) { showTmDialog = true }
            }
            SettingsGroup {
                SettingsRow("应用锁 (15分钟免密)", if (currentLock != null) "已开启" else "未开启", Icons.Default.Lock) { }
                HorizontalDivider(color = palette.onSurface.copy(alpha = 0.1f))
                SettingsRow("防杀后台保护", "加入电池白名单", Icons.Default.BatteryChargingFull) {
                    try { ctx.startActivity(Intent().apply { action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS; data = Uri.parse("package:${ctx.packageName}") }) } catch (e: Exception) {}
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    
    if (showPoem) {
        AlertDialog(
            onDismissRequest = { showPoem = false },
            title = { Text("发现隐藏内容 🎉") },
            text = { Text(poemText, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = { showPoem = false }) { Text("好诗") } }
        )
    }
    if (showAria2Dialog) {
        var inputUrl by remember { mutableStateOf(Aria2Manager.getServer(ctx)) }
        AlertDialog(
            onDismissRequest = { showAria2Dialog = false },
            title = { Text("配置 Aria2 RPC") },
            text = { OutlinedTextField(value = inputUrl, onValueChange = { inputUrl = it }, placeholder = { Text("http://192.168.1.100:6800/jsonrpc") }) },
            confirmButton = { TextButton(onClick = { Aria2Manager.setServer(ctx, inputUrl); showAria2Dialog = false }) { Text("保存") } }
        )
    }
    if (showTmDialog) {
        var inputJs by remember { mutableStateOf(ctx.getSharedPreferences("tampermonkey", Context.MODE_PRIVATE).getString("js", "") ?: "") }
        AlertDialog(
            onDismissRequest = { showTmDialog = false },
            title = { Text("输入 Tampermonkey JS") },
            text = { OutlinedTextField(value = inputJs, onValueChange = { inputJs = it }, minLines = 5) },
            confirmButton = { TextButton(onClick = { ctx.getSharedPreferences("tampermonkey", Context.MODE_PRIVATE).edit().putString("js", inputJs).apply(); showTmDialog = false }) { Text("保存并应用") } }
        )
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
