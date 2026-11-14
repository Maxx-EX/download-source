package com.downloadsource

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.downloadsource.core.DownloadManager
import com.downloadsource.core.UpdateManager
import com.downloadsource.storage.BrowserPrefs
import com.downloadsource.storage.DirStore
import com.downloadsource.ui.*
import java.io.File
import java.net.URLEncoder
import kotlin.math.roundToInt

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
            LaunchedEffect(Unit) { updateInfo = UpdateManager.checkUpdate(BuildConfig.VERSION_CODE) }

            if (updateInfo != null) {
                val ctx = LocalContext.current
                AlertDialog(
                    onDismissRequest = { updateInfo = null },
                    title = { Text("发现新版本 v${updateInfo?.version} 🎉") },
                    text = { Text(updateInfo?.changelog ?: "优化了下载体验。") },
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
                MaterialTheme(colorScheme = colorScheme, typography = SafeTypography) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(initialShareUrl: String?, onPickFolder: ((Uri) -> Unit) -> Unit, onOpenSettings: () -> Unit) {
    var currentTab by remember { mutableStateOf(Tab.DOWNLOAD) }
    var playingUri by remember { mutableStateOf<Uri?>(null) }
    var showFabMenu by remember { mutableStateOf(false) }
    
    // 状态提升：用于通知 Browser 打开特定面板
    var triggerSniff by remember { mutableStateOf(false) }
    var triggerHistory by remember { mutableStateOf(false) }

    if (playingUri != null) {
        VideoPlayerScreen(uri = playingUri!!, onBack = { playingUri = null })
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        
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
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (currentTab) {
                    Tab.DOWNLOAD -> DownloadScreen(initialShareUrl, onPickFolder, onOpenSettings)
                    Tab.BROWSER -> BrowserScreen(
                        onPlayVideo = { playingUri = Uri.fromFile(it) },
                        onPlayStream = { playingUri = Uri.parse(it) },
                        triggerSniff = triggerSniff,
                        onSniffHandled = { triggerSniff = false },
                        triggerHistory = triggerHistory,
                        onHistoryHandled = { triggerHistory = false }
                    )
                    Tab.LIBRARY -> MediaLibraryScreen(onPlayVideo = { playingUri = Uri.fromFile(it) })
                }
            }
        }
        
        // 修复版悬浮球：全屏可拖动
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(56.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(0f, screenWidth.value * 2.5f) // 简单限制边界
                        offsetY = (offsetY + dragAmount.y).coerceIn(-screenHeight.value * 2f, screenHeight.value * 2f)
                    }
                }
                .clip(CircleShape)
                .background(LocalPalette.current.primary.first().copy(alpha = 0.9f))
                .combinedClickable(
                    onClick = { currentTab = Tab.DOWNLOAD },
                    onLongClick = { showFabMenu = true }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Home, "Top", tint = Color.White)
        }
    }

    if (showFabMenu) {
        ModalBottomSheet(onDismissRequest = { showFabMenu = false }, containerColor = LocalPalette.current.surface) {
            Column(Modifier.padding(16.dp)) {
                Text("快捷控制台", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LocalPalette.current.onSurface)
                Spacer(Modifier.height(16.dp))
                BrowserMenuItem("资源嗅探列表", Icons.Default.Radar, LocalPalette.current.onSurface) { 
                    currentTab = Tab.BROWSER
                    triggerSniff = true // 发送指令
                    showFabMenu = false
                }
                BrowserMenuItem("历史浏览记录", Icons.Default.History, LocalPalette.current.onSurface) { 
                    currentTab = Tab.BROWSER
                    triggerHistory = true
                    showFabMenu = false
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun DownloadScreen(initialShareUrl: String?, onPickFolder: ((Uri) -> Unit) -> Unit, onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    val tasks by DownloadManager.tasks.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    
    var showAddDialog by remember { mutableStateOf(initialShareUrl != null) }
    var prefillUrl by remember { mutableStateOf(initialShareUrl ?: "") }
    var dirPath by remember { mutableStateOf(DirStore.resolveDownloadDir(ctx).absolutePath) }
    var searchQuery by remember { mutableStateOf("") }
    
    // 过滤功能
    var filterMode by remember { mutableStateOf("全部") }
    val filters = listOf("全部", "下载中", "已完成", "失败")

    LifecycleResumeEffect(Unit) { dirPath = DirStore.resolveDownloadDir(ctx).absolutePath; onPauseOrDispose {} }

    val doSearch = {
        val q = searchQuery.trim()
        if (q.isNotEmpty()) {
            keyboardController?.hide()
            if (q.startsWith("http") || q.contains(".com") || q.contains(".cn") || q.contains("pan.") || q.contains("lanzou")) {
                DownloadManager.add(ctx, q, null, 8, 0, emptyMap(), File(dirPath))
                searchQuery = ""
            } else {
                val engine = BrowserPrefs.getEngine(ctx)
                val encoded = URLEncoder.encode(q, "UTF-8")
                val url = when(engine) {
                    "Baidu" -> "https://www.baidu.com/s?wd=$encoded"
                    "Google" -> "https://www.google.com/search?q=$encoded"
                    else -> "https://www.bing.com/search?q=$encoded"
                }
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Download source", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = palette.onSurface)
                    Text("${BuildConfig.CHANNEL.uppercase()} · v${BuildConfig.VERSION_NAME}", color = palette.onSurfaceDim, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, null, tint = palette.onSurface) }
            }
            Spacer(Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(palette.surface).padding(horizontal = 16.dp, vertical = 4.dp)) {
                Icon(Icons.Default.Search, null, tint = palette.onSurfaceDim)
                BasicTextField(
                    value = searchQuery, onValueChange = { searchQuery = it }, textStyle = TextStyle(color = palette.onSurface),
                    cursorBrush = SolidColor(palette.primary.first()), singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { doSearch() }),
                    decorationBox = { inner -> if (searchQuery.isEmpty()) Text("输入链接或搜索关键词...", color = palette.onSurfaceDim); inner() },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { doSearch() }) { Icon(Icons.Default.ArrowForward, null, tint = palette.primary.first()) }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // 状态过滤栏
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEach { f ->
                    val selected = filterMode == f
                    Box(Modifier.clip(RoundedCornerShape(50)).background(if (selected) palette.primary.first() else palette.surface).clickable { filterMode = f }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(f, color = if (selected) Color.White else palette.onSurface, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            
            Spacer(Modifier.height(10.dp))
            
            val filteredTasks = tasks.filter { 
                when(filterMode) {
                    "下载中" -> it.status == com.downloadsource.core.TaskStatus.Running
                    "已完成" -> it.status == com.downloadsource.core.TaskStatus.Completed
                    "失败" -> it.status == com.downloadsource.core.TaskStatus.Failed
                    else -> true
                }
            }

            if (filteredTasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无任务 (￣▽￣)", color = palette.onSurfaceDim) }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(filteredTasks, key = { it.id }) { task ->
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

        FloatingActionButton(
            onClick = { showAddDialog = true; prefillUrl = "" },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = palette.primary.first(),
            shape = androidx.compose.foundation.shape.CircleShape
        ) {
            Text("+", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        }
    }

    if (showAddDialog) {
        AddTaskDialog(initialUrl = prefillUrl, onDismiss = { showAddDialog = false }) { url, name, threads, speed, headers ->
            DownloadManager.add(ctx, url, name, threads, speed, headers, File(DirStore.resolveDownloadDir(ctx).absolutePath))
            showAddDialog = false
        }
    }
}
