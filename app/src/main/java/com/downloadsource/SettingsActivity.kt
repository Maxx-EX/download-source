package com.downloadsource

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.downloadsource.core.DownloadManager
import com.downloadsource.core.LocalWebServer
import com.downloadsource.core.UpdateManager
import com.downloadsource.storage.BrowserPrefs
import com.downloadsource.storage.DirStore
import com.downloadsource.ui.*
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()

    // 各种状态
    var checking by remember { mutableStateOf(false) }
    var serverIp by remember { mutableStateOf("未启动") }
    
    var themeStyle by remember { mutableIntStateOf(ThemeStore.getThemeStyle(ctx)) }
    var engine by remember { mutableStateOf(BrowserPrefs.getEngine(ctx)) }
    var snifferType by remember { mutableIntStateOf(BrowserPrefs.getSnifferType(ctx)) }
    
    var showPwdDialog by remember { mutableStateOf(false) }
    var inputPwd by remember { mutableStateOf("") }
    var currentLock by remember { mutableStateOf(ThemeStore.getLockPassword(ctx)) }
    var showDonate by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // 顶部标题栏
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { 
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Back", tint = palette.onSurface, modifier = Modifier.rotate(180f)) 
            }
            Text("设置 (v${BuildConfig.VERSION_NAME})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            
            // 1. 版本与更新
            SettingsGroup {
                SettingsRow("版本通道", BuildConfig.CHANNEL.uppercase())
                HorizontalDivider(color = palette.onSurface.copy(alpha = 0.1f))
                SettingsRow("检查更新", if (checking) "检测中..." else "检查最新版", Icons.Default.Update) {
                    if (!checking) {
                        checking = true
                        scope.launch {
                            val info = UpdateManager.checkUpdate(BuildConfig.VERSION_NAME)
                            checking = false
                            if (info != null) UpdateManager.showUpdateDialog(ctx, info) else Toast.makeText(ctx, "当前已是最新版", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            // 2. 外观主题
            SettingsGroup {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("主题风格", color = palette.onSurface, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("默认", "粉白", "极光", "日落", "深海", "赛博").forEachIndexed { index, name ->
                        Box(Modifier.clip(RoundedCornerShape(50)).background(if (themeStyle == index) palette.primary.first() else palette.onSurface.copy(alpha = 0.1f)).clickable { 
                            themeStyle = index
                            ThemeStore.setThemeStyle(ctx, index)
                            Toast.makeText(ctx, "重启应用生效", Toast.LENGTH_SHORT).show()
                        }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text(name, color = if (themeStyle == index) Color.White else palette.onSurface, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // 3. 浏览器与引擎设置
            SettingsGroup {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("搜索引擎", color = palette.onSurface, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Bing", "Baidu", "Google").forEach { e ->
                        Box(Modifier.clip(RoundedCornerShape(50)).background(if (engine == e) palette.primary.first() else palette.onSurface.copy(alpha = 0.1f)).clickable { 
                            engine = e; BrowserPrefs.setEngine(ctx, e) 
                        }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text(e, color = if (engine == e) Color.White else palette.onSurface, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                HorizontalDivider(color = palette.onSurface.copy(alpha = 0.1f))
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("资源嗅探引擎", color = palette.onSurface, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("JS 深度注入", "Python 底层拦截").forEachIndexed { index, name ->
                        Box(Modifier.clip(RoundedCornerShape(50)).background(if (snifferType == index) palette.primary.first() else palette.onSurface.copy(alpha = 0.1f)).clickable { 
                            snifferType = index; BrowserPrefs.setSnifferType(ctx, index) 
                        }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text(name, color = if (snifferType == index) Color.White else palette.onSurface, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // 4. 安全与保护
            SettingsGroup {
                SettingsRow("应用锁", if (currentLock != null) "已开启" else "未开启", Icons.Default.Lock) {
                    showPwdDialog = true
                }
                HorizontalDivider(color = palette.onSurface.copy(alpha = 0.1f))
                SettingsRow("防杀后台保护", "加入电池白名单", Icons.Default.BatteryChargingFull) {
                    val intent = Intent().apply {
                        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        data = Uri.parse("package:${ctx.packageName}")
                    }
                    ctx.startActivity(intent)
                }
            }

            // 5. 工具与清理
            SettingsGroup {
                SettingsRow("局域网互传", if (LocalWebServer.isRunning) "已开启" else "关闭中", Icons.Default.Wifi) {
                    if (LocalWebServer.isRunning) {
                        LocalWebServer.stop()
                        serverIp = "未启动"
                        Toast.makeText(ctx, "服务器已关闭", Toast.LENGTH_SHORT).show()
                    } else {
                        serverIp = LocalWebServer.start(ctx)
                        Toast.makeText(ctx, "请在电脑浏览器访问: $serverIp", Toast.LENGTH_LONG).show()
                    }
                }
                if (LocalWebServer.isRunning) {
                    Text("电脑访问: http://$serverIp", color = palette.primary.first(), modifier = Modifier.padding(start = 16.dp, bottom = 12.dp), fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = palette.onSurface.copy(alpha = 0.1f))
                SettingsRow("清理下载缓存", "清理临时分片", Icons.Default.DeleteSweep) {
                    val dir = DirStore.resolveDownloadDir(ctx)
                    var count = 0
                    dir.listFiles()?.filter { it.name.startsWith(".") && it.isDirectory }?.forEach { it.deleteRecursively(); count++ }
                    Toast.makeText(ctx, "已清理 $count 个缓存", Toast.LENGTH_SHORT).show()
                }
                HorizontalDivider(color = palette.onSurface.copy(alpha = 0.1f))
                SettingsRow("清空下载记录", "不删除本地文件", Icons.Default.Delete) {
                    DownloadManager.tasks.value.forEach { DownloadManager.remove(ctx, it.id) }
                    Toast.makeText(ctx, "记录已清空", Toast.LENGTH_SHORT).show()
                }
            }
            
            // 6. 关于与赞赏
            SettingsGroup {
                Row(Modifier.fillMaxWidth().clickable { 
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Maxx-EX/download-source"))) 
                }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painter = painterResource(id = R.drawable.ic_github), contentDescription = "GitHub", modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("GitHub 仓库", color = palette.onSurface, fontWeight = FontWeight.Medium)
                        Text("Maxx-EX/download-source", color = palette.onSurfaceDim, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = palette.onSurfaceDim)
                }
                HorizontalDivider(color = palette.onSurface.copy(alpha = 0.1f))
                SettingsRow("赞赏码", "请我喝杯咖啡", Icons.Default.Favorite) { showDonate = true }
            }
            
            Spacer(Modifier.height(24.dp))
        }
    }

    // 应用锁设置弹窗
    if (showPwdDialog) {
        AlertDialog(
            onDismissRequest = { showPwdDialog = false },
            title = { Text(if (currentLock == null) "设置应用锁" else "关闭应用锁") },
            text = {
                if (currentLock == null) {
                    OutlinedTextField(
                        value = inputPwd, onValueChange = { inputPwd = it }, 
                        label = { Text("输入密码 (至少4位)") }, 
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                } else {
                    Text("确认要清除应用锁密码吗？")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (currentLock == null) {
                        if (inputPwd.length >= 4) {
                            ThemeStore.setLockPassword(ctx, inputPwd)
                            currentLock = inputPwd
                            Toast.makeText(ctx, "应用锁已开启", Toast.LENGTH_SHORT).show()
                            showPwdDialog = false
                        } else Toast.makeText(ctx, "密码至少4位", Toast.LENGTH_SHORT).show()
                    } else {
                        ThemeStore.setLockPassword(ctx, null)
                        currentLock = null
                        Toast.makeText(ctx, "应用锁已关闭", Toast.LENGTH_SHORT).show()
                        showPwdDialog = false
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showPwdDialog = false }) { Text("取消") } }
        )
    }

    // 赞赏码弹窗
    if (showDonate) {
        Dialog(onDismissRequest = { showDonate = false }) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(palette.surface).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("感谢赞助 (๑•̀ㅂ•́)و✧", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = palette.onSurface)
                Spacer(Modifier.height(16.dp))
                Image(painter = painterResource(id = R.drawable.donate_qr), contentDescription = "QR", contentScale = ContentScale.Fit, modifier = Modifier.size(260.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp))
                Spacer(Modifier.height(16.dp))
                Button(onClick = { showDonate = false }, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
            }
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
