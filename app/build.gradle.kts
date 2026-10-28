plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

import java.util.Properties
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

val releaseKeystore = rootProject.file("keystore/pulse-release.jks")
val hasReleaseSigning = releaseKeystore.exists()

// 构建时间取最新改动源码的时间戳：源码没变时值保持不变，
// 连续打包不会只因为时间戳变了就丢掉 Kotlin 的增量编译结果。
// 用 ValueSource 把源码目录声明成输入，开启配置缓存后改文件仍会重新取值，
// 不会像直接 walkTopDown 那样被缓存成旧值。
abstract class SourceTimestamp : ValueSource<Long, SourceTimestamp.Params> {
    interface Params : ValueSourceParameters {
        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        val sources: org.gradle.api.file.ConfigurableFileCollection
    }

    override fun obtain(): Long =
        parameters.sources.asFileTree.files.maxOfOrNull { it.lastModified() }
            ?: System.currentTimeMillis()
}

val buildTimeMillis = providers.of(SourceTimestamp::class.java) {
    parameters.sources.from(layout.projectDirectory.dir("src/main"))
}.get()

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
        versionCode = 39
        versionName = "0.2.1"
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
        // textmate 引擎用到 java.time / java.util.function 等 API，低版本系统需脱糖
        isCoreLibraryDesugaringEnabled = true
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
        // textmate 依赖的 eclipse jdt.annotation 与 paho 都带同名 bundle.properties，
        // 内容只是 OSGi 元数据，重复时直接丢弃
        resources.excludes += "bundle.properties"
    }
    testOptions {
        // sora-editor 的日志走 android.util.Log，JVM 单测里需要返回默认值才能跑通
        unitTests.isReturnDefaultValues = true
    }
    lint {
        // assembleRelease 默认附带 lintVital，每次多花几分钟；日常打包跳过，需要时单独跑 lint
        checkReleaseBuilds = false
    }
}

// Room 导出的 schema 随代码提交，升级时据此手写迁移
ksp {
    // KSP2 的 worker 进程工作目录与项目根不一致，这里显式给绝对路径
    arg("room.schemaLocation", layout.projectDirectory.dir("schemas").asFile.absolutePath)
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
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
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kaml)
    // YAML 节点树保留标量引号与类型标签，互转工具依赖它区分 "123" 与 123
    implementation(libs.snakeyaml.engine)
    // 二维码生成与识别：纯 Java 实现，不依赖 Play 服务
    implementation(libs.zxing.core)
    implementation(libs.kotlinx.coroutines.android)

    // 拼接预览的 Markdown 渲染
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)

    // 代码编辑器：TextMate 语法高亮（JSON/YAML/JavaScript）
    implementation(platform(libs.sora.editor.bom))
    implementation(libs.sora.editor)
    implementation(libs.sora.language.textmate)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
