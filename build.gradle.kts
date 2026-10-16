plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.rakkez.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rakkez.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    signingConfigs {
        create("rakkez") {
            storeFile = file("rakkez.keystore")
            storePassword = "rakkez123"
            keyAlias = "rakkez"
            keyPassword = "rakkez123"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("rakkez") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("rakkez")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
}
