package com.downloadsource.core
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object Aria2Manager {
    private val client = OkHttpClient()
    fun getServer(ctx: Context): String = ctx.getSharedPreferences("aria2", Context.MODE_PRIVATE).getString("server", "") ?: ""
    fun setServer(ctx: Context, url: String) { ctx.getSharedPreferences("aria2", Context.MODE_PRIVATE).edit().putString("server", url).apply() }
    
    suspend fun addTask(ctx: Context, url: String): Boolean = withContext(Dispatchers.IO) {
        val server = getServer(ctx)
        if (server.isBlank()) return@withContext false
        val jsonBody = """{"jsonrpc":"2.0","method":"aria2.addUri","id":"ds","params":[["$url"]]}"""
        val req = Request.Builder().url(server).post(jsonBody.toRequestBody("application/json".toMediaType())).build()
        try { client.newCall(req).execute().use { resp -> resp.isSuccessful } } catch (e: Exception) { false }
    }
}
