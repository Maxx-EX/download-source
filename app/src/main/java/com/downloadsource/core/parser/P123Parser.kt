package com.downloadsource.core.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object P123Parser {
    private val client = OkHttpClient()

    suspend fun parse(url: String, pwd: String?): String = withContext(Dispatchers.IO) {
        val shareKey = Regex("""/(?:s|share)/([a-zA-Z0-9]+)""").find(url)?.groupValues?.get(1) 
            ?: error("无法获取 123 云盘 shareKey")

        val api = "https://www.123pan.com/api/share/download/info"
        val jsonBody = """{"shareKey":"$shareKey","pwd":"${pwd ?: ""}","isHome":0}"""
        val req = Request.Builder().url(api).post(jsonBody.toRequestBody("application/json".toMediaType())).build()
        val resp = client.newCall(req).execute()
        val json = JSONObject(resp.body?.string() ?: "")
        
        if (json.optInt("code") != 0) error("123云盘解析失败: ${json.optString("message")}")

        val data = json.getJSONObject("data")
        val fileId = data.optJSONArray("InfoList")?.optJSONObject(0)?.optString("FileId") ?: error("未找到文件")

        val dlBody = """{"shareKey":"$shareKey","fileId":$fileId,"pwd":"${pwd ?: ""}","isHome":0}"""
        val dlReq = Request.Builder().url(api).post(dlBody.toRequestBody("application/json".toMediaType())).build()
        val dlJson = JSONObject(client.newCall(dlReq).execute().body?.string() ?: "")
        
        dlJson.getJSONObject("data").optString("DownloadURL") ?: error("获取 123 云盘直链失败")
    }
}
