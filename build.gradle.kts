import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.bundling.AbstractArchiveTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    `maven-publish`
}

group = "com.getair"
version = providers.gradleProperty("VERSION_NAME").getOrElse("0.1.0-SNAPSHOT")

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    jvmToolchain(17)
    androidTarget {
        publishLibraryVariants("release")
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
    jvm { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }
    linuxX64()
    mingwX64()
    macosX64()
    macosArm64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    js(IR) { browser(); nodejs() }
    wasmJs { browser(); nodejs() }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.media3.common)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.exoplayer.dash)
            implementation(libs.media3.exoplayer.hls)
        }
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
        }
        androidInstrumentedTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.androidx.test.runner)
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        appleTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.getair.video"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Air Video KMP")
            description.set("Backend-neutral Kotlin Multiplatform playback contracts and platform adapters for Air.")
            url.set("https://github.com/air-tv/video")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                    distribution.set("repo")
                }
                license {
                    name.set("Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("repo")
                }
            }
            scm {
                url.set("https://github.com/air-tv/video")
                connection.set("scm:git:https://github.com/air-tv/video.git")
                developerConnection.set("scm:git:ssh://git@github.com/air-tv/video.git")
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/air-tv/video")
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orNull
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
            }
        }
        providers.gradleProperty("TEMP_MAVEN_REPOSITORY").orNull?.let { temporaryRepository ->
            maven {
                name = "HostTest"
                url = uri(temporaryRepository)
            }
        }
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    from(rootProject.file("LICENSE-MIT")) {
        into("META-INF")
        rename { "LICENSE-MIT" }
    }
    from(rootProject.file("LICENSE-APACHE")) {
        into("META-INF")
        rename { "LICENSE-APACHE" }
    }
}

tasks.withType<PublishToMavenRepository>().configureEach {
    if (name.endsWith("ToGitHubPackagesRepository")) {
        val releaseVersionProvider = providers.gradleProperty("VERSION_NAME")
        val tagProvider = providers.environmentVariable("GITHUB_REF_NAME")
        val refTypeProvider = providers.environmentVariable("GITHUB_REF_TYPE")
        val eventProvider = providers.environmentVariable("GITHUB_EVENT_NAME")
        val actionsProvider = providers.environmentVariable("GITHUB_ACTIONS")
        doFirst {
            val releaseVersion = releaseVersionProvider.orNull.orEmpty()
            val stableVersion = Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")
            require(actionsProvider.orNull == "true" && eventProvider.orNull == "release") {
                "GitHub Packages publishing is restricted to the release workflow"
            }
            require(stableVersion.matches(releaseVersion)) {
                "GitHub Packages requires a stable MAJOR.MINOR.PATCH VERSION_NAME"
            }
            require(refTypeProvider.orNull == "tag" && tagProvider.orNull == "v$releaseVersion") {
                "GitHub Packages VERSION_NAME must exactly match the vMAJOR.MINOR.PATCH tag"
            }
        }
    }
}
