import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

group = "org.viptv"
version = providers.gradleProperty("VERSION_NAME").getOrElse("0.1.0-SNAPSHOT")

kotlin {
    jvmToolchain(17)
    androidTarget {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
    sourceSets {
        commonMain.dependencies { api(libs.kotlinx.coroutines.core) }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.media3.common)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.exoplayer.dash)
            implementation(libs.media3.exoplayer.hls)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidUnitTest.dependencies { implementation(kotlin("test")) }
        androidInstrumentedTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.androidx.test.runner)
        }
    }
}

android {
    namespace = "org.viptv.android.video"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}
