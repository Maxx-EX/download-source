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

    // 深度解析：支持单文件与多文件列表
    suspend fun parse(ctx: Context, url: String, pwd: String?): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val cookie = CookieStore.load(ctx, "quark") ?: error("请先登录夸克网盘获取 Cookie")
        val shareId = Regex("""/s/([a-zA-Z0-9]+)""").find(url)?.groupValues?.get(1) ?: error("无法识别夸克分享 ID")

        // 1. 获取 stoken
        val tokenApi = "https://drive-pc.quark.cn/1/clouddrive/share/sharepage/token?pr=ucpro&fr=pc"
        val tokenBody = """{"pwd_id":"$shareId","passcode":"${pwd ?: ""}"}"""
        val tokenReq = Request.Builder().url(tokenApi).post(tokenBody.toRequestBody("application/json".toMediaType())).header("Cookie", cookie).build()
        val tokenJson = JSONObject(client.newCall(tokenReq).execute().body?.string() ?: "")
        val stoken = tokenJson.optJSONObject("data")?.optString("stoken") ?: error("获取 Token 失败，Cookie 可能失效")

        // 2. 获取文件列表
        val detailApi = "https://drive-pc.quark.cn/1/clouddrive/share/sharepage/detail?pr=ucpro&fr=pc&pwd_id=$shareId&stoken=$stoken&pdir_fid=0&force=0&_page=1&_size=50"
        val detailReq = Request.Builder().url(detailApi).header("Cookie", cookie).build()
        val detailJson = JSONObject(client.newCall(detailReq).execute().body?.string() ?: "")
        val list = detailJson.optJSONObject("data")?.optJSONArray("list") ?: error("获取文件列表失败")
        
        if (list.length() == 0) error("分享为空")

        val results = mutableListOf<Pair<String, String>>()
        for (i in 0 until list.length()) {
            val file = list.getJSONObject(i)
            val fid = file.optString("fid")
            val fileName = file.optString("file_name")
            
            // 3. 获取下载直链
            val dlApi = "https://drive-pc.quark.cn/1/clouddrive/file/download?pr=ucpro&fr=pc"
            val dlBody = """{"fids":["$fid"]}"""
            val dlReq = Request.Builder().url(dlApi).post(dlBody.toRequestBody("application/json".toMediaType())).header("Cookie", cookie).build()
            val dlJson = JSONObject(client.newCall(dlReq).execute().body?.string() ?: "")
            val dlUrl = dlJson.optJSONArray("data")?.optJSONObject(0)?.optString("download_url")
            
            if (!dlUrl.isNullOrBlank()) {
                results.add(Pair(fileName, dlUrl))
            }
        }
        results
    }
}
