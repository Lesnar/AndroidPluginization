plugins {
    alias(libs.plugins.android.application)
}

/**
 * 把 :stub 模块打出的插件 APK 复制为宿主 assets/stub.apk，运行时由 PluginManager 释放并加载。
 */
abstract class CopyStubApkTask : DefaultTask() {
    // 不用 @InputDirectory：目录要等 :stub:assembleDebug 执行后才存在，改由 inputs.files 声明（见下方注册处）
    @get:Internal
    abstract val stubApkDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val apk = stubApkDir.get().asFile.listFiles { f -> f.extension == "apk" }?.firstOrNull()
            ?: error("未找到 stub 的 APK：${stubApkDir.get().asFile}")
        apk.copyTo(outputDir.get().file("stub.apk").asFile, overwrite = true)
    }
}

android {
    namespace = "com.example.pluginization"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.pluginization"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

androidComponents {
    onVariants { variant ->
        val copyStub = tasks.register<CopyStubApkTask>("copyStubApkFor${variant.name.replaceFirstChar { it.uppercase() }}") {
            dependsOn(":stub:assembleDebug")
            val apkDir = project(":stub").layout.buildDirectory.dir("outputs/apk/debug")
            stubApkDir.set(apkDir)
            inputs.files(apkDir.map { fileTree(it) { include("*.apk") } })
        }
        variant.sources.assets?.addGeneratedSourceDirectory(copyStub, CopyStubApkTask::outputDir)
    }
}
