package com.downloadsource

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.downloadsource.core.UpdateManager
import com.downloadsource.ui.*
import com.downloadsource.utils.PyRuleEngine

class MainActivity : ComponentActivity() {
    private fun shouldLock(): Boolean {
        val pwd = ThemeStore.getLockPassword(this) ?: return false
        return (System.currentTimeMillis() - ThemeStore.getLastUnlockTime(this)) > 900_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PyRuleEngine.init(this)
        setContent {
            val palette = rememberPalette()
            Box(Modifier.fillMaxSize().background(palette.background.first())) {
                GradientBackground(palette) {
                    MaterialTheme(typography = SafeTypography) {
                        var isLocked by remember { mutableStateOf(shouldLock()) }
                        var clipboardUrl by remember { mutableStateOf<String?>(null) }
                        // 修复：记录上次处理的剪贴板，防止死循环弹窗
                        val lastClipUrl = remember { mutableStateOf("") }
                        
                        LifecycleResumeEffect(Unit) {
                            if (shouldLock()) isLocked = true
                            
                            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val text = cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                            if (text != null && (text.startsWith("http") || text.contains("pan.quark"))) {
                                if (text != lastClipUrl.value) {
                                    clipboardUrl = text
                                    lastClipUrl.value = text
                                }
                            }
                            onPauseOrDispose {}
                        }

                        if (isLocked) { 
                            SecureLockScreen(onUnlock = { isLocked = false }) 
                        } else {
                            var updateInfo by remember { mutableStateOf<UpdateManager.ReleaseInfo?>(null) }
                            LaunchedEffect(Unit) { updateInfo = UpdateManager.checkUpdate(BuildConfig.VERSION_NAME) }
                            
                            if (clipboardUrl != null) {
                                AlertDialog(
                                    onDismissRequest = { clipboardUrl = null },
                                    title = { Text("检测到剪贴板链接") },
                                    text = { Text("发现: ${clipboardUrl?.take(30)}...\n是否立即下载？") },
                                    confirmButton = { TextButton(onClick = { 
                                        com.downloadsource.core.DownloadManager.add(this@MainActivity, clipboardUrl!!, null, 8, 0, emptyMap(), com.downloadsource.storage.DirStore.resolveDownloadDir(this@MainActivity))
                                        clipboardUrl = null 
                                    }) { Text("立即下载") } },
                                    dismissButton = { TextButton(onClick = { clipboardUrl = null }) { Text("忽略") } }
                                )
                            }
                            
                            if (updateInfo != null) { 
                                val ctx = LocalContext.current
                                AlertDialog(
                                    onDismissRequest = { updateInfo = null }, 
                                    title = { Text("发现新版本") }, 
                                    text = { Text(updateInfo?.changelog ?: "") }, 
                                    confirmButton = { 
                                        TextButton(onClick = { 
                                            com.downloadsource.core.DownloadManager.add(ctx, updateInfo!!.downloadUrl, "DownloadSource_Update_${updateInfo?.version}.apk", 8, 0, emptyMap(), com.downloadsource.storage.DirStore.resolveDownloadDir(ctx))
                                            updateInfo = null 
                                        }) { Text("立即更新") } 
                                    },
                                    dismissButton = { TextButton(onClick = { updateInfo = null }) { Text("稍后") } }
                                )
                            }
                            
                            MainScaffold()
                            
                            // 监听下载进度，实时刷新桌面小组件
                            val tasks by com.downloadsource.core.DownloadManager.tasks.collectAsState()
                            LaunchedEffect(tasks) {
                                val intent = android.content.Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                                intent.setClass(this@MainActivity, com.downloadsource.core.DownloadWidget::class.java)
                                val ids = android.appwidget.AppWidgetManager.getInstance(this@MainActivity).getAppWidgetIds(
                                    android.content.ComponentName(this@MainActivity, com.downloadsource.core.DownloadWidget::class.java)
                                )
                                intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                                sendBroadcast(intent)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SecureLockScreen(onUnlock: () -> Unit) {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    var inputPwd by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    val keys = listOf("1","2","3","4","5","6","7","8","9","Q","W","E","R","T","Y","U","I","O","P","A","S","D","F","G","H","J","K","L","Z","X","C","V","B","N","M","0","DEL")
    Column(Modifier.fillMaxSize().background(palette.background.first()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Lock, null, modifier = Modifier.size(64.dp), tint = palette.primary.first())
        Text("安全验证", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = palette.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 24.dp)) { repeat(inputPwd.length.coerceAtMost(8)) { Box(Modifier.size(16.dp).clip(CircleShape).background(palette.primary.first())) } }
        Text(errorMsg, color = Color.Red, fontSize = 14.sp)
        LazyVerticalGrid(columns = GridCells.Fixed(6), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().height(300.dp)) {
            items(keys) { key ->
                Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(palette.surface).clickable {
                    errorMsg = ""
                    if (key == "DEL") { if (inputPwd.isNotEmpty()) inputPwd = inputPwd.dropLast(1) } else { if (inputPwd.length < 8) inputPwd += key }
                    if (inputPwd.length >= 4) { if (ThemeStore.verifyLockPassword(ctx, inputPwd)) onUnlock() else if (inputPwd.length == 8) { errorMsg = "密码错误"; inputPwd = "" } }
                }, contentAlignment = Alignment.Center) { if (key == "DEL") Icon(Icons.Default.Backspace, null, tint = palette.onSurface) else Text(key, fontSize = 20.sp, color = palette.onSurface) }
            }
        }
    }
}
