package org.viptv.app

import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import org.viptv.video.AndroidMedia3BackendFactory
import org.viptv.video.PlaybackSource
import org.viptv.video.TrackSelectionResult
import uniffi.playback_gateway_ffi.TorrentClient
import uniffi.playback_gateway_ffi.TorrentSettlement
import kotlin.test.*

/** Actual auth/Core/strict JNI/loopback/Media3 effects; no production qualification claim. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class OwnedNativePipelineTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun configuration() = JSONObject(context.assets.open("native-fixture/config.json").bufferedReader().use { it.readText() })
    private fun request(source: String) = JSONObject().put("requestId", UUID.randomUUID().toString()).put("streamId", source)
        .put("position", 0).put("client", JSONObject().put("platform", "android_tv").put("canPlayDirect", true)
            .put("maxWidth", 1920).put("maxHeight", 1080).put("videoCodecs", JSONArray().put("h264")).put("audioCodecs", JSONArray().put("aac")))
    private suspend fun await(timeout: Long = 15_000, predicate: () -> Boolean) {
        withTimeout(timeout) { while (!predicate()) delay(25) }
    }
    private fun bytes(url: String, offset: Long, length: Int): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 2_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Range", "bytes=$offset-${offset + length - 1}")
        try {
            assertEquals(206, connection.responseCode, "selected byte range must be honored")
            return connection.inputStream.use { it.readBytes() }.also { assertEquals(length, it.size) }
        } finally { connection.disconnect() }
    }
    private fun denied(url: String): Boolean {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 1_000
        connection.readTimeout = 1_000
        return try { connection.responseCode in listOf(401, 403, 404, 410) } catch (_: Exception) { true }
        finally { connection.disconnect() }
    }

    private suspend fun fixtureControl(config: JSONObject, value: JSONObject) = withContext(Dispatchers.IO) {
        val connection = URL(config.getString("origin") + "/__fixture/control").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 2000
        connection.readTimeout = 2000
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer " + config.getString("access_token"))
        connection.setRequestProperty("Content-Type", "application/json")
        try {
            connection.outputStream.use { it.write(value.toString().toByteArray()) }
            assertEquals(200, connection.responseCode, "isolated fixture command refused")
        } finally { connection.disconnect() }
    }

    @Test fun bRealJniBackDuringMetadataAndExactIndexCapacityRefusalSettle() = runBlocking {
        val config = configuration()
        val peer = config.getString("peer")
        val hash = config.getString("info_hash")
        val bytes = config.getLong("payload_bytes")
        val parent = java.io.File(context.noBackupFilesDir, "owned-jni-${UUID.randomUUID()}").apply { mkdirs() }
        fixtureControl(config, JSONObject().put("hold_metadata", true))
        val client = TorrentClient.newNativeOwned(parent.path, bytes.toULong(), listOf(peer))
        var settled = false
        try {
            val acquisition = client.beginSelected("magnet:?xt=urn:btih:$hash", hash, 1u, null, 30_000u)
            val waiter = async(Dispatchers.IO) { runCatching { acquisition.waitReady() } }
            delay(500)
            assertFalse(waiter.isCompleted, "metadata wait must actually be pending before Back")
            val before = SystemClock.elapsedRealtimeNanos()
            acquisition.cancel()
            assertEquals(TorrentSettlement.SETTLED, withContext(Dispatchers.IO) { acquisition.cancelAndWait() })
            assertTrue((SystemClock.elapsedRealtimeNanos() - before) <= 2_000_000_000, "real pending metadata join exceeds bound")
            assertTrue(withTimeout(2000) { waiter.await() }.isFailure)
            acquisition.close()
            fixtureControl(config, JSONObject().put("hold_metadata", false))
            val wrong = client.beginSelected("magnet:?xt=urn:btih:$hash", hash, 99u, null, 30_000u)
            assertTrue(withContext(Dispatchers.IO) { runCatching { wrong.waitReady() }.isFailure }, "invalid exact episode must fail")
            assertEquals(TorrentSettlement.SETTLED, withContext(Dispatchers.IO) { wrong.cancelAndWait() })
            wrong.close()
            assertFalse(client.hasFailedSettlement())
            client.close()
            val undersized = TorrentClient.newNativeOwned(parent.resolve("capacity").path, (bytes - 1).toULong(), listOf(peer))
            val denied = undersized.beginSelected("magnet:?xt=urn:btih:$hash", hash, 1u, null, 30_000u)
            assertTrue(withContext(Dispatchers.IO) { runCatching { denied.waitReady() }.isFailure }, "full torrent payload must be charged before selected storage")
            assertEquals(TorrentSettlement.SETTLED, withContext(Dispatchers.IO) { denied.cancelAndWait() })
            denied.close()
            assertFalse(undersized.hasFailedSettlement())
            undersized.close()
            settled = true
        } finally {
            fixtureControl(config, JSONObject().put("hold_metadata", false))
            // Retain a failed native owner rather than deleting a live owned payload.
            if (settled) parent.deleteRecursively()
        }
    }

    @Test fun aExactOwnedEpisodesDecodeTracksRenewAndJoinIndependentGrants() = runBlocking {
        val config = configuration()
        val origin = config.getString("origin")
        assertEquals("https", java.net.URI(origin).scheme)
        assertEquals("127.0.0.1", java.net.URI(origin).host)
        val peer = config.getString("peer")
        assertTrue(peer.matches(Regex("127\\.0\\.0\\.1:[0-9]+")))
        val jobs = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val player = AndroidMedia3BackendFactory(context).createAndroidPlayer()
        val gateway = VipTvHttpGateway(origin, config.getString("access_token"), television = true)
        // The real backend middleware and admitted profile must accept the fixture bearer.
        assertNotNull(gateway.foregroundIdentity())
        val parent = java.io.File(context.noBackupFilesDir, "owned-qualification-${UUID.randomUUID()}").apply { mkdirs() }
        val cache = NativeTorrentCache.open(parent, { directory, capacity ->
            NativeTorrentEngineCacheManager(TorrentClient.newNativeOwned(directory.path, capacity.toULong(), listOf(peer)))
        }, nowNanos = SystemClock::elapsedRealtimeNanos)
        val coordinator = NativeTorrentCoordinator(cache, SystemClock::elapsedRealtimeNanos, { true }, { player.stop() })
        var activity: OwnedNativeFixtureActivity? = null
        var active: NativePlaybackControl? = null
        var invalidations = 0
        fun control(generation: Long): NativePlaybackControl {
            lateinit var owner: NativePlaybackControl
            owner = gateway.nativePlaybackControl("owned_scope", generation, cache, jobs, {
                if (active === owner) player.stop()
                if (owner.isLocallyRetired()) coordinator.cancelNative(owner)
            }, { invalidations++ })
            return owner
        }
        val started = SystemClock.elapsedRealtime()
        try {
            val fixtureActivity = instrumentation.startActivitySync(Intent(context, OwnedNativeFixtureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as OwnedNativeFixtureActivity
            activity = fixtureActivity
            await { fixtureActivity.texture.isAvailable }
            withContext(Dispatchers.Main.immediate) { player.attach(fixtureActivity.texture) }
            val source = config.getJSONArray("sources").getJSONObject(0).getString("stream_id")
            val first = control(1)
            coordinator.ownControl(first, 1)
            assertIs<NativePlaybackStart.Native>(first.start(request(source), true, true, cache))
            val firstAccepted = assertNotNull(first.firstGrantAcceptedAtMillis())
            val candidate = coordinator.prepare(first, 1)
            val uri = java.net.URI(candidate.capability.url)
            val segments = uri.rawPath.split('/')
            val wrongToken = "http://127.0.0.1:${uri.port}/${"b".repeat(64)}/${segments[2]}/${segments[3]}"
            val unselected = "http://127.0.0.1:${uri.port}/${segments[1]}/2/${segments[3]}"
            assertTrue(withContext(Dispatchers.IO) { denied(wrongToken) })
            assertTrue(withContext(Dispatchers.IO) { denied(unselected) })
            active = first
            coordinator.accept(candidate) { player.open(PlaybackSource(it.url, emptyMap()), playWhenReady = true) }
            await { player.state.value.isPlaying && player.state.value.positionMillis > 1000 }
            assertTrue(SystemClock.elapsedRealtime() - firstAccepted <= 30_000, "accepted grant through actual player readiness exceeds startup budget")
            await { player.audioTracks.value.size >= 2 && player.subtitleTracks.value.size >= 2 }
            val audio = player.audioTracks.value.first { it.language in listOf("spa", "es") }
            val subtitle = player.subtitleTracks.value.first { it.language in listOf("eng", "en") }
            withContext(Dispatchers.Main.immediate) {
                val audioResult = player.selectAudioTrack(audio.id)
                val subtitleResult = player.selectSubtitleTrack(subtitle.id)
                assertTrue(audioResult is TrackSelectionResult.Requested || audioResult is TrackSelectionResult.Selected)
                assertTrue(subtitleResult is TrackSelectionResult.Requested || subtitleResult is TrackSelectionResult.Selected)
                player.seekTo(1000)
            }
            await { player.subtitleCues.value.any { it.text == "Owned English cue" } }
            await { player.state.value.selectedAudioTrackId == audio.id && player.state.value.selectedSubtitleTrackId == subtitle.id }
            val pixels = withContext(Dispatchers.Main.immediate) {
                val bitmap = assertNotNull(fixtureActivity.texture.bitmap, "actual decoded surface unavailable")
                IntArray(64).also { samples -> for (i in samples.indices) samples[i] = bitmap.getPixel((i % 8) * (bitmap.width - 1) / 7, (i / 8) * (bitmap.height - 1) / 7) }
                    .also { bitmap.recycle() }
            }
            assertTrue(pixels.distinct().size > 8, "decoded surface must contain the owned test pattern")
            player.pause()
            val grant = first.playbackId()
            first.renew()
            assertEquals(grant, first.playbackId())
            coordinator.background()
            assertNotNull(coordinator.foreground())

            val second = control(2)
            coordinator.ownControl(second, 2)
            assertIs<NativePlaybackStart.Native>(second.start(request(source), true, true, cache))
            val independent = coordinator.prepare(second, 2)
            val before = withContext(Dispatchers.IO) { bytes(independent.capability.url, 0, 64) }
            val prefixHash = java.security.MessageDigest.getInstance("SHA-256").digest(before).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(config.getJSONArray("files").getJSONObject(1).getString("prefix_sha256"), prefixHash, "actual selected bytes differ from owned episode")
            val selectedFixture = config.getJSONArray("files").getJSONObject(1)
            val middle = withContext(Dispatchers.IO) { bytes(independent.capability.url, selectedFixture.getLong("sample_offset"), 64) }
            val middleHash = java.security.MessageDigest.getInstance("SHA-256").digest(middle).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(selectedFixture.getString("sample_sha256"), middleHash, "selected episode payload must match exact owned bytes")
            val shutdownAt = SystemClock.elapsedRealtimeNanos()
            val localJoined = withContext(Dispatchers.Main.immediate) {
                player.stop()
                first.retireLocal()
                candidate.work.cancel()
                val deadline = shutdownAt + 2_000_000_000
                withContext(Dispatchers.IO) { cache.retire(candidate.work, deadline) && first.joinLocal(deadline) }
            }
            val shutdownMillis = (SystemClock.elapsedRealtimeNanos() - shutdownAt) / 1_000_000
            assertTrue(localJoined && shutdownMillis <= 2000, "real joined local work exceeded common cancellation bound")
            assertTrue(withContext(Dispatchers.IO) { denied(candidate.capability.url) })
            assertContentEquals(before, withContext(Dispatchers.IO) { bytes(independent.capability.url, 0, 64) })
            assertTrue(cache.isAvailable)
            assertTrue(coordinator.retire(candidate))
            assertTrue(coordinator.retire(independent))
            assertTrue(coordinator.closeScope())
            assertEquals(0L, cache.reservedControlBytes)
            assertEquals(0L, cache.heldPayloadCapacityBytes)
            val evidence = JSONObject().put("actual_jni", true).put("decoded_surface", true).put("cue", true)
                .put("alternate_audio", true).put("independent_grant", true).put("joined_local_millis", shutdownMillis)
                .put("elapsed_millis", SystemClock.elapsedRealtime() - started).put("invalidations", invalidations)
            java.io.File(context.noBackupFilesDir, "owned-native-evidence.json").writeText(evidence.toString())
        } finally {
            withContext(NonCancellable) { runCatching { coordinator.closeScope() } }
            player.close()
            jobs.cancel()
            instrumentation.runOnMainSync { activity?.finish() }
            if (cache.heldPayloadCapacityBytes == 0L) parent.deleteRecursively()
        }
    }

    @Test fun cRealMissingPieceBodyCancellationJoinsWithoutRetiringIndependentReader() = runBlocking {
        val config = configuration()
        val files = config.getJSONArray("files")
        val selectedSize = files.getJSONObject(1).getLong("bytes")
        val offsetInPayload = files.getJSONObject(0).getLong("bytes")
        val pieceLength = config.getLong("piece_length")
        val heldPieces = JSONArray()
        for (piece in (offsetInPayload + selectedSize / 2) / pieceLength..(offsetInPayload + selectedSize - 1) / pieceLength) heldPieces.put(piece)
        fixtureControl(config, JSONObject().put("held_pieces", heldPieces))
        val parent = java.io.File(context.noBackupFilesDir, "owned-piecewait-${UUID.randomUUID()}").apply { mkdirs() }
        val client = TorrentClient.newNativeOwned(parent.path, config.getLong("payload_bytes").toULong(), listOf(config.getString("peer")))
        val player = AndroidMedia3BackendFactory(context).createAndroidPlayer()
        val activity = instrumentation.startActivitySync(Intent(context, OwnedNativeFixtureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as OwnedNativeFixtureActivity
        var settled = false
        try {
            fun begin() = client.beginSelected("magnet:?xt=urn:btih:${config.getString("info_hash")}", config.getString("info_hash"), 1u, null, 30_000u)
            val first = begin()
            val second = begin()
            val handle = withContext(Dispatchers.IO) { first.waitReady() }
            val independent = withContext(Dispatchers.IO) { second.waitReady() }
            val url = handle.streamUrl(1u)
            assertEquals(64, withContext(Dispatchers.IO) { bytes(url, 0, 64) }.size)
            await { activity.texture.isAvailable }
            withContext(Dispatchers.Main.immediate) { player.attach(activity.texture); player.open(PlaybackSource(url), playWhenReady = true) }
            await { player.state.value.isPlaying && player.state.value.positionMillis > 500 }
            withContext(Dispatchers.Main.immediate) { assertTrue(player.seekTo(35_000)) }
            await { player.state.value.isBuffering }
            val blocked = async(Dispatchers.IO) { runCatching { bytes(url, selectedSize - 64, 64) } }
            delay(750)
            assertFalse(blocked.isCompleted, "held real piece must keep an actual HTTP body pending")
            val start = SystemClock.elapsedRealtimeNanos()
            withContext(Dispatchers.Main.immediate) { player.stop() }
            handle.stop()
            first.cancel()
            val handleJoin = async(Dispatchers.IO) { handle.stopAndWait() }
            val acquisitionJoin = async(Dispatchers.IO) { first.cancelAndWait() }
            assertEquals(TorrentSettlement.SETTLED, handleJoin.await())
            assertEquals(TorrentSettlement.SETTLED, acquisitionJoin.await())
            assertTrue(withTimeout(2000) { blocked.await() }.isFailure, "retired body must not deliver selected bytes")
            assertTrue(SystemClock.elapsedRealtimeNanos() - start <= 2_000_000_000, "HTTP/body/native reader quiescence exceeds bound")
            assertTrue(withContext(Dispatchers.IO) { denied(url) })
            assertEquals(64, withContext(Dispatchers.IO) { bytes(independent.streamUrl(1u), 0, 64) }.size)
            first.close(); handle.close()
            second.cancel(); independent.stop()
            val secondJoin = async(Dispatchers.IO) { second.cancelAndWait() }
            val independentJoin = async(Dispatchers.IO) { independent.stopAndWait() }
            assertEquals(TorrentSettlement.SETTLED, secondJoin.await())
            assertEquals(TorrentSettlement.SETTLED, independentJoin.await())
            second.close(); independent.close()
            assertFalse(client.hasFailedSettlement())
            client.close()
            settled = true
        } finally {
            fixtureControl(config, JSONObject().put("held_pieces", JSONArray()))
            player.close()
            instrumentation.runOnMainSync { activity.finish() }
            if (settled) parent.deleteRecursively()
        }
    }

    @Test fun dActualPausedHeartbeatAndElapsedExpiryStopNativeAuthority() = runBlocking {
        val config = configuration()
        val parent = java.io.File(context.noBackupFilesDir, "owned-expiry-${UUID.randomUUID()}").apply { mkdirs() }
        val jobs = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val cache = NativeTorrentCache.open(parent, { directory, capacity -> NativeTorrentEngineCacheManager(
            TorrentClient.newNativeOwned(directory.path, capacity.toULong(), listOf(config.getString("peer")))) }, nowNanos = SystemClock::elapsedRealtimeNanos)
        val coordinator = NativeTorrentCoordinator(cache, SystemClock::elapsedRealtimeNanos, { true }, {})
        val gateway = VipTvHttpGateway(config.getString("origin"), config.getString("access_token"), television = true)
        val retiredAt = java.util.concurrent.atomic.AtomicLong()
        lateinit var control: NativePlaybackControl
        control = gateway.nativePlaybackControl("owned_expiry", 1, cache, jobs, {
            if (control.isLocallyRetired()) {
                retiredAt.compareAndSet(0, SystemClock.elapsedRealtimeNanos())
                coordinator.cancelNative(control)
            }
        }, {})
        try {
            coordinator.ownControl(control, 1)
            assertIs<NativePlaybackStart.Native>(control.start(request(config.getJSONArray("sources").getJSONObject(0).getString("stream_id")), true, true, cache))
            val candidate = coordinator.prepare(control, 1)
            val deadline = assertNotNull(control.state().deadlineMillis)
            val playback = control.playbackId()
            control.background()
            // Observe actual scheduled renewal while preparing/backgrounded, without faking clocks.
            await(27_000) { assertNotNull(control.state().deadlineMillis) > deadline }
            assertEquals(playback, control.playbackId())
            assertFalse(control.isLocallyRetired())
            // Test-only executor fault: cease heartbeat delivery while leaving the
            // actual expiry job, clock, backend authority and native owner intact.
            val heartbeat = NativePlaybackControl::class.java.getDeclaredField("heartbeat").apply { isAccessible = true }.get(control) as Job
            heartbeat.cancelAndJoin()
            val finalDeadline = assertNotNull(control.state().deadlineMillis)
            await(65_000) { control.isLocallyRetired() }
            assertTrue(SystemClock.elapsedRealtime() >= finalDeadline, "expiry must use the actual elapsed deadline")
            val cancellation = retiredAt.get()
            assertTrue(cancellation > 0)
            val commonDeadline = cancellation + 2_000_000_000
            assertTrue(withContext(Dispatchers.IO) { cache.retire(candidate.work, commonDeadline) && control.joinLocal(commonDeadline) })
            assertTrue(SystemClock.elapsedRealtimeNanos() <= commonDeadline)
            val joinedMillis = (SystemClock.elapsedRealtimeNanos() - cancellation) / 1_000_000
            assertTrue(withContext(Dispatchers.IO) { denied(candidate.capability.url) })
            assertTrue(coordinator.retire(candidate))
            assertTrue(coordinator.closeScope())
            java.io.File(context.noBackupFilesDir, "owned-expiry-evidence.json").writeText(JSONObject()
                .put("actual_scheduled_heartbeat", true).put("actual_elapsed_expiry", true)
                .put("heartbeat_executor_fault_injected", true).put("joined_local_millis", joinedMillis).toString())
        } finally {
            withContext(NonCancellable) { runCatching { coordinator.closeScope() } }
            jobs.cancel()
            if (cache.heldPayloadCapacityBytes == 0L) parent.deleteRecursively()
        }
    }

    @Test fun eActualHttpHlsAndOlderServerKeepOrdinaryLeases() = runBlocking {
        val config = configuration()
        val jobs = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val player = AndroidMedia3BackendFactory(context).createAndroidPlayer()
        val gateway = VipTvHttpGateway(config.getString("origin"), config.getString("access_token"), television = true)
        val parent = java.io.File(context.noBackupFilesDir, "owned-ordinary-${UUID.randomUUID()}").apply { mkdirs() }
        val cache = NativeTorrentCache.open(parent, { directory, capacity -> NativeTorrentEngineCacheManager(
            TorrentClient.newNativeOwned(directory.path, capacity.toULong(), listOf(config.getString("peer")))) }, nowNanos = SystemClock::elapsedRealtimeNanos)
        val coordinator = NativeTorrentCoordinator(cache, SystemClock::elapsedRealtimeNanos, { true }, { player.stop() })
        val activity = instrumentation.startActivitySync(Intent(context, OwnedNativeFixtureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as OwnedNativeFixtureActivity
        try {
            await { activity.texture.isAvailable }
            withContext(Dispatchers.Main.immediate) { player.attach(activity.texture) }
            for ((qualified, olderServer) in listOf(false to false, true to false, true to true)) {
                fixtureControl(config, JSONObject().put("legacy_protocol", olderServer))
                val sources = config.getJSONArray("ordinary_sources")
                for (index in 0 until sources.length()) {
                    val control = gateway.nativePlaybackControl("owned_ordinary", 1, cache, jobs, {}, {})
                    coordinator.ownControl(control, 1)
                    val legacy = assertIs<NativePlaybackStart.Legacy>(control.start(request(sources.getJSONObject(index).getString("stream_id")), qualified = qualified, vod = true, cache = cache))
                    val launch = legacy.launch
                    assertEquals("direct", launch.mode)
                    assertEquals(emptyMap(), launch.headers)
                    // Local native cleanup must not delete the adopted ordinary lease.
                    assertTrue(coordinator.retireControl(control))
                    assertNotNull(gateway.playbackRemainingMillis(launch.sessionId))
                    withContext(Dispatchers.Main.immediate) { player.open(PlaybackSource(launch.url, headers = launch.headers), playWhenReady = true) }
                    await { player.state.value.isPlaying && player.state.value.positionMillis > 500 }
                    withContext(Dispatchers.Main.immediate) { player.pause(); assertTrue(player.seekTo(5000)); player.play() }
                    await { player.state.value.isPlaying && player.state.value.positionMillis > 5000 }
                    withContext(Dispatchers.Main.immediate) { player.stop() }
                    gateway.stopPlayback(launch.sessionId)
                }
            }
            assertTrue(coordinator.closeScope())
            java.io.File(context.noBackupFilesDir, "owned-ordinary-evidence.json").writeText(JSONObject()
                .put("http_and_hls_decoded", true).put("old_server_404_legacy", true)
                .put("unqualified_client_ordinary", true).put("adopted_ordinary_lease_preserved", true).toString())
        } finally {
            fixtureControl(config, JSONObject().put("legacy_protocol", false))
            withContext(NonCancellable) { runCatching { coordinator.closeScope() } }
            player.close(); jobs.cancel()
            instrumentation.runOnMainSync { activity.finish() }
            if (cache.heldPayloadCapacityBytes == 0L) parent.deleteRecursively()
        }
    }

    @Test fun manualOwnedNativeObservation() = runBlocking {
        val seconds = InstrumentationRegistry.getArguments().getString("ownedNativeManualSeconds")?.toIntOrNull()
        org.junit.Assume.assumeTrue("explicit bounded human observation argument required", seconds != null)
        val observationSeconds = requireNotNull(seconds)
        require(observationSeconds in 180..300) { "ownedNativeManualSeconds must be 180..300" }
        val config = configuration()
        assertTrue(context.packageName.endsWith(".nativefixture"))
        ServerOrigin.save(context, config.getString("origin"))
        val store = context.getSharedPreferences("viptv.auth", android.content.Context.MODE_PRIVATE)
        assertTrue(store.edit().clear().putString("core.session", config.getJSONObject("core_session").toString())
            .putString("access", config.getString("access_token")).putString("refresh", "owned-unused-refresh").commit())
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val controller = withContext(Dispatchers.Main.immediate) { androidx.lifecycle.ViewModelProvider(activity)[ViptvModel::class.java].controller }
        val parent = java.io.File(context.noBackupFilesDir, "owned-manual-${UUID.randomUUID()}").apply { mkdirs() }
        controller.nativePlaybackQualified = { true }
        controller.nativeScopeOwnerOverride = NativeTorrentScopeOwner({
            NativeTorrentCache.open(parent, { directory, capacity -> NativeTorrentEngineCacheManager(
                TorrentClient.newNativeOwned(directory.path, capacity.toULong(), listOf(config.getString("peer")))) }, nowNanos = SystemClock::elapsedRealtimeNanos)
        }, controller::createNativeCoordinator)
        try {
            await { controller.state.value.selectedProfile?.id == "1" && !controller.state.value.loading && controller.nativeAuthorizationFacts() != null }
            val media = Media("owned_episode_1", "series", "Owned episode", seriesId = "owned_series", season = 1, episode = 1)
            val source = Source(config.getJSONArray("sources").getJSONObject(0).getString("stream_id"), "Owned episodes", displayResolved = true)
            withContext(Dispatchers.Main.immediate) {
                controller._state.value = controller.state.value.copy(route = Route.Sources(media), sources = listOf(source))
                controller.start(media, source)
            }
            await(30_000) { controller.state.value.route is Route.Player && controller.player.state.value.isPlaying }
            val began = SystemClock.elapsedRealtime()
            var testOnlyLoops = 0
            while (SystemClock.elapsedRealtime() - began < observationSeconds * 1000L) {
                // Keep the short owned clip available for observation without reopening a lease.
                // Human Back/pause remain authoritative; the fixture never restarts a Sources route.
                if (controller.state.value.route is Route.Player && controller.player.state.value.isPlaying && controller.player.state.value.positionMillis >= 38_000) {
                    withContext(Dispatchers.Main.immediate) { controller.player.seekTo(0) }
                    testOnlyLoops++
                }
                delay(100)
            }
            java.io.File(context.noBackupFilesDir, "owned-manual-evidence.json").writeText(JSONObject()
                .put("actual_main_activity", true).put("observation_seconds", seconds).put("test_only_same_title_loops", testOnlyLoops)
                .put("final_route", controller.state.value.route::class.simpleName)
                .put("physical_sound", "human_verdict_required").put("remote_focus", "human_verdict_required").toString())
        } finally {
            withContext(Dispatchers.Main.immediate) { controller.close() }
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun zActualControllerReturnsSourcePositionAndClosesProfileScope() = runBlocking {
        val config = configuration()
        // The package suffix owns these preferences. No normal app session is read or changed.
        assertTrue(context.packageName.endsWith(".nativefixture"))
        val store = context.getSharedPreferences("viptv.auth", android.content.Context.MODE_PRIVATE)
        assertTrue(store.edit().clear().putString("core.session", config.getJSONObject("core_session").toString())
            .putString("access", config.getString("access_token")).putString("refresh", "owned-unused-refresh").commit())
        val parent = java.io.File(context.noBackupFilesDir, "owned-controller-${UUID.randomUUID()}").apply { mkdirs() }
        val controller = withContext(Dispatchers.Main.immediate) { AppController(context, config.getString("origin")) }
        controller.nativePlaybackQualified = { true }
        controller.nativeScopeOwnerOverride = NativeTorrentScopeOwner({
            NativeTorrentCache.open(parent, { directory, capacity ->
                NativeTorrentEngineCacheManager(TorrentClient.newNativeOwned(directory.path, capacity.toULong(), listOf(config.getString("peer"))))
            }, nowNanos = SystemClock::elapsedRealtimeNanos)
        }, controller::createNativeCoordinator)
        val activity = instrumentation.startActivitySync(Intent(context, OwnedNativeFixtureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as OwnedNativeFixtureActivity
        try {
            await { controller.state.value.selectedProfile?.id == "1" && !controller.state.value.loading && controller.nativeAuthorizationFacts() != null }
            await { activity.texture.isAvailable }
            withContext(Dispatchers.Main.immediate) { controller.player.attach(activity.texture) }
            val media = Media("owned_episode_1", "series", "Owned episode", seriesId = "owned_series", season = 1, episode = 1)
            val source = Source(config.getJSONArray("sources").getJSONObject(0).getString("stream_id"), "Owned episodes", displayResolved = true)
            withContext(Dispatchers.Main.immediate) {
                controller._state.value = controller.state.value.copy(route = Route.Sources(media), sources = listOf(source))
                controller.start(media, source)
            }
            await(30_000) { controller.state.value.route is Route.Player && controller.player.state.value.isPlaying }
            val epoch = assertNotNull(controller.nativePlaybackEpoch)
            val candidate = assertNotNull(epoch.coordinator.authorizeActive())
            assertFalse(controller.state.value.toString().contains(candidate.capability.url))
            assertFalse(store.all.toString().contains(candidate.capability.url))
            withContext(Dispatchers.Main.immediate) { controller.player.seekTo(8000) }
            await { controller.player.state.value.isPlaying && controller.player.state.value.positionMillis > 8000 }
            withContext(Dispatchers.Main.immediate) { controller.exitPlayback() }
            await { controller.state.value.route !is Route.Player }
            // Actual controller snapshot retains title time on the return route.
            val returned = controller.state.value.route
            val position = when (returned) { is Route.Sources -> returned.media.positionMillis; is Route.Details -> returned.media.positionMillis; else -> -1 }
            assertTrue(position >= 8000, "return route must retain the native title position")
            await { candidate.retired }
            assertTrue(withContext(Dispatchers.IO) { denied(candidate.capability.url) })
            withContext(Dispatchers.Main.immediate) { controller.start(media.copy(positionMillis = position), source, explicitResume = true) }
            await(30_000) { controller.state.value.route is Route.Player && controller.player.state.value.isPlaying }
            val revoked = assertNotNull(epoch.coordinator.authorizeActive())
            withContext(Dispatchers.Main.immediate) { controller.player.pause() }
            fixtureControl(config, JSONObject().put("disable_source", true))
            await(27_000) { revoked.control.isLocallyRetired() }
            assertTrue(withTimeout(5000) { revoked.retirement.await() }, "paused producer revocation must settle local native work")
            assertTrue(withContext(Dispatchers.IO) { denied(revoked.capability.url) })
            assertFalse(controller.player.state.value.isPlaying)
            assertTrue(epoch.coordinator.cache.isAvailable, "per-grant producer refusal must not invent an authorization epoch")
            withContext(Dispatchers.Main.immediate) { controller.chooseProfile(controller.state.value.profiles.first { it.id == "2" }) }
            await { controller.state.value.selectedProfile?.id == "2" }
            await { epoch.coordinator.cache.heldPayloadCapacityBytes == 0L }
            assertFalse(controller.nativePlaybackQualified.invoke() && controller.nativePlaybackEpoch === epoch)
            controller.signOut().join()
            await { store.getString("access", null) == null }
            val refused = runCatching { VipTvHttpGateway(config.getString("origin"), config.getString("access_token"), television = true).foregroundIdentity() }.exceptionOrNull()
            assertTrue(refused is GatewayError && refused.status == 401, "signed-out fixture session must lose backend authority")
            java.io.File(context.noBackupFilesDir, "owned-controller-evidence.json").writeText(JSONObject()
                .put("actual_identity", true).put("actual_controller", true).put("returned_position", position)
                .put("paused_producer_revocation", true).put("profile_scope_closed", true).put("actual_signout_revoked", true).put("state_and_auth_preferences_native_secret_free", true).toString())
        } finally {
            withContext(Dispatchers.Main.immediate) { controller.close() }
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
