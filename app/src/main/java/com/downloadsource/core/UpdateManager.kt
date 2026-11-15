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

    data class ReleaseInfo(val version: String, val downloadUrl: String, val changelog: String, val isZeta: Boolean)

    suspend fun checkUpdate(currentVersionCode: Int): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$REPO/releases?per_page=100")
                .header("User-Agent", "DownloadSource")
                .build()
                
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val jsonArray = JsonParser.parseString(resp.body?.string()).asJsonArray
                val isZetaChannel = BuildConfig.CHANNEL == "zeta"
                val targetName = if (isZetaChannel) "Zeta" else "Omega"

                var bestRelease: ReleaseInfo? = null
                var maxVersionCode = currentVersionCode // 只有比本地大才需要更新

                for (element in jsonArray) {
                    val json = element.asJsonObject
                    if (json.get("draft")?.asBoolean == true || json.get("prerelease")?.asBoolean == true) continue

                    val tag = json.get("tag_name")?.asString ?: continue
                    val body = json.get("body")?.asString ?: "暂无更新日志"
                    val assets = json.getAsJsonArray("assets")
                    
                    val rawTag = tag.removePrefix("v")
                    val versionName = rawTag.substringBefore("-build")
                    // 兼容老版本的 build 号解析
                    val remoteVersionCode = rawTag.substringAfter("-build", "-1").toIntOrNull() ?: 0

                    val apkUrl = assets.firstOrNull { 
                        val name = it.asJsonObject.get("name").asString
                        name.contains(targetName, true) && name.endsWith(".apk") 
                    }?.asJsonObject?.get("browser_download_url")?.asString

                    // 核心修复：寻找真正的最大版本，不受发布顺序干扰
                    if (!apkUrl.isNullOrBlank() && remoteVersionCode > maxVersionCode) {
                        maxVersionCode = remoteVersionCode
                        bestRelease = ReleaseInfo(versionName, apkUrl, body, isZetaChannel)
                    }
                }
                return@withContext bestRelease
            }
        } catch (e: Exception) { 
            null 
        }
    }

    fun showUpdateDialog(ctx: Context, info: ReleaseInfo) {
        if (ThemeStore.getIgnoreVersion(ctx) == info.version) return
        val channelText = if (info.isZeta) "内测版 (Zeta)" else "正式版 (Omega)"
        val title = "发现新$channelText v${info.version} 🎉"
        val content = "【当前通道：$channelText】\n\n${info.changelog}"

        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setMessage(content)
            .setPositiveButton("立即下载") { _, _ -> openDownload(ctx, info.downloadUrl) }
            .setNeutralButton("忽略此版本") { _, _ -> ThemeStore.setIgnoreVersion(ctx, info.version) }
            .setNegativeButton("稍后", null).show()
    }

    fun openDownload(ctx: Context, url: String) {
        if (url.isNotEmpty()) ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
