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
            // 拉取最近的发布列表，而不是只拿 latest，防止被草稿或旧版干扰
            val req = Request.Builder()
                .url("https://api.github.com/repos/$REPO/releases")
                .header("User-Agent", "DownloadSource")
                .build()
                
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val jsonArray = JsonParser.parseString(resp.body?.string()).asJsonArray
                val isZetaChannel = BuildConfig.CHANNEL == "zeta"
                val targetName = if (isZetaChannel) "Zeta" else "Omega"

                // 遍历所有 Release
                for (element in jsonArray) {
                    val json = element.asJsonObject
                    // 忽略草稿和预发布（如果你想包含预发布，可以去掉 draft/prerelease 判断）
                    if (json.get("draft")?.asBoolean == true || json.get("prerelease")?.asBoolean == true) continue

                    val tag = json.get("tag_name")?.asString ?: continue
                    val body = json.get("body")?.asString ?: "暂无更新日志"
                    val assets = json.getAsJsonArray("assets")
                    
                    // 1. 解析版本号 (兼容 v4.5.0-build2 格式)
                    val rawTag = tag.removePrefix("v")
                    val versionName = rawTag.substringBefore("-build")
                    val remoteVersionCode = rawTag.substringAfter("-build", "-1").toIntOrNull() ?: 0

                    // 2. 查找当前通道对应的 APK
                    val apkUrl = assets.firstOrNull { 
                        val name = it.asJsonObject.get("name").asString
                        name.contains(targetName, true) && name.endsWith(".apk") 
                    }?.asJsonObject?.get("browser_download_url")?.asString

                    // 3. 如果找到了对应的 APK，并且云端版本号大于本地，或者版本名不一致
                    if (!apkUrl.isNullOrBlank() && remoteVersionCode > currentVersionCode) {
                        return@withContext ReleaseInfo(versionName, apkUrl, body, isZetaChannel)
                    }
                }
                null
            }
        } catch (e: Exception) { 
            null 
        }
    }

    fun showUpdateDialog(ctx: Context, info: ReleaseInfo) {
        if (ThemeStore.getIgnoreVersion(ctx) == info.version) return
        
        val channelText = if (info.isZeta) "内测版 (Zeta)" else "正式版 (Omega)"
        val title = "发现新$channelText v${info.version} 🎉"
        
        // 针对不同版本，可以在公告前加个提示
        val content = "【当前通道：$channelText】\n\n${info.changelog}"

        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setMessage(content)
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
