plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.downloadsource"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.downloadsource"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17 -O3 -flto"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    flavorDimensions += "channel"
    productFlavors {
        create("zeta") {
            dimension = "channel"
            applicationIdSuffix = ".zeta"        // com.downloadsource.zeta
            versionNameSuffix = "-zeta"
            resValue("string", "app_name", "Download source Zeta")
            buildConfigField("String", "CHANNEL", "\"zeta\"")
        }
        create("omega") {
            dimension = "channel"
            applicationIdSuffix = ".omega"       // com.downloadsource.omega
            versionNameSuffix = "-omega"
            resValue("string", "app_name", "Download source Omega")
            buildConfigField("String", "CHANNEL", "\"omega\"")
        }
    }

    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
    packaging { jniLibs { useLegacyPackaging = false } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
