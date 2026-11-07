plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI đặt APP_VERSION (1.0, 1.1, …, tính từ release mới nhất) và GITHUB_RUN_NUMBER (luôn tăng).
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val appVersion = System.getenv("APP_VERSION")?.takeIf { it.isNotBlank() } ?: "1.0"

android {
    namespace = "com.itlboy.mdmcheck"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.itlboy.mdmcheck"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // CI giải mã khoá ký cố định từ secret, để bản mới cài đè được lên bản cũ.
    // Build ở máy (không có biến này) thì dùng khoá debug mặc định.
    val keystore = System.getenv("SIGNING_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    if (keystore != null) {
        signingConfigs {
            create("ci") {
                storeFile = keystore
                storeType = "pkcs12"
                storePassword = System.getenv("SIGNING_PASSWORD")
                keyAlias = "mdmcheck"
                keyPassword = System.getenv("SIGNING_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (keystore != null) signingConfig = signingConfigs.getByName("ci")
        }
        release {
            isMinifyEnabled = false
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
    testOptions {
        // Rules dùng org.json qua Updater; trên JVM trả giá trị mặc định thay vì ném lỗi.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
