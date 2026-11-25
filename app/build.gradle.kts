plugins {
    id("com.android.application")
    // Kotlin is built into AGP 9; only the compiler plugins are applied here.
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "dev.damado.nordpass"
    // Current AndroidX libraries require API 37+. targetSdk stays at 36.
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.damado.nordpass"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("nordpass.versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("nordpass.versionName").orNull ?: "0.1.0-dev"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("NORD_PASS_KEYSTORE_PATH") ?: "release.jks")
            storePassword = System.getenv("NORD_PASS_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("NORD_PASS_KEY_ALIAS") ?: "nord-pass"
            keyPassword = System.getenv("NORD_PASS_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        getByName("release") {
            // Only CI has the release keystore; local release builds stay unsigned.
            if (System.getenv("NORD_PASS_KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Built-in Kotlin derives jvmTarget from targetCompatibility above.
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // These aren't managed by the Compose BOM, so pin their stable versions.
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.1")
    implementation("androidx.navigation:navigation-compose:2.10.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.squareup.okhttp3:okhttp:5.3.0")
    implementation("org.jsoup:jsoup:1.24.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
