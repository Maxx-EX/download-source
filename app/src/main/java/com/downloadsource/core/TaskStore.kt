package com.downloadsource.core
import android.content.Context
import com.downloadsource.data.AppDatabase
import com.downloadsource.data.TaskEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object TaskStore {
    private val gson = Gson()
    suspend fun loadAsync(context: Context): List<DownloadTask> = withContext(Dispatchers.IO) {
        val dao = AppDatabase.getDatabase(context).taskDao()
        val entities = dao.getAllTasks().first()
        entities.map { e -> DownloadTask(e.id, e.url, e.fileName, e.dirPath, e.threads, e.speedLimitKb, gson.fromJson(e.headersJson, object : TypeToken<Map<String, String>>() {}.type) ?: emptyMap(), e.totalBytes, e.downloadedBytes, try { TaskStatus.valueOf(e.status) } catch (ex: Exception) { TaskStatus.Pending }, e.error, e.outputPath, e.createdAt) }
    }
    fun save(context: Context, list: List<DownloadTask>) {
        val dao = AppDatabase.getDatabase(context).taskDao()
        val entities = list.map { t -> TaskEntity(t.id, t.url, t.fileName, t.dirPath, t.threads, t.speedLimitKb, gson.toJson(t.headers), t.totalBytes, t.downloadedBytes, t.status.name, t.error, t.outputPath, t.createdAt) }
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) { dao.insertTasks(entities) }
    }
}
