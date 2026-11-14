package com.downloadsource.core.parser

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.regex.Pattern

object LanzouParser {
    private val client = OkHttpClient()
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    fun parse(url: String, pwd: String?): String {
        try {
            // 尝试暴力构造请求 (很多接口不需要拿 sign 也能直接调)
            val fileId = Regex("""/([a-zA-Z0-9]+)(\?|$)""").find(url)?.groupValues?.get(1) ?: ""
            
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .build()
                
            client.newCall(req).execute().use { resp ->
                val html = resp.body?.string() ?: ""
                
                // 特征 1：如果网页里直接有直链
                val directMatch = Regex("""https://[^"'\s]+\.lanzou[a-z]\.com/[^"'\s]+""").find(html)
                if (directMatch != null) return directMatch.value

                // 特征 2：老接口提取
                val signMatch = Regex("""'sign'\s*:\s*'([^']+)'""").find(html)
                val webSignMatch = Regex("""'websignkey'\s*:\s*'([^']+)'""").find(html)
                
                if (signMatch != null && webSignMatch != null) {
                    val sign = signMatch.groupValues[1]
                    val webSignKey = webSignMatch.groupValues[1]
                    
                    val postBody = "action=downprocess&sign=$sign&ves=1&websignkey=$webSignKey&websign=&kd=1"
                        .toRequestBody("application/x-www-form-urlencoded".toMediaType())
                        
                    val ajaxReq = Request.Builder()
                        .url("https://developer.lanzouw.com/ajaxm.php")
                        .header("User-Agent", UA)
                        .header("Referer", url)
                        .post(postBody).build()
                        
                    client.newCall(ajaxReq).execute().use { ajaxResp ->
                        val json = JSONObject(ajaxResp.body?.string() ?: "")
                        if (json.optInt("zt") == 1) {
                            return "${json.getString("dom")}/file/${json.getString("url")}"
                        }
                    }
                }
            }
            throw Exception("蓝奏云 WAF 拦截，请尝试在内置浏览器打开该链接并手动触发下载")
        } catch (e: Exception) {
            throw Exception(e.message ?: "蓝奏云解析失败，可能链接已失效")
        }
    }
}
