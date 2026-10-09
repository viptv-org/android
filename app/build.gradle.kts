import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("plugin.serialization") version "2.1.10"
    id("org.jetbrains.kotlin.multiplatform")
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    androidTarget { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }
    sourceSets {
        androidMain {
            kotlin.srcDir("../vendor/core/generated/native-kotlin")
            kotlin.srcDir("../vendor/core/generated/kotlin")
            kotlin.srcDir("../vendor/core/generated/kotlin-wire")
            kotlin.srcDir("../vendor/core/adapters/android/src/main/kotlin")
            kotlin.srcDir("../vendor/playback-gateway/ffi/generated/kotlin")
        }
        androidMain.dependencies {
            implementation(project(":"))
            implementation("net.java.dev.jna:jna:5.17.0@aar")
            implementation(libs.kotlinx.serialization.json)
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
            implementation("com.squareup.okhttp3:okhttp:4.12.0")
        }
        androidUnitTest.dependencies {
            // Android AAR supplies device JNI libraries; host tests need the JAR dispatch resources.
            runtimeOnly("net.java.dev.jna:jna:5.17.0@jar")
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(platform("androidx.compose:compose-bom:2025.04.01"))
            implementation(libs.androidx.compose.ui.test.junit4)
            // JVM unit tests exercise the real OkHttp/JSON boundary.
            implementation("org.json:json:20240303")
            implementation("com.squareup.okhttp3:okhttp:4.12.0")
        }
        androidInstrumentedTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.androidx.test.runner)
            implementation("androidx.test.ext:junit:1.3.0")
            implementation(platform("androidx.compose:compose-bom:2025.04.01"))
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    lint { baseline = file("lint-baseline.xml") }
    buildFeatures { buildConfig = true }
    sourceSets.getByName("main").jniLibs.srcDir("src/androidMain/jniLibs")
    sourceSets.getByName("main").jniLibs.srcDir("../vendor/playback-gateway/ffi/generated/android/jniLibs")
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/nativeTorrentAssets"))
    packaging.jniLibs.useLegacyPackaging = false
    // The immutable gateway release artifacts are already stripped at source.
    packaging.jniLibs.keepDebugSymbols += "**/libplayback_gateway_ffi.so"
    testOptions.unitTests.all {
        it.systemProperty("jna.library.path", rootProject.file("vendor/core/target/debug").absolutePath)
        it.systemProperty("viptv.core.nativeVectors", rootProject.file("vendor/core/tests/native-torrent-vectors.json").absolutePath)
    }
    namespace = "org.viptv.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.viptv.app"
        minSdk = 24
        ndk { abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("boolean", "PLAYBACK_DIAGNOSTICS", "false")
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
            buildConfigField("boolean", "PLAYBACK_DIAGNOSTICS", "true")
        }
        create("performance") {
            initWith(getByName("debug"))
            isDebuggable = false
            isJniDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-performance.pro")
            matchingFallbacks += listOf("release")
        }
    }
}

dependencies {
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
val verifyDesign by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("node", "scripts/design-sync.mjs", "check")
}
tasks.named("preBuild") { dependsOn(verifyDesign) }

val verifyNativeTorrent by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("node", "scripts/native-torrent-sync.mjs", "check")
}
val prepareNativeTorrentNotices by tasks.registering(Copy::class) {
    dependsOn(verifyNativeTorrent)
    from("../vendor/playback-gateway") {
        include("LICENSE", "PROVENANCE.md", "THIRD_PARTY/**")
        into("playback-gateway")
    }
    into(layout.buildDirectory.dir("generated/nativeTorrentAssets"))
}
tasks.named("preBuild") { dependsOn(verifyNativeTorrent, prepareNativeTorrentNotices) }

