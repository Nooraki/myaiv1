plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ir.example.slmchat"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.example.slmchat"
        minSdk = 26
        targetSdk = 35
        // در گیت‌هاب از متغیرهای محیطی پر می‌شوند؛ محلی مقدار پیش‌فرض دارند.
        versionCode = (System.getenv("VERSION_CODE") ?: "2").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "2.0"

        // کتابخانه‌های native ی MediaPipe حجیم‌اند؛ فقط arm64 (تقریباً همه‌ی گوشی‌های امروزی)
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        val ksPath = System.getenv("KEYSTORE_PATH")
        if (!ksPath.isNullOrBlank()) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // اگر keystore تعریف نشده باشد null می‌شود و APK امضا‌نشده خواهد بود
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // موتور اجرای آفلاین مدل‌های زبانی کوچک (SLM) روی گوشی
    implementation("com.google.mediapipe:tasks-genai:0.10.27")

    // دانلود پس‌زمینه با قابلیت ادامه‌دادن و اعلان
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
