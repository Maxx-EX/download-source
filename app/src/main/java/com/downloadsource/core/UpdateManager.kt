package com.downloadsource.core

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.downloadsource.BuildConfig
import com.downloadsource.ThemeStore
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object UpdateManager {
    private const val REPO = "Maxx-EX/download-source"
    private val client = OkHttpClient()

    data class ReleaseInfo(val version: String, val downloadUrl: String, val changelog: String)

    suspend fun checkUpdate(currentVersion: String): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://api.github.com/repos/$REPO/releases/latest").header("User-Agent", "DownloadSource").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val json = JsonParser.parseString(resp.body?.string()).asJsonObject
                val tagName = json.get("tag_name")?.asString?.replace("v", "")?.substringBefore("-") ?: return@withContext null
                
                // 判断逻辑：忽略已屏蔽版本
                if (isNewerVersion(tagName, currentVersion)) {
                    val body = json.get("body")?.asString ?: "暂无更新日志"
                    val assets = json.getAsJsonArray("assets")
                    // 根据版本通道选择对应的包
                    val isZeta = BuildConfig.CHANNEL == "zeta"
                    val targetName = if (isZeta) "Zeta" else "Omega"
                    
                    val apkUrl = assets.firstOrNull { it.asJsonObject.get("name").asString.contains(targetName, true) && it.asJsonObject.get("name").asString.endsWith(".apk") }
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

    fun showUpdateDialog(ctx: Context, info: ReleaseInfo) {
        if (ThemeStore.getIgnoreVersion(ctx) == info.version) return

        AlertDialog.Builder(ctx)
            .setTitle("发现新版本 v${info.version} (${BuildConfig.CHANNEL.uppercase()})")
            .setMessage(info.changelog)
            .setPositiveButton("立即更新") { _, _ -> openDownload(ctx, info.downloadUrl) }
            .setNegativeButton("稍后") { _, _ -> }
            .setNeutralButton("忽略此版本") { _, _ -> ThemeStore.setIgnoreVersion(ctx, info.version) }
            .setCancelable(false).show()
    }

    fun openDownload(ctx: Context, url: String) {
        if (url.isNotEmpty()) ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