// An opt-in, debug-only trust anchor for the loopback emulator fixture server.
// Public CA material is generated under build/; production resources never use it.
val fixtureCa = providers.gradleProperty("fixtureCa")
if (fixtureCa.isPresent) {
    require(providers.gradleProperty("nativeTorrentFixtureArtifacts").isPresent) {
        "Fixture trust requires the isolated owned native fixture application"
    }
    val fixtureResources = layout.buildDirectory.dir("generated/fixtureRes")
    android.sourceSets.getByName("debug").res.srcDir(fixtureResources)
    val prepareFixtureTrust by tasks.registering {
        val certificate = rootProject.file(fixtureCa.get())
        inputs.file(certificate)
        outputs.dir(fixtureResources)
        doLast {
            val output = fixtureResources.get().asFile
            output.resolve("raw").mkdirs()
            output.resolve("xml").mkdirs()
            certificate.copyTo(output.resolve("raw/viptv_fixture_ca.pem"), overwrite = true)
            output.resolve("xml/network_security_config.xml").writeText("""
                <network-security-config>
                  <base-config cleartextTrafficPermitted="true"><trust-anchors><certificates src="system" /></trust-anchors></base-config>
                  <debug-overrides><trust-anchors><certificates src="@raw/viptv_fixture_ca" /></trust-anchors></debug-overrides>
                </network-security-config>
            """.trimIndent())
        }
    }
    tasks.matching { it.name == "preDebugBuild" }.configureEach { dependsOn(prepareFixtureTrust) }
}

// Private fixture configuration uses the owning plugin's typed DSL/classpath.
val fixtureArtifacts = providers.gradleProperty("nativeTorrentFixtureArtifacts")
if (fixtureArtifacts.isPresent) {
    val fixtureConfiguration = providers.gradleProperty("nativeTorrentFixtureConfig")
    require(fixtureConfiguration.isPresent) { "Isolated native fixture configuration required" }
    require(fixtureCa.isPresent) { "Isolated loopback fixture trust required" }
    require(gradle.startParameter.taskNames.none { it.contains("release", ignoreCase = true) }) { "Owned native fixtures are debug-only" }
    val products = rootProject.file(fixtureArtifacts.get())
    val configuration = rootProject.file(fixtureConfiguration.get())
    val normalKotlin = rootProject.file("vendor/playback-gateway/ffi/generated/kotlin").canonicalFile
    val normalJni = rootProject.file("vendor/playback-gateway/ffi/generated/android/jniLibs").canonicalFile
    val roots = rootProject.file("qualification/native-torrent")
    kotlin {
        sourceSets.getByName("androidMain").kotlin.apply {
            setSrcDirs(srcDirs.filter { it.canonicalFile != normalKotlin } + products.resolve("kotlin") + roots.resolve("androidMain/kotlin"))
        }
        sourceSets.getByName("androidInstrumentedTest").kotlin.srcDir(roots.resolve("androidInstrumentedTest/kotlin"))
    }
    val fixtureAssets = layout.buildDirectory.dir("generated/ownedNativeFixtureAssets")
    android {
        buildTypes.getByName("debug").apply {
            applicationIdSuffix = ".nativefixture"
            versionNameSuffix = "-owned-native-fixture"
        }
        sourceSets.getByName("main").jniLibs.apply {
            setSrcDirs(srcDirs.filter { it.canonicalFile != normalJni } + products.resolve("jniLibs"))
        }
        sourceSets.getByName("debug").apply {
            manifest.srcFile(roots.resolve("androidMain/AndroidManifest.xml"))
            assets.srcDir(fixtureAssets)
        }
    }
    androidComponents {
        beforeVariants(selector().withBuildType("release")) { it.enable = false }
    }
    val verifyOwnedFixture by tasks.registering(Exec::class) {
        workingDir(rootProject.projectDir)
        commandLine("python3", roots.resolve("verify_fixture_artifacts.py"), products, rootProject.file("TORRENT_REF"), configuration)
    }
    val prepareOwnedFixtureConfiguration by tasks.registering(Copy::class) {
        dependsOn(verifyOwnedFixture)
        from(configuration) { rename { "config.json" } }
        into(fixtureAssets.map { it.dir("native-fixture") })
    }
    tasks.named("preBuild") { dependsOn(verifyOwnedFixture, prepareOwnedFixtureConfiguration) }
}
