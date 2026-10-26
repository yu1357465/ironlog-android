plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.fitness_plan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.fitness_plan"
        minSdk = 26
        targetSdk = 35
        // 版本规则：versionCode = 主版本 × 10000 + 次版本 × 100 + 修订号（0.5.0 → 500），只增不减。
        // 1.0.0 留给第一个正式发布的版本。
        versionCode = 500
        versionName = "0.5.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 自用阶段：用本机 Android SDK 自带的调试签名，打包无需密码。
            // 换电脑后调试签名不同，需要先卸载手机上的旧版才能安装。
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            // 调试版与正式版并存，互不影响数据。
            // 注意：调试版的 Compose 未经优化，动画掉帧不代表正式版的表现。
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-DEV"
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
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.reorderable)

    // 二维码：生成/识别图片用 ZXing core；相机扫码用 zxing-android-embedded（不依赖 Google 服务）
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded) { isTransitive = false }

    testImplementation(libs.junit)
    // Android 自带的 org.json 在 JVM 单元测试中只是空壳
    testImplementation(libs.org.json)
}
