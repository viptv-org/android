package org.viptv.app

import com.sun.jna.Library
import com.sun.jna.Native
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import kotlin.test.*
import uniffi.playback_gateway_ffi.TorrentClient

/** Real Linux native IO evidence. This class makes no Android/JNI/Media3 claim. */
class OwnedNativeHostPipelineTest {
    interface LinuxClock : Library { fun clock_gettime(id: Int, result: LongArray): Int }
    private val libc = Native.load("c", LinuxClock::class.java)
    private fun now(): Long {
        val result = LongArray(2)
        check(libc.clock_gettime(7, result) == 0) // Linux CLOCK_BOOTTIME includes suspend.
        return result[0] * 1_000_000_000L + result[1]
    }
    private fun read(url: String, offset: Long): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 2000; connection.readTimeout = 10_000
        connection.setRequestProperty("Range", "bytes=$offset-${offset + 63}")
        return try {
            assertEquals(206, connection.responseCode)
            connection.inputStream.use { it.readBytes() }.also { assertEquals(64, it.size) }
        } finally { connection.disconnect() }
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    @Test fun actualAuthorizationRustEngineExactBytesAndIndependentSettlement() = runBlocking {
        val directory = File(requireNotNull(System.getProperty("owned.fixture.directory")))
        val config = JSONObject(directory.resolve("pipeline-config.json").readText())
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
        val certificate = directory.resolve("tls/cert.pem").inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
        keyStore.setCertificateEntry("owned-loopback", certificate)
        val trusts = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(keyStore) }
        val trust = trusts.trustManagers.single() as X509TrustManager
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        val http = OkHttpClient.Builder().sslSocketFactory(ssl.socketFactory, trust).build()
        val origin = config.getString("origin")
        val jobs = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val cacheParent = directory.resolve("host-cache").apply { mkdirs() }
        val cache = NativeTorrentCache.open(cacheParent, { path, capacity -> NativeTorrentEngineCacheManager(
            TorrentClient.newNativeOwned(path.path, capacity.toULong(), listOf(config.getString("peer")))) }, nowNanos = ::now)
        val coordinator = NativeTorrentCoordinator(cache, ::now, { true }, {}, main = Dispatchers.Default)
        fun control(generation: Long): NativePlaybackControl {
            lateinit var value: NativePlaybackControl
            val transport = NativePlaybackTransport(origin, { config.getString("access_token") }, { true }, cache, client = http)
            value = NativePlaybackControl(origin, "owned_linux", generation, { true }, transport,
                V2PlaybackControl(origin) { _, _, _ -> error("fixture must admit native") }, jobs,
                NativePlaybackClock { now() / 1_000_000 }, { if (value.isLocallyRetired()) coordinator.cancelNative(value) }, {})
            return value
        }
        fun request(): JSONObject = JSONObject().put("requestId", java.util.UUID.randomUUID().toString())
            .put("streamId", config.getJSONArray("sources").getJSONObject(0).getString("stream_id")).put("position", 0)
            .put("client", JSONObject().put("platform", "android_tv").put("canPlayDirect", true).put("maxWidth", 1920)
                .put("maxHeight", 1080).put("videoCodecs", JSONArray().put("h264")).put("audioCodecs", JSONArray().put("aac")))
        try {
            val first = control(1); coordinator.ownControl(first, 1)
            assertIs<NativePlaybackStart.Native>(first.start(request(), true, true, cache))
            val accepted = assertNotNull(first.firstGrantAcceptedAtMillis())
            val candidate = coordinator.prepare(first, 1)
            assertTrue(now() / 1_000_000 - accepted <= 30_000)
            val selected = config.getJSONArray("files").getJSONObject(1)
            assertEquals(selected.getString("prefix_sha256"), sha(withContext(Dispatchers.IO) { read(candidate.capability.url, 0) }))
            assertEquals(selected.getString("sample_sha256"), sha(withContext(Dispatchers.IO) { read(candidate.capability.url, selected.getLong("sample_offset")) }))
            val second = control(2); coordinator.ownControl(second, 2)
            assertIs<NativePlaybackStart.Native>(second.start(request(), true, true, cache))
            val independent = coordinator.prepare(second, 2)
            val began = now()
            first.retireLocal(); coordinator.cancelNative(first)
            val deadline = began + 2_000_000_000
            assertTrue(withContext(Dispatchers.IO) { cache.retire(candidate.work, deadline) && first.joinLocal(deadline) })
            val joinedMillis = (now() - began) / 1_000_000
            assertTrue(now() <= deadline)
            assertEquals(selected.getString("prefix_sha256"), sha(withContext(Dispatchers.IO) { read(independent.capability.url, 0) }))
            assertTrue(coordinator.retire(candidate)); assertTrue(coordinator.retire(independent)); assertTrue(coordinator.closeScope())
            assertEquals(0L, cache.heldPayloadCapacityBytes)
            directory.resolve("host-pipeline-evidence.json").writeText(JSONObject().put("platform", "linux_host")
                .put("actual_backend_authorization", true).put("actual_core_bridge", true).put("actual_owned_tcp_ffi", true)
                .put("exact_selected_bytes", true).put("independent_grants", true).put("joined_local_millis", joinedMillis)
                .put("android_jni", "NOT RUN").put("media3_decode", "NOT RUN").toString())
        } finally {
            withContext(NonCancellable) { runCatching { coordinator.closeScope() } }
            jobs.cancel()
        }
    }
}
