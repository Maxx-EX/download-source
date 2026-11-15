package com.downloadsource.core

import android.content.Context
import com.downloadsource.data.AppDatabase
import com.downloadsource.data.TaskEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object TaskStore {
    private val gson = Gson()

    suspend fun loadAsync(context: Context): List<DownloadTask> = withContext(Dispatchers.IO) {
        val dao = AppDatabase.getDatabase(context).taskDao()
        // 修复：使用 first() 获取当前数据库快照，防止 runBlocking 无限挂起死锁
        val entities = dao.getAllTasks().first()
        
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

    // 修复：异步落库，防止主线程卡顿
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
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            dao.insertTasks(entities)
        }
    }
}
