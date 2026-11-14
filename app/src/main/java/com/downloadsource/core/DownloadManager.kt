package com.downloadsource.core

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object DownloadManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val segmenter = Segmenter()
    private val jobs = mutableMapOf<String, Job>()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    fun init(context: Context) {
        _tasks.value = TaskStore.load(context)
    }

    fun add(context: Context, url: String, customName: String?, threads: Int, dir: File) {
        val name = if (!customName.isNullOrBlank()) customName else guessName(url)
        val task = DownloadTask(url = url, fileName = name, dirPath = dir.absolutePath, threads = threads)
        _tasks.value = _tasks.value + task
        TaskStore.save(context, _tasks.value)
        DownloadService.start(context)
        start(context, task.id)
    }

    fun start(context: Context, id: String) {
        val t = _tasks.value.find { it.id == id } ?: return
        if (t.status == TaskStatus.Running) return
        update(context, id) { it.copy(status = TaskStatus.Running, error = null) }
        DownloadService.start(context)

        jobs[id] = scope.launch {
            try {
                val dir = File(t.dirPath)
                if (!dir.exists()) dir.mkdirs()
                val out = segmenter.download(t.url, dir, t.fileName, parts = t.threads) { d, total, speed ->
                    update(context, id) { it.copy(downloadedBytes = d, totalBytes = total, speed = speed) }
                }
                update(context, id) {
                    it.copy(status = TaskStatus.Completed, downloadedBytes = it.totalBytes, speed = 0, outputPath = out.absolutePath)
                }
                if (_tasks.value.none { it.status == TaskStatus.Running }) DownloadService.stop(context)
            } catch (e: CancellationException) {
                update(context, id) { it.copy(status = TaskStatus.Paused, speed = 0) }
                throw e
            } catch (e: Exception) {
                update(context, id) { it.copy(status = TaskStatus.Failed, error = e.message ?: "未知错误", speed = 0) }
                if (_tasks.value.none { it.status == TaskStatus.Running }) DownloadService.stop(context)
            }
        }
    }

    fun pause(context: Context, id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
        if (_tasks.value.none { it.status == TaskStatus.Running }) DownloadService.stop(context)
    }

    fun remove(context: Context, id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
        _tasks.value = _tasks.value.filterNot { it.id == id }
        TaskStore.save(context, _tasks.value)
    }

    private fun update(context: Context, id: String, block: (DownloadTask) -> DownloadTask) {
        _tasks.value = _tasks.value.map { if (it.id == id) block(it) else it }
        TaskStore.save(context, _tasks.value)
    }

    private fun guessName(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val last = path.substringAfterLast('/').ifBlank { "download_${System.currentTimeMillis()}" }
        return if (last.contains('.')) last else "$last.bin"
    }
}
