package org.viptv.app

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** One published GitHub release as described by its `update.json` (VERSIONING.md, schema 1). */
@Serializable
internal data class UpdateManifest(
    val schema: Int,
    val versionCode: Int,
    val versionName: String,
    val minSdk: Int,
    val revision: String,
    val releaseUrl: String,
    val apk: UpdateApk,
)

@Serializable
internal data class UpdateApk(val name: String, val url: String, val size: Long, val sha256: String)

internal sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck
    data class Unsupported(val manifest: UpdateManifest) : UpdateCheck
    data class Available(val manifest: UpdateManifest) : UpdateCheck
}

internal class UpdateManifestException(message: String) : IOException(message)

private const val RELEASE_DOWNLOADS = "https://github.com/viptv-org/android/releases/download/"
internal const val UPDATE_CHECK_INTERVAL_MILLIS = 6L * 60 * 60 * 1000
private val updateJson = Json { ignoreUnknownKeys = true }
private val sha256Hex = Regex("[0-9a-f]{64}")
private val apkName = Regex("[A-Za-z0-9._-]+\\.apk")

/** APKs are signed with a public key, so only this repository's release downloads are trusted. */
internal fun isTrustedApkUrl(url: String): Boolean =
    url.startsWith(RELEASE_DOWNLOADS) && url.substring(RELEASE_DOWNLOADS.length).let { it.count { c -> c == '/' } == 1 && ".." !in it }

internal fun parseUpdateManifest(text: String, trustedUrl: (String) -> Boolean = ::isTrustedApkUrl): UpdateManifest {
    val manifest = try {
        updateJson.decodeFromString<UpdateManifest>(text)
    } catch (error: IllegalArgumentException) {
        throw UpdateManifestException("Malformed update manifest")
    }
    fun check(valid: Boolean, problem: String) { if (!valid) throw UpdateManifestException(problem) }
    check(manifest.schema == 1, "Unsupported update manifest schema ${manifest.schema}")
    check(manifest.versionCode > 0 && manifest.minSdk > 0, "Invalid update version")
    check(sha256Hex.matches(manifest.apk.sha256), "Invalid update checksum")
    check(manifest.apk.size > 0, "Invalid update size")
    check(apkName.matches(manifest.apk.name) && manifest.apk.url.endsWith("/" + manifest.apk.name), "Invalid update file name")
    check(trustedUrl(manifest.apk.url), "Untrusted update location")
    return manifest
}

internal fun evaluateUpdate(manifest: UpdateManifest, installedVersionCode: Int, sdkInt: Int): UpdateCheck = when {
    manifest.versionCode <= installedVersionCode -> UpdateCheck.UpToDate
    manifest.minSdk > sdkInt -> UpdateCheck.Unsupported(manifest)
    else -> UpdateCheck.Available(manifest)
}

/** Launch checks are throttled; a clock that moved backwards allows a new check. */
internal fun isUpdateCheckDue(lastCheckMillis: Long?, nowMillis: Long): Boolean =
    lastCheckMillis == null || nowMillis < lastCheckMillis || nowMillis - lastCheckMillis >= UPDATE_CHECK_INTERVAL_MILLIS

/**
 * Fetches the latest release manifest and downloads its APK into [directory].
 * A downloaded file is only returned after its size and SHA-256 match the manifest.
 */
internal class AppUpdateClient(
    private val manifestUrl: String,
    private val directory: File,
    private val client: OkHttpClient,
    private val trustedUrl: (String) -> Boolean = ::isTrustedApkUrl,
) {
    suspend fun fetchManifest(): UpdateManifest = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(manifestUrl).header("Cache-Control", "no-cache").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Update check failed with HTTP ${response.code}")
            parseUpdateManifest(response.body?.string() ?: throw IOException("Empty update manifest"), trustedUrl)
        }
    }

    suspend fun download(manifest: UpdateManifest, onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }): File = withContext(Dispatchers.IO) {
        val apk = manifest.apk
        if (!trustedUrl(apk.url)) throw UpdateManifestException("Untrusted update location")
        directory.mkdirs()
        directory.listFiles()?.filter { it.name != apk.name }?.forEach(File::delete)
        val target = File(directory, apk.name)
        if (target.isFile && target.length() == apk.size && sha256(target) == apk.sha256) return@withContext target
        val partial = File(directory, apk.name + ".part")
        try {
            client.newCall(Request.Builder().url(apk.url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Update download failed with HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty update download")
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            downloaded += count
                            if (downloaded > apk.size) throw IOException("Update download is larger than expected")
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                            onProgress(downloaded, apk.size)
                        }
                    }
                }
                if (downloaded != apk.size) throw IOException("Update download is incomplete")
                if (digest.digest().toHex() != apk.sha256) throw IOException("Update checksum does not match")
            }
            target.delete()
            if (!partial.renameTo(target)) throw IOException("Could not store update")
            target
        } finally {
            partial.delete()
        }
    }
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().toHex()
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
