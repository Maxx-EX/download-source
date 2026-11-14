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
        val req = Request.Builder().url(url).header("User-Agent", MOBILE_UA).build()
        val html = client.newCall(req).execute().use { it.body?.string() ?: error("获取蓝奏云页面失败") }

        var realUrl = url
        val iframeMatcher = Pattern.compile("<iframe[^>]*src=\"([^\"]+)\"").matcher(html)
        if (iframeMatcher.find()) {
            realUrl = iframeMatcher.group(1)!!
            if (realUrl.startsWith("/")) realUrl = "https://developer.lanzouw.com$realUrl"
        }

        val req2 = Request.Builder().url(realUrl).header("User-Agent", MOBILE_UA).build()
        val html2 = client.newCall(req2).execute().use { it.body?.string() ?: error("获取蓝奏云真实页面失败") }

        val signMatcher = Pattern.compile("'sign':'([^']+)'").matcher(html2)
        val webSignKeyMatcher = Pattern.compile("'websignkey':'([^']+)'").matcher(html2)
        
        if (!signMatcher.find() || !webSignKeyMatcher.find()) {
            error("未找到蓝奏云下载参数，可能需要密码或链接已失效")
        }

        val sign = signMatcher.group(1)!!
        val webSignKey = webSignKeyMatcher.group(1)!!

        val body = "action=downprocess&sign=$sign&ves=1&websignkey=$webSignKey&websign=&kd=1"
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
        
        val req3 = Request.Builder()
            .url("https://developer.lanzouw.com/ajaxm.php")
            .header("User-Agent", MOBILE_UA)
            .header("Referer", realUrl)
            .post(body).build()

        val jsonStr = client.newCall(req3).execute().use { it.body?.string() ?: "" }
        val json = JSONObject(jsonStr)
        
        if (json.optInt("zt") == 1) {
            return "${json.getString("dom")}/file/${json.getString("url")}"
        } else {
            error(json.optString("inf", "蓝奏云解析失败"))
        }
    }
}
