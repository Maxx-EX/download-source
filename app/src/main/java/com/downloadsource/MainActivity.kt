package com.downloadsource

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.downloadsource.storage.BrowserHistoryStore
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

@Composable
fun MainScaffold(initialShareUrl: String?, onPickFolder: ((Uri) -> Unit) -> Unit, onOpenSettings: () -> Unit) {
    var currentTab by remember { mutableStateOf(Tab.DOWNLOAD) }
    var playingUri by remember { mutableStateOf<Uri?>(null) }
    var showFabMenu by remember { mutableStateOf(false) }

    if (playingUri != null) {
        VideoPlayerScreen(uri = playingUri!!, onBack = { playingUri = null })
        return
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = LocalPalette.current.surface) {
                    // 小功能：底部导航栏点击动画
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
                        onShowSnackbar = {}
                    )
                    Tab.LIBRARY -> MediaLibraryScreen(onPlayVideo = { playingUri = Uri.fromFile(it) })
                }
            }
        }
        
        // 大功能：可拖拽悬浮球，长按弹出快捷历史记录与嗅探
        DraggableFloatingButton(
            onClick = { currentTab = Tab.DOWNLOAD },
            onLongClick = { showFabMenu = true }
        )
    }

    // 小功能：长按悬浮球弹出的高级菜单
    if (showFabMenu) {
        ModalBottomSheet(onDismissRequest = { showFabMenu = false }, containerColor = LocalPalette.current.surface) {
            Column(Modifier.padding(16.dp)) {
                Text("快捷控制台", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LocalPalette.current.onSurface)
                Spacer(Modifier.height(16.dp))
                BrowserMenuItem("历史浏览记录", Icons.Default.History, LocalPalette.current.onSurface) { 
                    currentTab = Tab.BROWSER
                    showFabMenu = false
                }
                BrowserMenuItem("资源嗅探列表", Icons.Default.Radar, LocalPalette.current.onSurface) { 
                    currentTab = Tab.BROWSER
                    showFabMenu = false
                }
                BrowserMenuItem("回到多页首页", Icons.Default.Tab, LocalPalette.current.onSurface) { 
                    currentTab = Tab.BROWSER
                    showFabMenu = false
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DraggableFloatingButton(onClick: () -> Unit, onLongClick: () -> Unit) {
    val palette = LocalPalette.current
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(48.dp)
            .draggable(orientation = Orientation.Vertical, state = rememberDraggableState { delta -> offsetY += delta })
            .draggable(orientation = Orientation.Horizontal, state = rememberDraggableState { delta -> offsetX += delta })
            .clip(CircleShape)
            .background(palette.primary.first().copy(alpha = 0.9f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Home, "Top", tint = Color.White)
    }
}

@Composable
fun DownloadScreen(initialShareUrl: String?, onPickFolder: ((Uri) -> Unit) -> Unit, onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    val tasks by DownloadManager.tasks.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    
    var showAddDialog by remember { mutableStateOf(initialShareUrl != null) }
    var prefillUrl by remember { mutableStateOf(initialShareUrl ?: "") }
    var dirPath by remember { mutableStateOf(DirStore.resolveDownloadDir(ctx).absolutePath) }
    var searchQuery by remember { mutableStateOf("") }
    
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedTasks = remember { mutableStateListOf<String>() }

    LifecycleResumeEffect(Unit) { dirPath = DirStore.resolveDownloadDir(ctx).absolutePath; onPauseOrDispose {} }

    val doSearch = {
        val q = searchQuery.trim()
        if (q.isNotEmpty()) {
            keyboardController?.hide()
            focusManager.clearFocus()
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isSelectionMode) "已选择 ${selectedTasks.size} 项" else "Download source", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = palette.onSurface)
                        if (BuildConfig.CHANNEL == "zeta") {
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.background(Color.Red, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                Text("内测", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
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
            Spacer(Modifier.height(16.dp))
            
            // 主页搜索框
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(palette.surface).padding(horizontal = 16.dp, vertical = 4.dp)) {
                Icon(Icons.Default.Search, null, tint = palette.onSurfaceDim)
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = TextStyle(color = palette.onSurface),
                    cursorBrush = SolidColor(palette.primary.first()),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { doSearch() }),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) Text("输入链接或搜索关键词...", color = palette.onSurfaceDim)
                        inner()
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { doSearch() }) { Icon(Icons.Default.ArrowForward, null, tint = palette.primary.first()) }
                }
            }
            
            Spacer(Modifier.height(16.dp))
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
        AddTaskDialog(initialUrl = prefillUrl, onDismiss = { showAddDialog = false }) { url, name, threads, speed, headers ->
            DownloadManager.add(ctx, url, name, threads, speed, headers, File(DirStore.resolveDownloadDir(ctx).absolutePath))
            showAddDialog = false
        }
    }
}
