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
    val httpClient = OkHttpClient.Builder().connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS).readTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val segmenter = Segmenter(httpClient)
    private val jobs = mutableMapOf<String, Job>()
    private val mutex = Mutex()
    var MAX_CONCURRENT = 3

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    fun init(context: Context) {
        scope.launch {
            _tasks.value = TaskStore.loadAsync(context)
            _tasks.value.forEach { if (it.status == TaskStatus.Running) updateTaskState(context, it.id) { t -> t.copy(status = TaskStatus.Pending, speed = 0) } }
            NetworkMonitor.start(context)
        }
    }

    fun add(context: Context, url: String, customName: String?, threads: Int, speedLimitKb: Long, headers: Map<String, String>, baseDir: File) {
        val tempName = customName ?: "parsing_${System.currentTimeMillis()}"
        val task = DownloadTask(url = url, fileName = tempName, dirPath = baseDir.absolutePath, threads = threads, speedLimitKb = speedLimitKb, headers = headers)
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
                            val catDir = createCategoryDir(baseDir, file.name)
                            val newTask = DownloadTask(url = file.url, fileName = file.name, dirPath = catDir.absolutePath, threads = threads, speedLimitKb = speedLimitKb, headers = headers)
                            mutex.withLock { _tasks.value = _tasks.value + newTask }
                            TaskStore.save(context, _tasks.value)
                            startTask(context, newTask.id)
                        }
                    } else throw Exception("网盘解析为内测功能")
                    return@launch
                }
                var parsedUrl = MirrorResolver.resolveFastest(context, url)
                val realName = if (customName == null) probeRealFileName(parsedUrl, headers) else customName
                val catDir = createCategoryDir(baseDir, realName)
                updateTaskState(context, task.id) { it.copy(url = parsedUrl, fileName = realName, dirPath = catDir.absolutePath) }
                startTask(context, task.id)
            } catch (e: Exception) { updateTaskState(context, task.id) { it.copy(status = TaskStatus.Failed, error = e.message) } }
        }
    }

    private fun createCategoryDir(baseDir: File, fileName: String): File {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val sub = when (ext) {
            "mp4", "mkv", "avi", "mov", "ts" -> "视频"
            "mp3", "flac", "wav" -> "音频"
            "apk" -> "安装包"
            else -> "其他"
        }
        return File(baseDir, sub).apply { if (!exists()) mkdirs() }
    }

    fun start(context: Context, id: String) { scope.launch { startTask(context, id) } }
    private fun startTask(context: Context, id: String) {
        if (_tasks.value.count { it.status == TaskStatus.Running } >= MAX_CONCURRENT) { updateTaskState(context, id) { it.copy(status = TaskStatus.Pending) }; return }
        val t = _tasks.value.find { it.id == id } ?: return
        if (t.status == TaskStatus.Running) return
        updateTaskState(context, id) { it.copy(status = TaskStatus.Running, error = null) }
        DownloadService.start(context)
        jobs[id] = scope.launch {
            try {
                val out = segmenter.download(t.id, t.url, File(t.dirPath), t.fileName, t.threads, t.speedLimitKb, t.headers) { d, total, speed -> _tasks.value = _tasks.value.map { if (it.id == id) it.copy(downloadedBytes = d, totalBytes = total, speed = speed) else it } }
                updateTaskState(context, id) { it.copy(status = TaskStatus.Completed, downloadedBytes = it.totalBytes, speed = 0, outputPath = out.absolutePath) }
                if (out.name.endsWith(".apk", true)) {
                    try {
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/vnd.android.package-archive"); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK) })
                    } catch (e: Exception) {}
                }
                checkPending(context)
            } catch (e: Exception) { updateTaskState(context, id) { it.copy(status = TaskStatus.Failed, error = e.message) }; checkPending(context) }
        }
    }

    fun pause(context: Context, id: String) { segmenter.cancelTask(id); jobs[id]?.cancel(); jobs.remove(id); updateTaskState(context, id) { it.copy(status = TaskStatus.Paused, speed = 0) } }
    fun remove(context: Context, id: String) { segmenter.cancelTask(id); jobs[id]?.cancel(); jobs.remove(id); _tasks.value = _tasks.value.filterNot { it.id == id }; TaskStore.save(context, _tasks.value) }
    private fun updateTaskState(context: Context, id: String, block: (DownloadTask) -> DownloadTask) { _tasks.value = _tasks.value.map { if (it.id == id) block(it) else it }; TaskStore.save(context, _tasks.value) }
    private fun checkPending(context: Context) { if (_tasks.value.none { it.status == TaskStatus.Running || it.status == TaskStatus.Pending }) DownloadService.stop(context) else { _tasks.value.firstOrNull { it.status == TaskStatus.Pending }?.let { startTask(context, it.id) } } }
    
    private fun probeRealFileName(url: String, headers: Map<String, String>): String { return "file_${System.currentTimeMillis()}.bin" }
}
