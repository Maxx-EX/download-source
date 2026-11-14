package com.downloadsource.core

import android.content.Context
import com.downloadsource.core.parser.*

object DiskParser {
    suspend fun parse(ctx: Context, url: String, pwd: String?): String {
        return when {
            url.contains("lanzou") || url.contains("lanpw") -> LanzouParser.parse(url, pwd)
            url.contains("123pan.com") -> P123Parser.parse(url, pwd)
            url.contains("pan.quark.cn") -> QuarkParser.parse(ctx, url, pwd)
            url.contains("cloud.189.cn") -> TianyiParser.parse(url, pwd)
            // 阿里云盘和迅雷云盘等暂时走公共 API 方案
            url.contains("aliyundrive.com") || url.contains("alipan.com") || url.contains("pan.xunlei.com") -> {
                val info = NetDiskParser.detect(url)
                val apiUrl = NetDiskParser.buildPublicApiUrl(info)
                // 简单通过公共接口获取，如果失败抛出异常
                val resp = okhttp3.OkHttpClient().newCall(okhttp3.Request.Builder().url(apiUrl).build()).execute()
                val jsonStr = resp.body?.string() ?: ""
                val json = com.google.gson.JsonParser.parseString(jsonStr).asJsonObject
                json.get("direct_link")?.asString ?: json.get("url")?.asString ?: error("公共接口解析失败，可能暂不支持")
            }
            else -> url // 普通直链直接返回
        }
    }
}
