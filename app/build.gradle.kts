plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

fun loadDotEnv(file: java.io.File): Map<String, String> {
    if (!file.exists()) return emptyMap()
    return file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
        .associate { line ->
            val idx = line.indexOf('=')
            val key = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim().trim('"')
            key to value
        }
}

fun escapeBuildConfigString(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "")

val rootEnv = loadDotEnv(rootProject.file(".env"))

android {
    namespace = "com.chiptrack.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.chiptrack.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"
    }

    buildTypes {
        debug {
            // 调试包从仓库根目录 .env 注入（文件已 gitignore）
            buildConfigField(
                "String",
                "LLM_BASE_URL",
                "\"${escapeBuildConfigString(rootEnv["LLM_BASE_URL"].orEmpty())}\""
            )
            buildConfigField(
                "String",
                "LLM_API_KEY",
                "\"${escapeBuildConfigString(rootEnv["LLM_API_KEY"].orEmpty())}\""
            )
            buildConfigField(
                "String",
                "LLM_MODEL",
                "\"${escapeBuildConfigString(rootEnv["LLM_MODEL"].orEmpty())}\""
            )
        }
        release {
            isMinifyEnabled = false
            // 首发 GitHub Release 暂用 debug 签名，便于直接安装；正式上架前再换正式 keystore
            signingConfig = signingConfigs.getByName("debug")
            // 公开发布包不带 API Key
            buildConfigField("String", "LLM_BASE_URL", "\"\"")
            buildConfigField("String", "LLM_API_KEY", "\"\"")
            buildConfigField("String", "LLM_MODEL", "\"\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true
        buildConfig = true
    }
}

// 本地预制名单不进 release 包（gitignore 只管仓库，不管 APK）
tasks.matching { it.name == "mergeReleaseAssets" }.configureEach {
    doLast {
        val preset = outputs.files.singleFile.resolve("players.preset.json")
        if (preset.exists() && preset.delete()) {
            logger.lifecycle("Excluded players.preset.json from release assets")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.5")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
