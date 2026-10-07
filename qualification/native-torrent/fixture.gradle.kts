import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Applied only after the normal app's artifact verification has been configured.
val fixtureArtifacts = providers.gradleProperty("nativeTorrentFixtureArtifacts")
if (fixtureArtifacts.isPresent) {
    val fixtureConfiguration = providers.gradleProperty("nativeTorrentFixtureConfig")
    require(fixtureConfiguration.isPresent) { "Isolated native fixture configuration required" }
    require(providers.gradleProperty("fixtureCa").isPresent) { "Isolated loopback fixture trust required" }
    val products = rootProject.file(fixtureArtifacts.get())
    val configuration = rootProject.file(fixtureConfiguration.get())
    val normalKotlin = rootProject.file("vendor/playback-gateway/ffi/generated/kotlin").canonicalFile
    val normalJni = rootProject.file("vendor/playback-gateway/ffi/generated/android/jniLibs").canonicalFile
    val roots = rootProject.file("qualification/native-torrent")
    extensions.configure<KotlinMultiplatformExtension> {
        sourceSets.getByName("androidMain").kotlin.apply {
            setSrcDirs(srcDirs.filter { it.canonicalFile != normalKotlin } + products.resolve("kotlin") + roots.resolve("androidMain/kotlin"))
        }
        sourceSets.getByName("androidInstrumentedTest").kotlin.srcDir(roots.resolve("androidInstrumentedTest/kotlin"))
    }
    val fixtureAssets = layout.buildDirectory.dir("generated/ownedNativeFixtureAssets")
    extensions.configure<BaseAppModuleExtension> {
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
    gradle.taskGraph.whenReady {
        require(allTasks.none { it.name.contains("release", ignoreCase = true) }) { "Owned native fixtures are debug-only" }
    }
}
