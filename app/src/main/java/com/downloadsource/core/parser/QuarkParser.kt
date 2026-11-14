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

    suspend fun parse(ctx: Context, url: String, pwd: String?): String = withContext(Dispatchers.IO) {
        val cookie = CookieStore.load(ctx, "quark") ?: error("请先在浏览器登录夸克网盘以获取 Cookie")
        val shareId = Regex("""/s/([a-zA-Z0-9]+)""").find(url)?.groupValues?.get(1) ?: error("无法识别夸克分享 ID")

        val tokenApi = "https://drive-pc.quark.cn/1/clouddrive/share/sharepage/token?pr=ucpro&fr=pc"
        val tokenBody = """{"pwd_id":"$shareId","passcode":"${pwd ?: ""}"}"""
        val tokenReq = Request.Builder().url(tokenApi).post(tokenBody.toRequestBody("application/json".toMediaType())).header("Cookie", cookie).build()
        
        val tokenJson = JSONObject(client.newCall(tokenReq).execute().body?.string() ?: "")
        val stoken = tokenJson.optJSONObject("data")?.optString("stoken") ?: error("获取夸克 Token 失败，Cookie 可能已过期")

        val detailApi = "https://drive-pc.quark.cn/1/clouddrive/share/sharepage/detail?pr=ucpro&fr=pc&pwd_id=$shareId&stoken=$stoken&pdir_fid=0&force=0&_page=1&_size=50"
        val detailReq = Request.Builder().url(detailApi).header("Cookie", cookie).build()
        val detailJson = JSONObject(client.newCall(detailReq).execute().body?.string() ?: "")
        
        val list = detailJson.optJSONObject("data")?.optJSONArray("list") ?: error("获取文件列表失败")
        if (list.length() == 0) error("分享为空")
        val fid = list.getJSONObject(0).optString("fid")

        val dlApi = "https://drive-pc.quark.cn/1/clouddrive/file/download?pr=ucpro&fr=pc"
        val dlBody = """{"fids":["$fid"]}"""
        val dlReq = Request.Builder().url(dlApi).post(dlBody.toRequestBody("application/json".toMediaType())).header("Cookie", cookie).build()
        val dlJson = JSONObject(client.newCall(dlReq).execute().body?.string() ?: "")
        
        dlJson.optJSONArray("data")?.optJSONObject(0)?.optString("download_url") ?: error("获取夸克直链失败")
    }
}
