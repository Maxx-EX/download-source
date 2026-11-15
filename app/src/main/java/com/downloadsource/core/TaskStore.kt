package com.downloadsource.core

import android.content.Context
import com.downloadsource.data.AppDatabase
import com.downloadsource.data.TaskEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TaskStore {
    private val gson = Gson()

    suspend fun loadAsync(context: Context): List<DownloadTask> = withContext(Dispatchers.IO) {
        val dao = AppDatabase.getDatabase(context).taskDao()
        // 注意：此处是同步读取，实际项目中可以通过 Flow 收集，为了兼容老代码暂用此写法
        val entities = mutableListOf<TaskEntity>()
        kotlinx.coroutines.runBlocking { 
            dao.getAllTasks().collect { list -> entities.addAll(list) } 
        }
        entities.map { e ->
            DownloadTask(
                id = e.id, url = e.url, fileName = e.fileName, dirPath = e.dirPath,
                threads = e.threads, speedLimitKb = e.speedLimitKb,
                headers = gson.fromJson(e.headersJson, object : TypeToken<Map<String, String>>() {}.type) ?: emptyMap(),
                totalBytes = e.totalBytes, downloadedBytes = e.downloadedBytes,
                status = try { TaskStatus.valueOf(e.status) } catch (ex: Exception) { TaskStatus.Pending },
                error = e.error, outputPath = e.outputPath, createdAt = e.createdAt
            )
        }
    }

    fun save(context: Context, list: List<DownloadTask>) {
        val dao = AppDatabase.getDatabase(context).taskDao()
        val entities = list.map { t ->
            TaskEntity(
                id = t.id, url = t.url, fileName = t.fileName, dirPath = t.dirPath,
                threads = t.threads, speedLimitKb = t.speedLimitKb,
                headersJson = gson.toJson(t.headers),
                totalBytes = t.totalBytes, downloadedBytes = t.downloadedBytes,
                status = t.status.name, error = t.error, outputPath = t.outputPath, createdAt = t.createdAt
            )
        }
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            dao.insertTasks(entities)
        }
    }
}
