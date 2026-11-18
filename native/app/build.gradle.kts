plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.donggun.oxfordtodo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.donggun.oxfordtodo.native"
        minSdk = 26
        targetSdk = 36
        // 버전은 native/VERSION 파일 한 곳에서만 정한다 (예: 1.3). 앱 설정 화면과 APK 파일 이름에 같이 나온다.
        val ver = file("../VERSION").readText().trim()
        val vp = ver.split(".")
        versionCode = (vp.getOrNull(0)?.toIntOrNull() ?: 1) * 1000 + (vp.getOrNull(1)?.toIntOrNull() ?: 0)
        versionName = ver
    }
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.google.android.gms:play-services-auth:21.4.0")
}
