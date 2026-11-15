package com.downloadsource.core.parser

import android.content.Context
import com.downloadsource.storage.CookieStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object QuarkParser {
    private val client = OkHttpClient()

    suspend fun parse(ctx: Context, url: String, pwd: String?): List<DiskParser.FileInfo> = withContext(Dispatchers.IO) {
        val cookie = CookieStore.load(ctx, "quark") ?: error("请先在浏览器登录夸克网盘")
        val shareId = Regex("""/s/([a-zA-Z0-9]+)""").find(url)?.groupValues?.get(1) ?: error("链接识别失败")

        // 1. 获取 stoken
        val tokenApi = "https://drive-pc.quark.cn/1/clouddrive/share/sharepage/token?pr=ucpro&fr=pc"
        val tokenBody = """{"pwd_id":"$shareId","passcode":"${pwd ?: ""}"}"""
        val tokenReq = Request.Builder().url(tokenApi).post(tokenBody.toRequestBody("application/json".toMediaType())).header("Cookie", cookie).build()
        val tokenJson = JSONObject(client.newCall(tokenReq).execute().body?.string() ?: "")
        val stoken = tokenJson.optJSONObject("data")?.optString("stoken") ?: error("获取Token失败，Cookie失效")

        // 2. 递归获取文件树
        val results = mutableListOf<DiskParser.FileInfo>()
        fetchFolderRecursive(cookie, shareId, stoken, "0", results)
        results
    }

    private fun fetchFolderRecursive(cookie: String, shareId: String, stoken: String, pdirFid: String, results: MutableList<DiskParser.FileInfo>) {
        val detailApi = "https://drive-pc.quark.cn/1/clouddrive/share/sharepage/detail?pr=ucpro&fr=pc&pwd_id=$shareId&stoken=$stoken&pdir_fid=$pdirFid&force=0&_page=1&_size=50"
        val detailReq = Request.Builder().url(detailApi).header("Cookie", cookie).build()
        val detailJson = JSONObject(client.newCall(detailReq).execute().body?.string() ?: "")
        val list = detailJson.optJSONObject("data")?.optJSONArray("list") ?: return
        
        for (i in 0 until list.length()) {
            val file = list.getJSONObject(i)
            val fid = file.optString("fid")
            val name = file.optString("file_name")
            val isDir = file.optBoolean("dir")
            val size = file.optLong("size", 0L)

            if (isDir) {
                fetchFolderRecursive(cookie, shareId, stoken, fid, results)
            } else {
                val dlApi = "https://drive-pc.quark.cn/1/clouddrive/file/download?pr=ucpro&fr=pc"
                val dlBody = """{"fids":["$fid"]}"""
                val dlReq = Request.Builder().url(dlApi).post(dlBody.toRequestBody("application/json".toMediaType())).header("Cookie", cookie).build()
                val dlJson = JSONObject(client.newCall(dlReq).execute().body?.string() ?: "")
                val dlUrl = dlJson.optJSONArray("data")?.optJSONObject(0)?.optString("download_url")
                if (!dlUrl.isNullOrBlank()) {
                    results.add(DiskParser.FileInfo(name, dlUrl, size))
                }
            }
        }
    }
}
