plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

import java.util.Properties

val releaseKeystore = rootProject.file("keystore/pulse-release.jks")
val hasReleaseSigning = releaseKeystore.exists()

// 构建时间取最新改动源码的时间戳：源码没变时值保持不变，
// 连续打包不会只因为时间戳变了就丢掉 Kotlin 的增量编译结果。
// 这里只注入毫秒值，交给界面用设备本地时区渲染，避免打包机时区污染显示
val buildTimeMillis = rootProject.file("app/src/main").walkTopDown()
    .filter { it.isFile }
    .maxOfOrNull { it.lastModified() }
        ?: System.currentTimeMillis()

val signingProps = Properties().apply {
    val file = rootProject.file("keystore/keystore.properties")
    if (file.exists()) load(file.inputStream())
}

android {
    namespace = "com.evan.pulse"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.evan.pulse"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 36
        versionName = "0.1.36"
        buildConfigField("long", "BUILD_TIME_MS", "${buildTimeMillis}L")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = signingProps.getProperty("PULSE_RELEASE_STORE_PASSWORD") ?: ""
                keyAlias = signingProps.getProperty("PULSE_RELEASE_KEY_ALIAS") ?: "pulse"
                keyPassword = signingProps.getProperty("PULSE_RELEASE_KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            optimization {
                enable = true
            }
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.miuix)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.nav)
    implementation(libs.miuix.squircle)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)
    implementation(libs.paho.mqtt)

    // 前置/后置脚本的解释器
    implementation(libs.rhino)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kaml)
    implementation(libs.kotlinx.coroutines.android)

    // 拼接预览的 Markdown 渲染
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
