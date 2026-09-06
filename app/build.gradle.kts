// Apply the Android and Kotlin plugins
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Configure the application identity and supported Android versions
android {
    namespace = "com.example.pawcareai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.pawcareai"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Android Emulator uses 10.0.2.2 to reach Laravel Herd on Windows.
        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2/api/\"")
    }

    // Configure release packaging without changing the app interface
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Keep Java and Kotlin compilation compatible
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    // Expose the backend address through BuildConfig
    buildFeatures {
        buildConfig = true
    }
}

// Libraries used by the existing screens, API connection and checks
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    testImplementation(libs.junit)
}
