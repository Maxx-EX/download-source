package com.downloadsource.core

import android.content.Context
import com.downloadsource.core.parser.LanzouParser
import com.downloadsource.core.parser.P123Parser
import com.downloadsource.core.parser.QuarkParser
import com.downloadsource.core.parser.TianyiParser

object DiskParser {
    suspend fun parse(ctx: Context, url: String, pwd: String?): String {
        return when {
            url.contains("lanzou") || url.contains("lanpw") -> LanzouParser.parse(url, pwd)
            url.contains("123pan.com") -> P123Parser.parse(url, pwd)
            url.contains("pan.quark.cn") -> QuarkParser.parse(ctx, url, pwd)
            url.contains("cloud.189.cn") -> TianyiParser.parse(url, pwd)
            else -> url
        }
    }
}
