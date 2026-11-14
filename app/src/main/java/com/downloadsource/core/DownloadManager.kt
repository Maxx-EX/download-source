package com.downloadsource.core

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLDecoder

object DownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val segmenter = Segmenter()
    private val jobs = mutableMapOf<String, Job>()
    private val httpClient = OkHttpClient()
    private const val MAX_CONCURRENT = 3 // 并发下载数

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    fun init(context: Context) {
        _tasks.value = TaskStore.load(context)
        NetworkMonitor.start(context)
    }

    fun add(context: Context, url: String, customName: String?, threads: Int, dir: File) {
        val tempName = if (!customName.isNullOrBlank()) customName else "parsing_${System.currentTimeMillis()}"
        val task = DownloadTask(url = url, fileName = tempName, dirPath = dir.absolutePath, threads = threads)
        _tasks.value = _tasks.value + task
        TaskStore.save(context, _tasks.value)
        
        scope.launch {
            val realName = if (customName.isNullOrBlank()) probeRealFileName(url) else customName
            update(context, task.id) { it.copy(fileName = realName) }
            startTask(context, task.id)
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
                val out = segmenter.download(t.url, dir, t.fileName, t.threads) { d, total, speed ->
                    update(context, id) { it.copy(downloadedBytes = d, totalBytes = total, speed = speed) }
                    DownloadService.updateProgress(context, t.fileName, d, total, speed)
                }
                update(context, id) { it.copy(status = TaskStatus.Completed, downloadedBytes = it.totalBytes, speed = 0, outputPath = out.absolutePath) }
                checkPending(context)
            } catch (e: CancellationException) {
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
        jobs[id]?.cancel(); jobs.remove(id)
        update(context, id) { it.copy(status = TaskStatus.Paused, speed = 0) }
        checkPending(context)
    }

    fun rename(context: Context, id: String, newName: String) {
        val t = _tasks.value.find { it.id == id } ?: return
        val dir = File(t.dirPath); val oldFile = File(dir, t.fileName); val newFile = File(dir, newName)
        if (oldFile.exists()) oldFile.renameTo(newFile)
        update(context, id) { it.copy(fileName = newName, outputPath = newFile.absolutePath) }
    }

    fun remove(context: Context, id: String) {
        jobs[id]?.cancel(); jobs.remove(id)
        _tasks.value = _tasks.value.filterNot { it.id == id }
        TaskStore.save(context, _tasks.value)
        checkPending(context)
    }

    private fun update(context: Context, id: String, block: (DownloadTask) -> DownloadTask) {
        _tasks.value = _tasks.value.map { if (it.id == id) block(it) else it }
        TaskStore.save(context, _tasks.value)
    }

    private fun probeRealFileName(url: String): String {
        try {
            // 如果是 m3u8 或 ts，直接命名
            if (url.contains(".m3u8") || url.endsWith(".ts", true)) return "video_${System.currentTimeMillis()}.mp4"
            
            val req = Request.Builder().url(url).head().build()
            httpClient.newCall(req).execute().use { resp ->
                val disposition = resp.header("Content-Disposition")
                if (disposition != null && disposition.contains("filename=")) {
                    var name = disposition.substringAfter("filename=").trim('"', ' ')
                    if (name.contains("''")) name = name.substringAfter("''")
                    return URLDecoder.decode(name, "UTF-8")
                }
                val path = resp.request.url.encodedPath
                val last = path.substringAfterLast('/')
                if (last.isNotBlank() && last.contains('.')) return last
                val mime = resp.header("Content-Type") ?: ""
                val ext = when {
                    mime.contains("video/mp4") -> ".mp4"
                    mime.contains("audio/mpeg") -> ".mp3"
                    mime.contains("application/vnd.android.package-archive") -> ".apk"
                    else -> ".bin"
                }
                return "download_${System.currentTimeMillis()}$ext"
            }
        } catch (e: Exception) { return "file_${System.currentTimeMillis()}.bin" }
    }
}
