import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.multiplatform")
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    androidTarget { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }
    sourceSets {
        androidMain.dependencies {
            implementation(project(":"))
            implementation(libs.androidx.activity.compose)
            implementation(platform("androidx.compose:compose-bom:2025.04.01"))
            implementation(libs.androidx.compose.ui)
            implementation(libs.androidx.compose.foundation)
            implementation(libs.androidx.compose.material3)
            implementation("androidx.compose.material:material-icons-extended")
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.kotlinx.coroutines.android)
            implementation("io.coil-kt:coil-compose:2.7.0")
            implementation("com.google.zxing:core:3.5.3")
        }
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            implementation(platform("androidx.compose:compose-bom:2025.04.01"))
            implementation(libs.androidx.compose.ui.test.junit4)
        }
    }
}

android {
    namespace = "org.viptv.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.viptv.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        create("viptvDevelopment") {
            // This committed key is deliberately debug-only and can never sign a release build.
            storeFile = file("signing/viptv-development.p12")
            storeType = "PKCS12"
            storePassword = "viptv-development-only"
            keyAlias = "viptv-development"
            keyPassword = "viptv-development-only"
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("viptvDevelopment")
        }
    }
}
