package com.downloadsource.core

import android.content.Context
import android.media.MediaScannerConnection
import com.downloadsource.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLDecoder

object DownloadManager {
    val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val segmenter = Segmenter(httpClient)
    private val jobs = mutableMapOf<String, Job>()
    private val mutex = Mutex()
    
    // 方向一：允许用户配置最大并发数 (默认 3)
    var MAX_CONCURRENT = 3 
        set(value) { field = value.coerceIn(1, 10) }

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    fun init(context: Context) {
        scope.launch {
            _tasks.value = TaskStore.loadAsync(context)
            _tasks.value.forEach { if (it.status == TaskStatus.Running) update(context, it.id) { t -> t.copy(status = TaskStatus.Pending, speed = 0) } }
            NetworkMonitor.start(context)
        }
    }

    fun add(context: Context, url: String, customName: String?, threads: Int, speedLimitKb: Long, headers: Map<String, String>, dir: File) {
        val tempName = if (!customName.isNullOrBlank()) customName else "parsing_${System.currentTimeMillis()}"
        
        // 方向三：自动分类存储
        val finalDir = createCategoryDir(dir, tempName)

        val task = DownloadTask(url = url, fileName = tempName, dirPath = finalDir.absolutePath, threads = threads, speedLimitKb = speedLimitKb, headers = headers)
        
        scope.launch {
            mutex.withLock { _tasks.value = _tasks.value + task }
            TaskStore.save(context, _tasks.value)
            
            try {
                val shareInfo = NetDiskParser.detect(url)
                if (shareInfo.type != NetDiskParser.DiskType.UNKNOWN) {
                    if (BuildConfig.CHANNEL == "zeta") {
                        val results = DiskParser.parse(context, shareInfo)
                        remove(context, task.id)
                        
                        for (file in results) {
                            val catDir = createCategoryDir(dir, file.name)
                            val newTask = DownloadTask(url = file.url, fileName = file.name, dirPath = catDir.absolutePath, threads = threads, speedLimitKb = speedLimitKb, headers = headers)
                            mutex.withLock { _tasks.value = _tasks.value + newTask }
                            TaskStore.save(context, _tasks.value)
                            startTask(context, newTask.id)
                        }
                    } else {
                        throw Exception("网盘解析为内测功能，请下载 Zeta 版体验")
                    }
                    return@launch
                }
                
                var parsedUrl = MirrorResolver.resolveFastest(context, url)
                if (parsedUrl.startsWith("data:")) {
                    val mime = parsedUrl.substringAfter("data:").substringBefore(";")
                    val ext = mimeToExtension(mime)
                    val name = if (customName.isNullOrBlank()) "blob_${System.currentTimeMillis()}$ext" else customName
                    val catDir = createCategoryDir(dir, name)
                    update(context, task.id) { it.copy(url = parsedUrl, fileName = name, dirPath = catDir.absolutePath) }
                    startTask(context, task.id)
                    return@launch
                }

                val realName = if (customName.isNullOrBlank()) probeRealFileName(parsedUrl, headers) else customName
                val catDir = createCategoryDir(dir, realName)
                update(context, task.id) { it.copy(url = parsedUrl, fileName = realName, dirPath = catDir.absolutePath) }
                startTask(context, task.id)
            } catch (e: Exception) {
                update(context, task.id) { it.copy(status = TaskStatus.Failed, error = e.message ?: "解析失败") }
            }
        }
    }

