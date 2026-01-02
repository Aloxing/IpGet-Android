plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ipget.aloxing"
    // 注意：release(36) 并非标准写法，通常直接使用数字
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ipget.aloxing"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 【优化 1】限制资源语言，只保留中英文，可以减少几十 KB 的第三方库翻译资源
        resourceConfigurations += listOf("en", "zh")
    }

    buildTypes {
        release {
            // 【优化 2】开启 R8 混淆，这是压缩体积的核心，通常能减少 50% 以上的大小
            isMinifyEnabled = true
            // 【优化 3】开启资源压缩，移除未使用的图片或 XML
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // 【优化 4】恢复使用 BOM 来管理版本，确保所有 Compose 组件版本兼容
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // 已经有了 libs.androidx.compose.material3，不需要再重复写 implementation("...material3:1.3.1")
    implementation(libs.androidx.compose.material3)

    // 【关键优化 5】谨慎使用 extended 图标库
    // 这个库包含数千个图标，非常大。开启 R8 (Minify) 后它会被压缩。
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}