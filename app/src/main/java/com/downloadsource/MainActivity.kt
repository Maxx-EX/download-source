package com.downloadsource

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.downloadsource.core.DownloadManager
import com.downloadsource.core.UpdateManager
import com.downloadsource.storage.DirStore
import com.downloadsource.ui.*
import java.io.File

class MainActivity : ComponentActivity() {
    private var pickerResult: ((Uri) -> Unit)? = null
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) {
            r.data?.data?.let { uri -> DirStore.save(this, uri); pickerResult?.invoke(uri) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedText = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null

        setContent {
            val palette = rememberPalette()
            var updateInfo by remember { mutableStateOf<UpdateManager.ReleaseInfo?>(null) }
            LaunchedEffect(Unit) { updateInfo = UpdateManager.checkUpdate(BuildConfig.VERSION_NAME) }

            if (updateInfo != null) {
                val ctx = LocalContext.current
                AlertDialog(
                    onDismissRequest = { updateInfo = null },
                    title = { Text("发现新版本 v${updateInfo?.version} 🎉") },
                    text = { Text(updateInfo?.changelog ?: "优化了下载与播放体验。") },
                    confirmButton = { TextButton(onClick = { UpdateManager.openDownload(ctx, updateInfo?.downloadUrl ?: ""); updateInfo = null }) { Text("去下载") } },
                    dismissButton = { TextButton(onClick = { updateInfo = null }) { Text("稍后") } }
                )
            }

            val colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(this) else darkColorScheme()
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicLightColorScheme(this) else lightColorScheme()
            }

            GradientBackground(palette) {
                MaterialTheme(colorScheme = colorScheme) {
                    MainScaffold(
                        initialShareUrl = sharedText,
                        onPickFolder = { cb -> pickerResult = cb; picker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)) },
                        onOpenSettings = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
                    )
                }
            }
        }
    }
}

enum class Tab { DOWNLOAD, BROWSER, LIBRARY }

@Composable
fun MainScaffold(initialShareUrl: String?, onPickFolder: ((Uri) -> Unit) -> Unit, onOpenSettings: () -> Unit) {
    var currentTab by remember { mutableStateOf(Tab.DOWNLOAD) }
    var playingUri by remember { mutableStateOf<Uri?>(null) }

    if (playingUri != null) {
        VideoPlayerScreen(uri = playingUri!!, onBack = { playingUri = null })
        return
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = LocalPalette.current.surface) {
                NavigationBarItem(selected = currentTab == Tab.DOWNLOAD, onClick = { currentTab = Tab.DOWNLOAD }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("下载") })
                NavigationBarItem(selected = currentTab == Tab.BROWSER, onClick = { currentTab = Tab.BROWSER }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("搜索") })
                NavigationBarItem(selected = currentTab == Tab.LIBRARY, onClick = { currentTab = Tab.LIBRARY }, icon = { Icon(Icons.Default.Folder, null) }, label = { Text("文件库") })
            }
        }
    ) { padding ->
        // 修复：使用 when 直接切换，完全避免 Compose 动画缓存导致的 WebView 泄漏闪退
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (currentTab) {
                Tab.DOWNLOAD -> DownloadScreen(initialShareUrl, onPickFolder, onOpenSettings)
                Tab.BROWSER -> BrowserScreen(
                    onPlayVideo = { playingUri = Uri.fromFile(it) },
                    onPlayStream = { playingUri = Uri.parse(it) }
                )
                Tab.LIBRARY -> MediaLibraryScreen(onPlayVideo = { playingUri = Uri.fromFile(it) })
            }
        }
    }
}

@Composable
fun DownloadScreen(initialShareUrl: String?, onPickFolder: ((Uri) -> Unit) -> Unit, onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    val tasks by DownloadManager.tasks.collectAsStateWithLifecycle()
    
    var showAddDialog by remember { mutableStateOf(initialShareUrl != null) }
    var prefillUrl by remember { mutableStateOf(initialShareUrl ?: "") }
    var dirPath by remember { mutableStateOf(DirStore.resolveDownloadDir(ctx).absolutePath) }
    
    // 多选模式
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedTasks = remember { mutableStateListOf<String>() }

    LifecycleResumeEffect(Unit) { dirPath = DirStore.resolveDownloadDir(ctx).absolutePath; onPauseOrDispose {} }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (isSelectionMode) "已选择 ${selectedTasks.size} 项" else "Download source", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = palette.onSurface)
                    Text("${BuildConfig.CHANNEL.uppercase()} · v${BuildConfig.VERSION_NAME}", color = palette.onSurfaceDim, style = MaterialTheme.typography.bodySmall)
                }
                if (isSelectionMode) {
                    IconButton(onClick = { 
                        selectedTasks.forEach { DownloadManager.remove(ctx, it) }
                        selectedTasks.clear()
                        isSelectionMode = false
                    }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                    IconButton(onClick = { isSelectionMode = false; selectedTasks.clear() }) { Icon(Icons.Default.Close, null, tint = palette.onSurface) }
                } else {
                    if (tasks.isNotEmpty()) IconButton(onClick = { isSelectionMode = true }) { Icon(Icons.Default.Checklist, null, tint = palette.onSurface) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, null, tint = palette.onSurface) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("存至: ", color = palette.onSurfaceDim, style = MaterialTheme.typography.bodySmall)
                Text(dirPath, color = palette.onSurface, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                if (!isSelectionMode) TextButton(onClick = { onPickFolder { dirPath = DirStore.resolveDownloadDir(ctx).absolutePath } }) { Text("更改") }
            }
            Spacer(Modifier.height(10.dp))
            
            if (tasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无任务 (￣▽￣)", color = palette.onSurfaceDim) }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(tasks, key = { it.id }) { task ->
                        if (isSelectionMode) {
                            Row(Modifier.fillMaxWidth().clickable {
                                if (selectedTasks.contains(task.id)) selectedTasks.remove(task.id) else selectedTasks.add(task.id)
                            }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = selectedTasks.contains(task.id), onCheckedChange = { 
                                    if (it) selectedTasks.add(task.id) else selectedTasks.remove(task.id) 
                                })
                                DownloadCard(task = task, onPause = {}, onResume = {}, onDelete = {}, onOpen = {})
                            }
                        } else {
                            DownloadCard(
                                task = task, 
                                onPause = { DownloadManager.pause(ctx, task.id) }, 
                                onResume = { DownloadManager.start(ctx, task.id) }, 
                                onDelete = { DownloadManager.remove(ctx, task.id) },
                                onOpen = { }
                            )
                        }
                    }
                }
            }
        }

        if (!isSelectionMode) {
            FloatingActionButton(
                onClick = { showAddDialog = true; prefillUrl = "" },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                containerColor = palette.primary.first(),
                shape = androidx.compose.foundation.shape.CircleShape
            ) {
                Text("+", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            }
        }
    }

    if (showAddDialog) {
        AddTaskDialog(initialUrl = prefillUrl, onDismiss = { showAddDialog = false }) { url, name, threads, speed ->
            DownloadManager.add(ctx, url, name, threads, speed, h, File(DirStore.resolveDownloadDir(ctx).absolutePath))
            showAddDialog = false
        }
    }
}
