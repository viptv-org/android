package org.viptv.app

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

class AppUpdatesTest {
    private val apkBytes = "fixture apk payload ".repeat(5_000)
    private val apkSha = MessageDigest.getInstance("SHA-256").digest(apkBytes.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun manifestJson(
        url: String = "https://github.com/viptv-org/android/releases/download/v0.3.0/viptv-android-0.3.0.apk",
        sha256: String = apkSha,
        size: Long = apkBytes.length.toLong(),
        schema: Int = 1,
    ) = """
        {"schema":$schema,"versionCode":3000,"versionName":"0.3.0","minSdk":24,"revision":"abc",
         "releaseUrl":"https://github.com/viptv-org/android/releases/tag/v0.3.0","future":true,
         "apk":{"name":"viptv-android-0.3.0.apk","url":"$url","size":$size,"sha256":"$sha256"}}
    """.trimIndent()

    @Test
    fun parsesReleaseManifestAndIgnoresUnknownFields() {
        val manifest = parseUpdateManifest(manifestJson())
        assertEquals(3000, manifest.versionCode)
        assertEquals("viptv-android-0.3.0.apk", manifest.apk.name)
    }

    @Test
    fun rejectsUnsupportedOrUnsafeManifests() {
        assertFailsWith<UpdateManifestException> { parseUpdateManifest(manifestJson(schema = 2)) }
        assertFailsWith<UpdateManifestException> { parseUpdateManifest(manifestJson(sha256 = "ABC")) }
        assertFailsWith<UpdateManifestException> { parseUpdateManifest(manifestJson(size = 0)) }
        assertFailsWith<UpdateManifestException> { parseUpdateManifest("{not json") }
        assertFailsWith<UpdateManifestException> {
            parseUpdateManifest(manifestJson(url = "https://example.com/viptv-org/android/releases/download/v0.3.0/viptv-android-0.3.0.apk"))
        }
        assertFailsWith<UpdateManifestException> {
            parseUpdateManifest(manifestJson(url = "https://github.com/viptv-org/android/releases/download/v0.3.0/other.apk"))
        }
    }

    @Test
    fun trustsOnlyThisRepositorysReleaseDownloads() {
        assertTrue(isTrustedApkUrl("https://github.com/viptv-org/android/releases/download/v0.3.0/viptv-android-0.3.0.apk"))
        assertFalse(isTrustedApkUrl("http://github.com/viptv-org/android/releases/download/v0.3.0/a.apk"))
        assertFalse(isTrustedApkUrl("https://github.com/viptv-org/android-fork/releases/download/v0.3.0/a.apk"))
        assertFalse(isTrustedApkUrl("https://github.com/viptv-org/android/releases/download/v0.3.0/../x/a.apk"))
        assertFalse(isTrustedApkUrl("https://github.com/viptv-org/android/releases/download/a.apk"))
    }

    @Test
    fun offersOnlyNewerSupportedReleases() {
        val manifest = parseUpdateManifest(manifestJson())
        assertIs<UpdateCheck.Available>(evaluateUpdate(manifest, installedVersionCode = 1, sdkInt = 30))
        assertEquals(UpdateCheck.UpToDate, evaluateUpdate(manifest, installedVersionCode = 3000, sdkInt = 30))
        assertEquals(UpdateCheck.UpToDate, evaluateUpdate(manifest, installedVersionCode = 4000, sdkInt = 30))
        assertIs<UpdateCheck.Unsupported>(evaluateUpdate(manifest, installedVersionCode = 1, sdkInt = 23))
    }

    @Test
    fun throttlesLaunchChecks() {
        assertTrue(isUpdateCheckDue(null, 1_000))
        assertFalse(isUpdateCheckDue(1_000, 1_000 + UPDATE_CHECK_INTERVAL_MILLIS - 1))
        assertTrue(isUpdateCheckDue(1_000, 1_000 + UPDATE_CHECK_INTERVAL_MILLIS))
        assertTrue(isUpdateCheckDue(5_000, 1_000))
    }

    @Test
    fun fetchesManifestAndDownloadsVerifiedApk() = runBlocking {
        val directory = Files.createTempDirectory("viptv-update").toFile()
        lateinit var origin: String
        FixtureServer(expectedRequests = 2) { request ->
            when (request.target) {
                "/update.json" -> FixtureResponse(manifestJson(url = "$origin/v0.3.0/viptv-android-0.3.0.apk"))
                "/v0.3.0/viptv-android-0.3.0.apk" -> FixtureResponse(apkBytes)
                else -> FixtureResponse("", 404)
            }
        }.use { server ->
            origin = server.origin
            File(directory, "viptv-android-0.2.9.apk").writeText("stale")
            val updates = AppUpdateClient("$origin/update.json", directory, OkHttpClient()) { it.startsWith(origin) }
            val manifest = updates.fetchManifest()
            var progress = 0L
            val apk = updates.download(manifest) { downloaded, _ -> progress = downloaded }
            assertEquals(apkBytes, apk.readText())
            assertEquals(apkBytes.length.toLong(), progress)
            assertEquals(listOf("viptv-android-0.3.0.apk"), directory.list()!!.toList())
            // A verified file already on disk is reused without another request.
            assertEquals(apk, updates.download(manifest))
        }
    }

    @Test
    fun discardsDownloadsThatDoNotMatchTheManifest() = runBlocking {
        val directory = Files.createTempDirectory("viptv-update").toFile()
        FixtureServer(expectedRequests = 1) { FixtureResponse(apkBytes.reversed()) }.use { server ->
            val updates = AppUpdateClient("${server.origin}/update.json", directory, OkHttpClient()) { it.startsWith(server.origin) }
            val manifest = parseUpdateManifest(manifestJson(url = "${server.origin}/v0.3.0/viptv-android-0.3.0.apk")) { true }
            assertFailsWith<UpdateDamagedException> { updates.download(manifest) }
            assertEquals(emptyList<String>(), directory.list()!!.toList())
        }
    }

    @Test
    fun remembersManifestAcrossRestarts() {
        val manifest = parseUpdateManifest(manifestJson())
        assertEquals(manifest, parseUpdateManifest(updateManifestJson(manifest)))
    }

    @Test
    fun promptsOncePerVersionAndDescribesStatus() {
        val available = UpdateStatus.Available(parseUpdateManifest(manifestJson()))
        assertTrue(shouldPromptForUpdate(available, dismissedVersionCode = 0))
        assertFalse(shouldPromptForUpdate(available, dismissedVersionCode = 3000))
        assertFalse(shouldPromptForUpdate(UpdateStatus.UpToDate, dismissedVersionCode = 0))
        assertEquals("Version 0.2.0 · Update available", aboutRowDetail(available, "0.2.0"))
        assertEquals("Version 0.1.0", aboutRowDetail(UpdateStatus.Disabled, "0.1.0"))
        assertEquals("VIPTV 0.3.0 is available.", aboutUpdateLine(available))
        assertEquals("Updates are off for development builds.", aboutUpdateLine(UpdateStatus.Disabled))
        assertEquals(null, aboutUpdateLine(UpdateStatus.Unknown))
    }
}
