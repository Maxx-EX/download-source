package com.downloadsource.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

object DirStore {
    private const val PREF = "ds_pref"
    private const val KEY_URI = "download_tree_uri"

    fun save(context: Context, uri: Uri) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_URI, uri.toString()).apply()
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    fun get(context: Context): Uri? = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_URI, null)?.let { Uri.parse(it) }

    fun resolveDownloadDir(context: Context): File {
        val uri = get(context) ?: return defaultDir()
        val path = treeUriToPath(uri) ?: return defaultDir()
        val f = File(path)
        if (!f.exists()) f.mkdirs()
        return if (f.canWrite()) f else defaultDir()
    }

    // 默认使用公共 Download 目录，如果不行退回应用专属目录
    fun defaultDir(): File {
        val publicDownload = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (publicDownload.exists() && publicDownload.canWrite()) return publicDownload
        return File(Environment.getExternalStorageDirectory(), "DownloadSource")
    }

    private fun treeUriToPath(uri: Uri): String? = try {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val split = docId.split(":").toTypedArray()
        if (split.size >= 2) {
            val type = split[0]
            val path = split[1]
            if ("primary".equals(type, ignoreCase = true)) {
                Environment.getExternalStorageDirectory().absolutePath + "/" + path
            } else if (type.contains("storage")) {
                "/storage/$path"
            } else {
                "/storage/$type/$path"
            }
        } else null
    } catch (e: Exception) {
        // 回环降级：针对某些无法解析的 ROM，强行取一个系统路径
        null
    }
}
