plugins {
    alias(libs.plugins.android.application)
}

val pleosSdk = providers.gradleProperty("pleos.sdk").map { it.toBoolean() }.getOrElse(false)

android {
    namespace = "ai.nunchi.mate"
    compileSdk {
        version = release(37)
    }
    useLibrary("android.car")

    defaultConfig {
        applicationId = "ai.nunchi.mate"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-poc"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    sourceSets {
        getByName("main") {
            kotlin.directories.add(if (pleosSdk) "src/pleos/kotlin" else "src/nopleos/kotlin")        }
    }

    androidResources {
        noCompress += listOf("bin", "ogg")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.onnxruntime.android)
    if (pleosSdk) {
        implementation(libs.pleos.navihelper)
        implementation(libs.pleos.vehicle)
        implementation(libs.pleos.tts)
        implementation(libs.pleos.stt)
    }
}