    // 自动根据后缀创建文件夹
    private fun createCategoryDir(baseDir: File, fileName: String): File {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val sub = when (ext) {
            "mp4", "mkv", "avi", "mov", "ts" -> "视频"
            "mp3", "flac", "wav", "m4a" -> "音频"
            "apk" -> "安装包"
            "jpg", "png", "gif", "webp" -> "图片"
            "pdf", "doc", "docx", "xls", "xlsx", "txt" -> "文档"
            else -> "其他"
        }
        val dir = File(baseDir, sub)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun start(context: Context, id: String) { scope.launch { startTask(context, id) } }

    private fun startTask(context: Context, id: String) {
        val runningCount = _tasks.value.count { it.status == TaskStatus.Running }
        if (runningCount >= MAX_CONCURRENT) { update(context, id) { it.copy(status = TaskStatus.Pending) }; return }

        val t = _tasks.value.find { it.id == id } ?: return
        if (t.status == TaskStatus.Running) return
        update(context, id) { it.copy(status = TaskStatus.Running, error = null) }
        DownloadService.start(context)

        jobs[id] = scope.launch {
            try {
                val dir = File(t.dirPath); if (!dir.exists()) dir.mkdirs()
                val out = segmenter.download(t.id, t.url, dir, t.fileName, t.threads, t.speedLimitKb, t.headers) { d, total, speed ->
                    update(context, id) { it.copy(downloadedBytes = d, totalBytes = total, speed = speed) }
                    DownloadService.updateProgress(context, t.fileName, d, total, speed)
                }
                update(context, id) { it.copy(status = TaskStatus.Completed, downloadedBytes = it.totalBytes, speed = 0, outputPath = out.absolutePath) }
                
                try {
                    val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION") vibrator.vibrate(200)
                    }
                } catch (e: Exception) { /* 忽略 */ }

                if (out.name.endsWith(".apk", true)) {
                    installApk(context, out)
                } else {
                    MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), null, null)
                }
                checkPending(context)
            } catch (e: CancellationException) {
                segmenter.cancelTask(t.id)
                update(context, id) { it.copy(status = TaskStatus.Paused, speed = 0) }
                throw e
            } catch (e: Exception) {
                update(context, id) { it.copy(status = TaskStatus.Failed, error = e.message ?: "未知错误", speed = 0) }
                checkPending(context)
            }
        }
    }
    
    private fun installApk(context: Context, file: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {}
    }

    private fun checkPending(context: Context) {
        if (_tasks.value.none { it.status == TaskStatus.Running || it.status == TaskStatus.Pending }) DownloadService.stop(context)
        else {
            val next = _tasks.value.firstOrNull { it.status == TaskStatus.Pending }
            if (next != null) startTask(context, next.id)
        }
    }

    fun pause(context: Context, id: String) {
        segmenter.cancelTask(id)
        jobs[id]?.cancel(); jobs.remove(id)
        update(context, id) { it.copy(status = TaskStatus.Paused, speed = 0) }
        checkPending(context)
    }

    fun pauseAll(context: Context) { _tasks.value.filter { it.status == TaskStatus.Running }.forEach { pause(context, it.id) } }
    fun resumeAll(context: Context) { autoResumeTasks(context) }

    fun autoResumeTasks(context: Context) {
        _tasks.value.filter { it.status == TaskStatus.Pending || it.status == TaskStatus.Paused }.forEach { startTask(context, it.id) }
    }

    fun rename(context: Context, id: String, newName: String) {
        val t = _tasks.value.find { it.id == id } ?: return
        val dir = File(t.dirPath); val oldFile = File(dir, t.fileName); val newFile = File(dir, newName)
        if (oldFile.exists()) oldFile.renameTo(newFile)
        update(context, id) { it.copy(fileName = newName, outputPath = newFile.absolutePath) }
    }

    fun remove(context: Context, id: String) {
        segmenter.cancelTask(id)
        jobs[id]?.cancel(); jobs.remove(id)
        _tasks.value = _tasks.value.filterNot { it.id == id }
        TaskStore.save(context, _tasks.value)
        checkPending(context)
    }

    private fun update(context: Context, id: String, block: (DownloadTask) -> DownloadTask) {
        _tasks.value = _tasks.value.map { if (it.id == id) block(it) else it }
        TaskStore.save(context, _tasks.value)
    }

    private fun probeRealFileName(url: String, headers: Map<String, String>): String {
        try {
            if (url.contains(".m3u8")) return "video_${System.currentTimeMillis()}.mp4"
            val builder = Request.Builder().url(url).head()
            headers.forEach { (k, v) -> builder.header(k, v) }
            httpClient.newCall(builder.build()).execute().use { resp ->
                val disposition = resp.header("Content-Disposition")
                if (disposition != null && disposition.contains("filename=")) {
                    var name = disposition.substringAfter("filename=").trim('"', ' ')
                    if (name.contains("''")) name = name.substringAfter("''")
                    return URLDecoder.decode(name, "UTF-8")
                }
                val path = resp.request.url.encodedPath
                var last = path.substringAfterLast('/')
                if (last.isNotBlank() && last.contains('.')) return URLDecoder.decode(last, "UTF-8")
                
                val mime = resp.header("Content-Type") ?: ""
                val ext = mimeToExtension(mime)
                
                if (last.isBlank()) last = "download_${System.currentTimeMillis()}"
                return "$last$ext"
            }
        } catch (e: Exception) { return "file_${System.currentTimeMillis()}.bin" }
    }

    private fun mimeToExtension(mime: String): String {
        return when {
            mime.contains("video/mp4") -> ".mp4"
            mime.contains("video/quicktime") -> ".mov"
            mime.contains("video/x-matroska") -> ".mkv"
            mime.contains("audio/mpeg") -> ".mp3"
            mime.contains("application/vnd.android.package-archive") -> ".apk"
            mime.contains("application/pdf") -> ".pdf"
            mime.contains("image/jpeg") -> ".jpg"
            mime.contains("image/png") -> ".png"
            mime.contains("application/zip") -> ".zip"
            else -> ".bin"
        }
    }
}
