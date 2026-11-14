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

    suspend fun checkUpdate(currentVersionCode: Int = BuildConfig.VERSION_CODE): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://api.github.com/repos/$REPO/releases/latest")
                .header("User-Agent", "DownloadSource").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val json = JsonParser.parseString(resp.body?.string()).asJsonObject
                val tag = json.get("tag_name")?.asString ?: return@withContext null

                val rawTag = tag.removePrefix("v")
                val versionName = rawTag.substringBefore("-build")
                val remoteVersionCode = rawTag.substringAfter("-build", "-1").toIntOrNull() ?: 0

                if (remoteVersionCode > currentVersionCode) {
                    val body = json.get("body")?.asString ?: "暂无更新日志"
                    val assets = json.getAsJsonArray("assets")
                    val targetName = if (BuildConfig.CHANNEL == "zeta") "Zeta" else "Omega"
                    val apkUrl = assets.firstOrNull {
                        it.asJsonObject.get("name").asString.contains(targetName, true)
                                && it.asJsonObject.get("name").asString.endsWith(".apk")
                    }?.asJsonObject?.get("browser_download_url")?.asString ?: ""
                    ReleaseInfo(versionName, apkUrl, body)
                } else null
            }
        } catch (e: Exception) { null }
    }

    fun showUpdateDialog(ctx: Context, info: ReleaseInfo) {
        if (ThemeStore.getIgnoreVersion(ctx) == info.version) return
        AlertDialog.Builder(ctx)
            .setTitle("发现新版本 v${info.version}")
            .setMessage(info.changelog)
            .setPositiveButton("立即更新") { _, _ -> openDownload(ctx, info.downloadUrl) }
            .setNeutralButton("忽略此版本") { _, _ -> ThemeStore.setIgnoreVersion(ctx, info.version) }
            .setNegativeButton("稍后", null).show()
    }

    fun openDownload(ctx: Context, url: String) {
        if (url.isNotEmpty()) ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
