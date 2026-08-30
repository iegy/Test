plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.iegy.tajweed.fatiha"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.iegy.tajweed.fatiha"
        minSdk = 23
        targetSdk = 35
        versionCode = 7
        versionName = "2.2.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    androidResources {
        // The model is already INT8 and does not benefit materially from APK recompression.
        // Keeping it stored also avoids an additional decompression spike on first use.
        noCompress += listOf("ogg", "wav", "onnx")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Pinned intentionally for reproducible offline inference. MIT licensed.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.29.0")
}
