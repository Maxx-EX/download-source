package com.downloadsource.core

import java.net.URLEncoder

object NetDiskParser {
    enum class DiskType(val key: String, val label: String) {
        BAIDU("baidu", "百度网盘"), QUARK("quark", "夸克网盘"),
        ALIYUN("aliyun", "阿里云盘"), LZ("lz", "蓝奏云"),
        UC("uc", "UC网盘"), XUNLEI("xunlei", "迅雷云盘"),
        TIANYI("tianyi", "天翼云盘"), UNKNOWN("", "通用直链")
    }

    data class ShareInfo(val type: DiskType, val url: String, val pwd: String?)

    // 自动识别链接和提取码
    fun detect(raw: String): ShareInfo {
        val text = raw.trim()
        val pwdRegex = Regex("""(?i)(?:提取码|密码|pwd|code)[\s:：=]*([a-zA-Z0-9]{4})""")
        val pwd = pwdRegex.find(text)?.groupValues?.get(1)
            ?: Regex("""(?i)[?&]pwd=([a-zA-Z0-9]{4})""").find(text)?.groupValues?.get(1)

        val type = when {
            text.contains("pan.baidu.com") || text.contains("eyun.baidu.com") -> DiskType.BAIDU
            text.contains("pan.quark.cn") || text.contains("drive-pc.quark.cn") -> DiskType.QUARK
            text.contains("aliyundrive.com") || text.contains("alipan.com") -> DiskType.ALIYUN
            text.contains("lanzou") || text.contains("lanpw") || text.contains("woozooo") -> DiskType.LZ
            text.contains("drive.uc.cn") || text.contains("fast.uc.cn") -> DiskType.UC
            text.contains("pan.xunlei.com") -> DiskType.XUNLEI
            text.contains("cloud.189.cn") -> DiskType.TIANYI
            else -> DiskType.UNKNOWN
        }
        val url = Regex("""https?://[^\s]+""").find(text)?.value ?: text
        return ShareInfo(type, url, pwd)
    }

    fun buildPublicApiUrl(info: ShareInfo, apiBase: String = "https://lz.qaiu.top"): String {
        val encoded = URLEncoder.encode(info.url, "UTF-8")
        return if (info.pwd != null) "$apiBase/json/parser?url=$encoded&pwd=${info.pwd}"
        else "$apiBase/json/parser?url=$encoded"
    }
}
