package com.downloadsource

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.downloadsource.core.DownloadManager
import com.downloadsource.storage.DirStore
import com.downloadsource.ui.*
import java.io.File

class MainActivity : ComponentActivity() {

    private var pickerResult: ((Uri) -> Unit)? = null
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.data?.let { uri ->
                DirStore.save(this, uri)
                pickerResult?.invoke(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isDark = androidx.compose.foundation.isSystemInDarkTheme()
            val palette = if (isDark) DarkPalette else LightPalette

            GradientBackground(palette) {
                MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                    MainScreen(
                        onPickFolder = { cb -> pickerResult = cb; picker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onPickFolder: ((Uri) -> Unit) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val palette = LocalPalette.current
    val tasks by DownloadManager.tasks.collectAsStateWithLifecycle()

    var urlInput by remember { mutableStateOf("") }
    var dirUri by remember { mutableStateOf(DirStore.get(ctx)) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        // 标题
        Text(
            "Download source",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            BuildConfig.CHANNEL.replaceFirstChar { it.uppercase() } + " 通道",
            color = palette.onSurfaceDim,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(18.dp))

        // 目录
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("保存到：", color = palette.onSurfaceDim, style = MaterialTheme.typography.bodySmall)
            Text(
                dirUri?.path?.substringAfterLast(':') ?: "未选择",
                color = palette.onSurface,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { onPickFolder { dirUri = it } }) {
                Icon(Icons.Default.Folder, null)
                Spacer(Modifier.width(4.dp))
                Text("选择")
            }
        }

        Spacer(Modifier.height(10.dp))

        // 手动添加任务
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                placeholder = { Text("粘贴下载链接 / 搜索…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    submit(urlInput, ctx, dirUri) { urlInput = "" }
                })
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { submit(urlInput, ctx, dirUri) { urlInput = "" } },
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.Default.Add, null)
            }
        }

        Spacer(Modifier.height(16.dp))

        // 任务列表
        if (tasks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有任务，粘贴一个链接开始下载吧", color = palette.onSurfaceDim)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tasks, key = { it.id }) { task ->
                    DownloadCard(
                        task = task,
                        onPause = { DownloadManager.pause(task.id) },
                        onResume = { dirUri?.let { DownloadManager.start(ctx, task.id, File(it.path!!)) } },
                        onDelete = { DownloadManager.remove(task.id) },
                    )
                }
            }
        }
    }
}

private fun submit(raw: String, ctx: android.content.Context, dirUri: Uri?, onDone: () -> Unit) {
    val url = raw.trim()
    if (url.isBlank()) return
    if (dirUri == null) {
        android.widget.Toast.makeText(ctx, "请先选择保存目录", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    DownloadManager.add(ctx, url, File(dirUri.path!!))
    onDone()
}
