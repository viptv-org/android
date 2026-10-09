plugins { alias(libs.plugins.android.library) }

android {
    namespace = "androidx.media3.decoder.ffmpeg"
    compileSdk = 36
    sourceSets.getByName("main").java.exclude("**/ExperimentalFfmpegVideoRenderer.java")
    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.annotation:annotation-jvm:1.7.1")
    implementation(libs.media3.common)
    implementation(libs.media3.exoplayer)
    implementation("androidx.media3:media3-decoder:${libs.versions.media3.get()}")
    compileOnly("org.checkerframework:checker-qual:3.33.0")
}

val verifyAudioDecoder by tasks.registering(Exec::class) {
    workingDir(rootDir)
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3",
        "scripts/check-ffmpeg-audio.py")
}
tasks.named("preBuild") { dependsOn(verifyAudioDecoder) }
