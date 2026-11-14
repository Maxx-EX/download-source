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
    // 全局单例 OkHttpClient，复用连接池
    val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val segmenter = Segmenter(httpClient)
    private val jobs = mutableMapOf<String, Job>()
    private val mutex = Mutex()
    private const val MAX_CONCURRENT = 3

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    fun init(context: Context) {
        _tasks.value = TaskStore.load(context)
        _tasks.value.forEach { if (it.status == TaskStatus.Running) update(context, it.id) { t -> t.copy(status = TaskStatus.Pending, speed = 0) } }
        NetworkMonitor.start(context)
    }

    fun autoResumeTasks(context: Context) {
        _tasks.value.filter { it.status == TaskStatus.Pending || it.status == TaskStatus.Paused }.forEach { startTask(context, it.id) }
    }

    fun add(context: Context, url: String, customName: String?, threads: Int, speedLimitKb: Long, headers: Map<String, String>, dir: File) {
        val tempName = if (!customName.isNullOrBlank()) customName else "parsing_${System.currentTimeMillis()}"
        val task = DownloadTask(url = url, fileName = tempName, dirPath = dir.absolutePath, threads = threads, speedLimitKb = speedLimitKb, headers = headers)
        scope.launch {
            mutex.withLock { _tasks.value = _tasks.value + task }
            TaskStore.save(context, _tasks.value)
            
            try {
                val shareInfo = NetDiskParser.detect(url)
                var parsedUrl = if (shareInfo.type != NetDiskParser.DiskType.UNKNOWN) {
                    if (BuildConfig.CHANNEL == "zeta") DiskParser.parse(context, shareInfo.url, shareInfo.pwd)
                    else throw Exception("网盘解析为内测功能，请下载 Zeta 版体验")
                } else url
                
                parsedUrl = MirrorResolver.resolveFastest(context, parsedUrl)
                val realName = if (customName.isNullOrBlank()) probeRealFileName(parsedUrl, headers) else customName
                
                update(context, task.id) { it.copy(url = parsedUrl, fileName = realName) }
                startTask(context, task.id)
            } catch (e: Exception) {
                update(context, task.id) { it.copy(status = TaskStatus.Failed, error = e.message ?: "解析失败") }
            }
        }
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
                
                // 加入系统媒体库
                MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), null, null)
                
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
    fun removeAllCompleted(context: Context) { _tasks.value.filter { it.status == TaskStatus.Completed }.forEach { remove(context, it.id) } }

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
                // 解析 RFC 5987 filename*
                val disposition = resp.header("Content-Disposition")
                if (disposition != null && disposition.contains("filename=")) {
                    var name = disposition.substringAfter("filename=").trim('"', ' ')
                    if (name.contains("''")) name = name.substringAfter("''")
                    return URLDecoder.decode(name, "UTF-8")
                }
                val path = resp.request.url.encodedPath
                val last = path.substringAfterLast('/')
                if (last.isNotBlank() && last.contains('.')) return last
            }
        } catch (_: Exception) { }
        return "download_${System.currentTimeMillis()}.bin"
    }
}
