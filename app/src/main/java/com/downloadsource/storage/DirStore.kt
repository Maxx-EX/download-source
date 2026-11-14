package com.downloadsource.storage

import android.content.Context
import android.content.Intent
import android.net.Uri

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
}
