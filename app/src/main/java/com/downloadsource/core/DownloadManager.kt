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

    fun add(context: Context, url: String, dir: File) {
        val name = guessName(url)
        val task = DownloadTask(url = url, fileName = name)
        _tasks.value = _tasks.value + task
        start(context, task.id, dir)
    }

    fun start(context: Context, id: String, dir: File) {
        val t = _tasks.value.find { it.id == id } ?: return
        if (t.status == TaskStatus.Running) return
        update(id) { it.copy(status = TaskStatus.Running, error = null) }

        jobs[id] = scope.launch {
            try {
                val out = segmenter.download(t.url, dir, t.fileName) { d, total, speed ->
                    update(id) { it.copy(downloadedBytes = d, totalBytes = total, speed = speed) }
                }
                update(id) {
                    it.copy(
                        status = TaskStatus.Completed,
                        downloadedBytes = it.totalBytes,
                        speed = 0,
                        outputPath = out.absolutePath
                    )
                }
            } catch (e: CancellationException) {
                update(id) { it.copy(status = TaskStatus.Paused, speed = 0) }
                throw e
            } catch (e: Exception) {
                update(id) { it.copy(status = TaskStatus.Failed, error = e.message ?: "unknown", speed = 0) }
            }
        }
    }

    fun pause(id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
    }

    fun remove(id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
        _tasks.value = _tasks.value.filterNot { it.id == id }
    }

    private fun update(id: String, block: (DownloadTask) -> DownloadTask) {
        _tasks.value = _tasks.value.map { if (it.id == id) block(it) else it }
    }

    private fun guessName(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val last = path.substringAfterLast('/').ifBlank { "download_${System.currentTimeMillis()}" }
        return if (last.contains('.')) last else "$last.bin"
    }
}
