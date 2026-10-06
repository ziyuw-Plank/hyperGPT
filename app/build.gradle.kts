import java.util.Properties

plugins {
    id("com.android.application")
}

// 签名信息从本地 keystore.properties 读取（已加入 .gitignore，绝不提交到仓库）。
// 没有该文件时：debug 用 Android 默认 debug key，release 输出未签名 APK。
val ksProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasKs = ksProps.getProperty("storeFile")?.let { rootProject.file(it).exists() } == true

android {
    namespace = "io.github.zyw.powergpt"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.zyw.powergpt"
        minSdk = 34          // Android 14（HyperOS 1.x）起
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (hasKs) {
            create("self") {
                storeFile = rootProject.file(ksProps.getProperty("storeFile"))
                storePassword = ksProps.getProperty("storePassword")
                keyAlias = ksProps.getProperty("keyAlias")
                keyPassword = ksProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (hasKs) signingConfig = signingConfigs.getByName("self")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = false   // 保留 xposed_scope 等仅被 meta-data 引用的资源
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasKs) signingConfig = signingConfigs.getByName("self")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "META-INF/**"
    }
}

dependencies {
    // Xposed API 只参与编译，运行时由 LSPosed 提供；绝不能打包进 APK
    compileOnly("de.robv.android.xposed:api:82")
}
