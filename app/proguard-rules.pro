# 修复 Compose 和 Lifecycle 在 R8 下的崩溃
-keep class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt { *; }
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.compose.**
