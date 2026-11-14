package com.downloadsource.core

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object TaskStore {
    private const val PREF = "task_store"
    private const val KEY = "tasks_json"
    private val gson = Gson()

    fun load(context: Context): List<DownloadTask> {
        val s = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<DownloadTask>>() {}.type
            gson.fromJson<List<DownloadTask>>(s, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, list: List<DownloadTask>) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, gson.toJson(list)).apply()
    }
}
