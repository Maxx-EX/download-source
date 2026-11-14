package com.downloadsource.core.parser

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.regex.Pattern

object LanzouParser {
    private val client = OkHttpClient()
    private const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 10; SM-G981B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.162 Mobile Safari/537.36"

    fun parse(url: String, pwd: String?): String {
        // 1. 提取文件ID
        val fileId = Regex("""/([a-zA-Z0-9]+)(\?|$)""").find(url)?.groupValues?.get(1) ?: error("无法获取蓝奏云文件ID")
        val baseUrl = url.substringBefore("lanzou").plus("lanzou") + ".com"
        
        // 2. 模拟 POST 请求，带密码验证
        val postBody = "action=downprocess&sign=${fileId}&pwd=$pwd".toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val req = Request.Builder()
            .url("$baseUrl/ajaxm.php")
            .header("User-Agent", MOBILE_UA)
            .header("Referer", url)
            .post(postBody).build()

        val jsonStr = client.newCall(req).execute().use { it.body?.string() ?: "" }
        val json = JSONObject(jsonStr)
        
        if (json.optInt("zt") == 1) {
            return "${json.getString("dom")}/file/${json.getString("url")}"
        }
        // 如果直接 POST 失败，尝试抓取页面再解析
        error(json.optString("inf", "蓝奏云解析失败，提取码错误或链接失效"))
    }
}
