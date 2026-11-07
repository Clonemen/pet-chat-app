plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.petchat.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.petchat.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        // 与 Kotlin 1.9.24 匹配的 Compose 编译器
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")

    // 图片加载（宠物外观，data:image/svg+xml 与远程图都支持）
    //noinspection NewerVersionAvailable
    implementation("io.coil-kt:coil-compose:2.7.0")

    // 网络：REST + WebSocket
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON 序列化（与后端接口契约对应）
    //noinspection NewerVersionAvailable,NewerVersionAvailable,NewerVersionAvailable,NewerVersionAvailable,NewerVersionAvailable
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // 二维码生成（我的好友二维码）：纯 Java 核心库
    implementation("com.google.zxing:core:3.5.3")

    // 相机扫码：CameraX
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // ML Kit 条码识别（扫码解码，模型内置可离线）
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
}
