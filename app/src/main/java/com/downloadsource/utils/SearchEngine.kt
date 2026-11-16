package com.downloadsource.utils

import android.content.Context
import com.downloadsource.storage.BrowserPrefs

enum class SearchEngine(val displayName: String, val baseUrl: String) {
    BING("Bing", "https://www.bing.com/search?q="),
    BAIDU("Baidu", "https://www.baidu.com/s?wd="),
    GOOGLE("Google", "https://www.google.com/search?q=");

    companion object {
        fun getCurrent(ctx: Context): SearchEngine {
            val name = BrowserPrefs.getEngine(ctx)
            return values().firstOrNull { it.displayName == name } ?: BING
        }
    }
}
