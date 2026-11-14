package com.downloadsource.storage

import android.content.Context
import android.os.Environment
import java.io.File

object DirStore {
    private const val PREF = "ds_pref"
    private const val KEY_PATH = "download_path"

    // 使用App专属外部存储，完美兼容Android 10+且不需要繁琐的SAF授权
    // 用户可通过系统文件管理器在 /Android/data/包名/files/Download 找到
    fun resolveDownloadDir(context: Context): File {
        val savedPath = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_PATH, null)
        if (savedPath != null) {
            val f = File(savedPath)
            if (f.exists() && f.canWrite()) return f
        }
        return defaultDir(context)
    }

    fun setCustomPath(context: Context, path: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_PATH, path).apply()
    }

    fun defaultDir(context: Context): File {
        val f = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "DownloadSource")
        if (!f.exists()) f.mkdirs()
        return f
    }
}
