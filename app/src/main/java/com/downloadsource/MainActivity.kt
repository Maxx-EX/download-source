package com.downloadsource

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.downloadsource.core.DownloadManager
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
        setContent {
            var isDark by remember { mutableStateOf(rememberDark()) }
            LifecycleResumeEffect(Unit) { isDark = rememberDark(); onPauseOrDispose {} }
            val palette = if (isDark) DarkPalette else LightPalette
            GradientBackground(palette) {
                MaterialTheme(
                    colorScheme = if (isDark) darkColorScheme() else lightColorScheme(),
                    typography = Typography(defaultFontFamily = FontFamily.SansSerif)
                ) {
                    MainScaffold(
                        onPickFolder = { cb -> pickerResult = cb; picker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)) },
                        onOpenSettings = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
                    )
                }
            }
        }
    }
}

enum class Tab { DOWNLOAD, BROWSER }

@Composable
fun MainScaffold(onPickFolder: ((Uri) -> Unit) -> Unit, onOpenSettings: () -> Unit) {
    var currentTab by remember { mutableStateOf(Tab.DOWNLOAD) }
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = LocalPalette.current.surface) {
                NavigationBarItem(selected = currentTab == Tab.DOWNLOAD, onClick = { currentTab = Tab.DOWNLOAD }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("下载") })
                NavigationBarItem(selected = currentTab == Tab.BROWSER, onClick = { currentTab = Tab.BROWSER }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("搜索") })
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                }.using(SizeTransform(clip = false))
            }, label = "tab"
        ) { tab ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    Tab.DOWNLOAD -> DownloadScreen(onPickFolder, onOpenSettings)
                    Tab.BROWSER -> BrowserScreen()
                }
            }
        }
    }
}

@Composable
fun DownloadScreen(onPickFolder: ((Uri) -> Unit) -> Unit, onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    val tasks by DownloadManager.tasks.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var dirPath by remember { mutableStateOf(DirStore.resolveDownloadDir(ctx).absolutePath) }
    
    LifecycleResumeEffect(Unit) { dirPath = DirStore.resolveDownloadDir(ctx).absolutePath; onPauseOrDispose {} }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Download source", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = palette.onSurface)
                    Text("${BuildConfig.CHANNEL.uppercase()} · v${BuildConfig.VERSION_NAME}", color = palette.onSurfaceDim, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, null, tint = palette.onSurface) }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("存至: ", color = palette.onSurfaceDim, style = MaterialTheme.typography.bodySmall)
                Text(dirPath, color = palette.onSurface, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                TextButton(onClick = { onPickFolder { dirPath = DirStore.resolveDownloadDir(ctx).absolutePath } }) { Text("更改") }
            }
            Spacer(Modifier.height(10.dp))
            if (tasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无任务 (￣▽￣)", color = palette.onSurfaceDim) }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(tasks, key = { it.id }) { task ->
                        DownloadCard(task, { DownloadManager.pause(ctx, task.id) }, { DownloadManager.start(ctx, task.id) }, { DownloadManager.remove(ctx, task.id) })
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = palette.primary.first(),
            shape = androidx.compose.foundation.shape.CircleShape
        ) {
            Text("+", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        }
    }

    if (showAddDialog) {
        AddTaskDialog(onDismiss = { showAddDialog = false }) { url, name, threads ->
            DownloadManager.add(ctx, url, name, threads, File(DirStore.resolveDownloadDir(ctx).absolutePath))
            showAddDialog = false
        }
    }
}
