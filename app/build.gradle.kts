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
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.kotlinx.coroutines.android)
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
}
