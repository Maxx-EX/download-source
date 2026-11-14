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
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_URI, uri.toString()).apply()
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    fun get(context: Context): Uri? =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_URI, null)?.let { Uri.parse(it) }

    /** 解析下载目录，SAF 失败时退回应用私有外部目录 */
    fun resolveDownloadDir(context: Context): File {
        val uri = get(context) ?: return defaultDir(context)
        val path = treeUriToPath(uri) ?: return defaultDir(context)
        val f = File(path)
        if (!f.exists()) f.mkdirs()
        return if (f.canWrite()) f else defaultDir(context)
    }

    fun defaultDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val f = File(base, "Download")
        if (!f.exists()) f.mkdirs()
        return f
    }

    private fun treeUriToPath(uri: Uri): String? = try {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(":", limit = 2)
        if (parts.size == 2) {
            val base = when (parts[0]) {
                "primary", "home" -> Environment.getExternalStorageDirectory().absolutePath
                else -> null
            }
            if (base != null) {
                val rel = parts[1]
                if (rel.isEmpty()) base else "$base/$rel"
            } else null
        } else null
    } catch (_: Exception) {
        null
    }
}
