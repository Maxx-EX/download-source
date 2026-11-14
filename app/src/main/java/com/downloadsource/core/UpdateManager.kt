package com.downloadsource.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object UpdateManager {
    // 修复：指向真实仓库
    private const val REPO = "Maxx-EX/download-source" 
    private val client = OkHttpClient()

    data class ReleaseInfo(val version: String, val downloadUrl: String, val changelog: String)

    suspend fun checkUpdate(currentVersion: String): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://api.github.com/repos/$REPO/releases/latest").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val json = JsonParser.parseString(resp.body?.string()).asJsonObject
                val tagName = json.get("tag_name").asString.replace("v", "").substringBefore("-")
                
                if (isNewerVersion(tagName, currentVersion)) {
                    val body = json.get("body")?.asString ?: ""
                    val assets = json.getAsJsonArray("assets")
                    val apkUrl = assets.firstOrNull { it.asJsonObject.get("name").asString.endsWith(".apk") }
                        ?.asJsonObject?.get("browser_download_url")?.asString ?: ""
                    ReleaseInfo(tagName, apkUrl, body)
                } else null
            }
        } catch (e: Exception) { null }
    }

    private fun isNewerVersion(new: String, old: String): Boolean {
        return try {
            val n = new.split(".").map { it.toIntOrNull() ?: 0 }
            val o = old.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(n.size, o.size)) {
                val nv = n.getOrElse(i) { 0 }
                val ov = o.getOrElse(i) { 0 }
                if (nv > ov) return true
                if (nv < ov) return false
            }
            false
        } catch (e: Exception) { false }
    }

    fun openDownload(ctx: Context, url: String) {
        if (url.isNotEmpty()) {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
