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
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.downloadsource.core.DownloadManager
import com.downloadsource.core.UpdateManager
import com.downloadsource.ui.*
import java.io.File
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var isLocked by remember { mutableStateOf(false) }
            LifecycleResumeEffect(Unit) {
                if (ThemeStore.getLockPassword(this@MainActivity) != null) isLocked = true
                onPauseOrDispose {}
            }

            if (isLocked) {
                SecureLockScreen(onUnlock = { isLocked = false })
                return@setContent
            }

            val palette = rememberPalette()
            GradientBackground(palette) {
                MaterialTheme { MainScaffold() }
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
        
        // 悬浮球 (缩小尺寸，半透明防遮挡)
        var offsetX by remember { mutableFloatStateOf(screenWidthPx / 2) }
        var offsetY by remember { mutableFloatStateOf(screenHeightPx - 400f) }
        var showMenu by remember { mutableStateOf(false) }
        val ballSize = 48.dp
        val ballSizePx = with(density) { ballSize.toPx() }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(ballSize)
                // 使用半透明防止完全遮挡网页内容
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

@Composable
fun DownloadScreen() {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    val tasks by DownloadManager.tasks.collectAsStateWithLifecycle()
    
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("Download source", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = palette.onSurface)
            Text("v${BuildConfig.VERSION_NAME}", color = palette.onSurfaceDim, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tasks, key = { it.id }) { task ->
                    DownloadCard(task = task, onPause = { }, onResume = { }, onDelete = { }, onOpen = { })
                }
            }
        }
    }
}
