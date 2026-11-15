package com.downloadsource

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle // 修复：补全导入
import com.downloadsource.core.DownloadManager
import com.downloadsource.core.DownloadTask // 修复：补全导入
import com.downloadsource.core.UpdateManager
import com.downloadsource.ui.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var isLocked by remember { mutableStateOf(false) }
            LifecycleResumeEffect(Unit) {
                if (ThemeStore.getLockPassword(this@MainActivity) != null) {
                    isLocked = true
                }
                onPauseOrDispose {}
            }

            if (isLocked) {
                SecureLockScreen(onUnlock = { isLocked = false })
                return@setContent
            }

            val palette = rememberPalette()
            var updateInfo by remember { mutableStateOf<UpdateManager.ReleaseInfo?>(null) }
            LaunchedEffect(Unit) { updateInfo = UpdateManager.checkUpdate(BuildConfig.VERSION_NAME) }

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

            GradientBackground(palette) {
                MaterialTheme(typography = SafeTypography) { MainScaffold() }
            }
        }
    }
}

// 修复：恢复安全键盘
@Composable
fun SecureLockScreen(onUnlock: () -> Unit) {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    var inputPwd by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    
    val keys = listOf("1","2","3","4","5","6","7","8","9","Q","W","E","R","T","Y","U","I","O","P","A","S","D","F","G","H","J","K","L","Z","X","C","V","B","N","M","0","DEL")

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, null, modifier = Modifier.size(64.dp), tint = palette.primary.first())
        Spacer(Modifier.height(16.dp))
        Text("安全验证", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = palette.onSurface)
        
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(inputPwd.length.coerceAtMost(8)) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(palette.primary.first()))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(errorMsg, color = Color.Red, fontSize = 14.sp)

        Spacer(Modifier.height(32.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(300.dp)
        ) {
            items(keys) { key ->
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.surface)
                        .clickable {
                            errorMsg = ""
                            if (key == "DEL") {
                                if (inputPwd.isNotEmpty()) inputPwd = inputPwd.dropLast(1)
                            } else {
                                if (inputPwd.length < 8) inputPwd += key
                            }
                            
                            if (inputPwd.length >= 4) {
                                if (ThemeStore.verifyLockPassword(ctx, inputPwd)) {
                                    onUnlock()
                                } else if (inputPwd.length == 8) {
                                    errorMsg = "密码错误"
                                    inputPwd = ""
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (key == "DEL") Icon(Icons.Default.Backspace, null, tint = palette.onSurface)
                    else Text(key, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = palette.onSurface)
                }
            }
        }
    }
}

enum class Tab { DOWNLOAD, BROWSER, LIBRARY }

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold() {
    var currentTab by remember { mutableStateOf(Tab.DOWNLOAD) }
    var playingUri by remember { mutableStateOf<Uri?>(null) }
    var triggerSniff by remember { mutableStateOf(false) }
    var triggerHistory by remember { mutableStateOf(false) }

    if (playingUri != null) {
        VideoPlayerScreen(uri = playingUri!!, onBack = { playingUri = null })
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        
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
                    Tab.DOWNLOAD -> DownloadScreen()
                    Tab.BROWSER -> BrowserScreen(
                        onPlayVideo = { playingUri = Uri.fromFile(it) },
                        onPlayStream = { playingUri = Uri.parse(it) },
                        triggerSniff = triggerSniff,
                        onSniffHandled = { triggerSniff = false },
                        triggerHistory = triggerHistory,
                        onHistoryHandled = { triggerHistory = false },
                        externalUrlToLoad = null,
                        onUrlLoaded = {}
                    )
                    Tab.LIBRARY -> MediaLibraryScreen(onPlayVideo = { playingUri = Uri.fromFile(it) })
                }
            }
        }
        
        // 悬浮球
        var offsetX by remember { mutableFloatStateOf(screenWidthPx / 2) }
        var offsetY by remember { mutableFloatStateOf(screenHeightPx - 400f) }
        var showMenu by remember { mutableStateOf(false) }
        val ballSize = 48.dp
        val ballSizePx = with(density) { ballSize.toPx() }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(ballSize)
                .alpha(if (showMenu) 1f else 0.7f)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { showMenu = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x).coerceIn(0f, screenWidthPx - ballSizePx)
                            offsetY = (offsetY + dragAmount.y).coerceIn(0f, screenHeightPx - ballSizePx)
                        }
                    )
                }
                .clip(CircleShape)
                .background(LocalPalette.current.primary.first())
                .combinedClickable(onClick = { showMenu = !showMenu }, onLongClick = { showMenu = true }),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Menu, "Menu", tint = Color.White)
        }

        if (showMenu) {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                offset = DpOffset(with(density) { offsetX.toDp() }, with(density) { offsetY.toDp() })
            ) {
                DropdownMenuItem(text = { Text("下载列表") }, onClick = { currentTab = Tab.DOWNLOAD; showMenu = false })
                DropdownMenuItem(text = { Text("浏览器") }, onClick = { currentTab = Tab.BROWSER; showMenu = false })
                DropdownMenuItem(text = { Text("文件库") }, onClick = { currentTab = Tab.LIBRARY; showMenu = false })
            }
        }
    }
}

// 修复：恢复完整的 DownloadScreen 和任务状态
@Composable
fun DownloadScreen() {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    val tasks by DownloadManager.tasks.collectAsStateWithLifecycle() // 现在已导入
    var showAddDialog by remember { mutableStateOf(false) }
    var prefillUrl by remember { mutableStateOf("") }
    var dirPath by remember { mutableStateOf(com.downloadsource.storage.DirStore.resolveDownloadDir(ctx).absolutePath) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Download source", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = palette.onSurface)
                    Text("v${BuildConfig.VERSION_NAME}", color = palette.onSurfaceDim, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(16.dp))
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(tasks, key = { it.id }) { task ->
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

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = palette.primary.first(),
            shape = CircleShape
        ) { Text("+", style = MaterialTheme.typography.headlineMedium, color = Color.White) }
    }

    if (showAddDialog) {
        AddTaskDialog(initialUrl = prefillUrl, onDismiss = { showAddDialog = false }) { url, name, threads, speed, headers ->
            DownloadManager.add(ctx, url, name, threads, speed, headers, java.io.File(dirPath))
            showAddDialog = false
        }
    }
}
