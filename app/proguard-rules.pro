# 修复 Compose 和 Lifecycle 在 R8 下的崩溃
-keep class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt { *; }
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.compose.**

# 保留 Room 的实体类和 Dao
-keep class com.downloadsource.data.** { *; }
-keepclassmembers class * {
    @androidx.room.* <fields>;
}

# 保留 Media3 播放器
-keep class androidx.media3.** { *; }

# 保留 WebView JS 桥接
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.downloadsource.web.WebInterceptor { *; }
