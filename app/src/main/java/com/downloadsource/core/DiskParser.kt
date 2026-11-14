package com.downloadsource.core

import android.content.Context
import com.downloadsource.core.parser.*

object DiskParser {
    // 修复：统一返回 List<Pair<文件名, 下载链接>>
    suspend fun parse(ctx: Context, url: String, pwd: String?): List<Pair<String, String>> {
        return when {
            url.contains("lanzou") || url.contains("lanpw") -> listOf(Pair("lanzou_file_${System.currentTimeMillis()}.zip", LanzouParser.parse(url, pwd)))
            url.contains("123pan.com") -> listOf(Pair("123pan_file_${System.currentTimeMillis()}.zip", P123Parser.parse(url, pwd)))
            url.contains("pan.quark.cn") -> QuarkParser.parse(ctx, url, pwd) // 夸克返回列表
            url.contains("cloud.189.cn") -> listOf(Pair("tianyi_file_${System.currentTimeMillis()}.zip", TianyiParser.parse(url, pwd)))
            url.contains("baidu.com") -> BaiduParser.parse(ctx, url, pwd)
            else -> listOf(Pair("download_${System.currentTimeMillis()}.bin", url))
        }
    }
}
