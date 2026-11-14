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
        val uri = get(context)
        val path = if (uri != null) treeUriToPath(uri) else null
        
        // 有自定义路径且可写则使用，否则用默认专属文件夹
        if (path != null) {
            val f = File(path)
            if (!f.exists()) f.mkdirs()
            if (f.canWrite()) return f
        }
        return defaultDir()
    }

    // 默认路径：直接放在内部存储根目录的 DownloadSource 文件夹
    fun defaultDir(): File {
        val f = File(Environment.getExternalStorageDirectory(), "DownloadSource")
        if (!f.exists()) f.mkdirs()
        return f
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
    } catch (e: Exception) { null }
}
