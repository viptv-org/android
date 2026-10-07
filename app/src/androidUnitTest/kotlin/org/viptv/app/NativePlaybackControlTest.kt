package org.viptv.app

import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.*
import kotlinx.coroutines.*
import org.json.JSONObject

/** Real loopback HTTP fixtures test byte/deadline behavior; they claim no Android TLS/device qualification. */
class NativePlaybackControlTest {
    private fun cache(): NativeTorrentCache = NativeTorrentCache.open(Files.createTempDirectory("native-control-").toFile(), { _, _ ->
        object : NativeTorrentCacheManager {
            override fun hasFailedSettlement() = false
            override fun closeAfterSettlement() {}
        }
    })
    private fun corpus() = JSONObject(File(requireNotNull(System.getProperty("viptv.core.nativeVectors"))).readText())
    private fun input() = corpus().getJSONObject("context").getJSONObject("request").also { it.getJSONObject("client").put("canPlayDirect", true) }
    private fun ready() = corpus().getJSONArray("cases").getJSONObject(0).getJSONArray("steps").getJSONObject(0).getString("body")
    private fun legacy(calls: MutableList<JSONObject> = mutableListOf()) = V2PlaybackControl("https://fixture.invalid") { method, _, body ->
        if (method == "DELETE") JSONObject() else {
            body?.let { calls += JSONObject(it.toString()) }
            JSONObject("""{"id":"legacy_fixture","status":"ready","delivery":{"kind":"direct","url":"https://fixture.invalid/media.mp4","headers":{},"format":"original","position":120,"live":false},"error_code":null,"error":null,"expires_at":${System.currentTimeMillis() / 1000 + 60},"renew_after_seconds":20}""")
        }
    }
    private fun control(server: NativeControlHttpFixture, cache: NativeTorrentCache, jobs: CoroutineScope,
        current: () -> Boolean = { true }, clock: NativePlaybackClock = NativePlaybackClock { 100_000 },
        legacy: V2PlaybackControl = legacy(), prevented: () -> Unit = {}, invalidated: () -> Unit = {}) =
        NativePlaybackControl("https://fixture.invalid", "scope_fixture", 7, current,
            NativePlaybackTransport(server.origin, { "fixture_bearer" }, current, cache), legacy, jobs, clock, prevented, invalidated)

    @Test fun negotiationFailuresKeepLegacyShapeAndNeverAdvertise() = runBlocking {
        for ((status, body) in listOf(404 to "{}", 405 to "{}", 200 to """{"version":1,"native_torrent_versions":[]}""",
            200 to """{"version":1,"native_torrent_versions":[1],"other":true}""", 200 to """{"version":1,"version":1,"native_torrent_versions":[1]}""", 200 to "malformed")) {
            val owner = cache()
            val calls = mutableListOf<JSONObject>()
            NativeControlHttpFixture { NativeHttpReply(body.toByteArray(), status) }.use { server ->
                val control = control(server, owner, this, legacy = legacy(calls))
                assertIs<NativePlaybackStart.Legacy>(control.start(input(), true, true, owner))
                assertFalse(calls.single().getJSONObject("client").has("native_torrent"))
                assertEquals("identity", server.requests.single().headers["accept-encoding"])
                assertEquals("/api/v2/playback-protocol", server.requests.single().target)
            }
            assertEquals(0, owner.reservedControlBytes)
            assertTrue(owner.closeScope())
        }
    }

    @Test fun authorizationRefusalAndScopeSwitchDoNotFallBack() = runBlocking {
        for (status in listOf(401, 403)) {
            val owner = cache()
            val calls = mutableListOf<JSONObject>()
            NativeControlHttpFixture { NativeHttpReply("{}".toByteArray(), status) }.use { server ->
                val control = control(server, owner, this, legacy = legacy(calls))
                assertEquals(403, assertFailsWith<GatewayError> { control.start(input(), true, true, owner) }.status)
                assertTrue(calls.isEmpty())
            }
            assertTrue(owner.closeScope())
        }
        val owner = cache()
        var current = true
        NativeControlHttpFixture { current = false; NativeHttpReply("""{"version":1,"native_torrent_versions":[1]}""".toByteArray()) }.use { server ->
            val calls = mutableListOf<JSONObject>()
            assertFailsWith<GatewayError> { control(server, owner, this, { current }, legacy = legacy(calls)).start(input(), true, true, owner) }
            assertTrue(calls.isEmpty())
        }
        assertTrue(owner.closeScope())
    }

    @Test fun transportBoundsBodiesRedirectsCompressionAndRequestSize() = runBlocking {
        val owner = cache()
        for (reply in listOf(NativeHttpReply(ByteArray(4_097)), NativeHttpReply("{}".toByteArray(), 302, mapOf("Location" to "http://127.0.0.1:1/private")),
            NativeHttpReply("{}".toByteArray(), headers = mapOf("Content-Encoding" to "gzip")))) {
            NativeControlHttpFixture { reply }.use { server ->
                val transport = NativePlaybackTransport(server.origin, { "fixture" }, { true }, owner)
                assertFailsWith<GatewayError> { transport.request("GET", "/v2/playback-protocol", negotiation = true) }
                assertEquals(1, server.requests.size)
            }
            assertEquals(0, owner.reservedControlBytes)
        }
        NativeControlHttpFixture { NativeHttpReply(ByteArray(6_291_457)) }.use { server ->
            val transport = NativePlaybackTransport(server.origin, { "fixture" }, { true }, owner)
            assertFailsWith<GatewayError> { transport.request("POST", "/v2/playback", ByteArray(16_385)) }
            assertEquals(0, server.requests.size)
            assertFailsWith<GatewayError> { transport.request("GET", "/v2/playback/id") }
        }
        val malformedOrigin = "http://fixture.invalid:invalidport"
        val failure = assertFailsWith<GatewayError> {
            NativePlaybackTransport(malformedOrigin, { "fixture" }, { true }, owner).request("GET", "/v2/playback-protocol", negotiation = true)
        }
        assertFalse(failure.message.contains(malformedOrigin))
        assertTrue(owner.isAvailable)
        assertEquals(0, owner.reservedControlBytes)
        assertTrue(owner.closeScope())
    }

    @Test fun negotiationAndControlTimeoutsCancelAndJoinBodies() = runBlocking {
        val owner = cache()
        for (negotiation in listOf(true, false)) {
            NativeControlHttpFixture { NativeHttpReply("{}".toByteArray(), delayMillis = if (negotiation) 5_500 else 10_500) }.use { server ->
                val transport = NativePlaybackTransport(server.origin, { "fixture" }, { true }, owner)
                val before = System.nanoTime()
                assertFailsWith<java.io.IOException> { transport.request("GET", "/v2/playback-protocol", negotiation = negotiation) }
                assertTrue((System.nanoTime() - before) / 1_000_000 < if (negotiation) 7_000 else 12_000)
                assertEquals(0, owner.reservedControlBytes)
            }
        }
        assertTrue(owner.closeScope())
    }

    @Test fun suspendExpiryUnavailableClockAndRegressingClockInvalidateReads() = runBlocking {
        for (next in listOf<Long?>(161_000, 99_999, null)) {
            val owner = cache()
            var now: Long? = 100_000
            var invalidations = 0
            NativeControlHttpFixture { request ->
                NativeHttpReply((if (request.target.endsWith("playback-protocol")) """{"version":1,"native_torrent_versions":[1]}""" else if (request.method == "DELETE") """{"ok":true}""" else ready()).toByteArray())
            }.use { server ->
                val control = control(server, owner, this, clock = NativePlaybackClock { now }, invalidated = { invalidations++ })
                assertIs<NativePlaybackStart.Native>(control.start(input(), true, true, owner))
                assertNotNull(control.remainingMillis())
                now = next
                assertFails { control.authorize() }
                assertEquals(1, invalidations)
                assertTrue(withContext(Dispatchers.IO) { control.joinLocal(System.nanoTime() + 2_000_000_000) })
                control.stop()
                assertNull(control.remainingMillis())
            }
            assertTrue(owner.closeScope())
        }
    }

    @Test fun foregroundRequiresCurrentSuccessfulBackendRenewal() = runBlocking {
        val owner = cache()
        var heartbeat = 0
        NativeControlHttpFixture { request ->
            val value = when {
                request.target.endsWith("playback-protocol") -> """{"version":1,"native_torrent_versions":[1]}"""
                request.method == "DELETE" -> """{"ok":true}"""
                request.target.endsWith("heartbeat") -> { heartbeat++; ready() }
                else -> ready()
            }
            NativeHttpReply(value.toByteArray())
        }.use { server ->
            val control = control(server, owner, this)
            control.start(input(), true, true, owner)
            control.background()
            assertFails { control.authorize() }
            control.foreground()
            assertEquals(1, heartbeat)
            assertNotNull(control.remainingMillis())
            control.stop()
            assertEquals(0, owner.reservedControlBytes)
        }
        assertTrue(owner.closeScope())
    }

    @Test fun nativeHeartbeatAndReleaseSendNoHttpBody() = runBlocking {
        val owner = cache()
        var now = 100_000L
        NativeControlHttpFixture { request ->
            when {
                request.target.endsWith("playback-protocol") -> NativeHttpReply("""{"version":1,"native_torrent_versions":[1]}""".toByteArray())
                request.method == "DELETE" -> NativeHttpReply("""{"ok":true}""".toByteArray())
                request.target.endsWith("heartbeat") -> if (request.body.isEmpty()) {
                    NativeHttpReply(ready().replace("1700000000", "1700000001").replace("1700000060", "1700000061").toByteArray())
                } else NativeHttpReply("{}".toByteArray(), 400)
                else -> NativeHttpReply(ready().toByteArray())
            }
        }.use { server ->
            val control = control(server, owner, this, clock = NativePlaybackClock { now })
            try {
                assertIs<NativePlaybackStart.Native>(control.start(input(), true, true, owner))
                assertTrue(server.requests.single { it.target == "/api/v2/playback" }.body.isNotEmpty())
                val initialDeadline = requireNotNull(control.state().deadlineMillis)
                now += 1_000
                val renewal = runCatching { control.renew() }
                val heartbeat = server.requests.single { it.target.endsWith("heartbeat") }
                assertEquals("", heartbeat.body)
                assertEquals("0", heartbeat.headers["content-length"])
                assertNull(heartbeat.headers["content-type"])
                renewal.getOrThrow()
                assertTrue(requireNotNull(control.state().deadlineMillis) > initialDeadline)
                assertNotNull(control.authorize())
                control.stop()
                assertEquals("", server.requests.single { it.method == "DELETE" }.body)
            } finally {
                control.stop()
            }
        }
        assertTrue(owner.closeScope())
    }

    @Test fun cancelBeforeResponseUsesRequestTombstoneAndCannotPublishLateGrant() = runBlocking {
        val owner = cache()
        val entered = CountDownLatch(1)
        NativeControlHttpFixture { request ->
            when {
                request.target.endsWith("playback-protocol") -> NativeHttpReply("""{"version":1,"native_torrent_versions":[1]}""".toByteArray())
                request.method == "DELETE" -> NativeHttpReply("""{"ok":true}""".toByteArray())
                else -> { entered.countDown(); NativeHttpReply(ready().toByteArray(), delayMillis = 400) }
            }
        }.use { server ->
            val control = control(server, owner, this)
            val start = async(Dispatchers.Default) { control.start(input(), true, true, owner) }
            assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
            start.cancelAndJoin()
            assertTrue(server.requests.any { it.method == "DELETE" && it.target == "/api/v2/playback-requests/request_fixture" && it.body.isEmpty() })
            assertNull(control.remainingMillis())
            assertEquals(0, owner.reservedControlBytes)
        }
        assertTrue(owner.closeScope())
    }

    @Test fun preparationAndIdlePausedControlKeepTwentySecondHeartbeats() = runBlocking {
        val owner = cache()
        val heartbeats = java.util.concurrent.atomic.AtomicInteger()
        val twice = CountDownLatch(2)
        NativeControlHttpFixture { request ->
            val value = when {
                request.target.endsWith("playback-protocol") -> """{"version":1,"native_torrent_versions":[1]}"""
                request.method == "DELETE" -> """{"ok":true}"""
                request.target.endsWith("heartbeat") -> {
                    heartbeats.incrementAndGet(); twice.countDown(); ready()
                }
                heartbeats.get() == 0 -> """{"id":"playback_fixture","status":"starting","delivery":null,"error_code":null,"error":null,"expires_at":1700000060,"renew_after_seconds":20}"""
                else -> ready()
            }
            NativeHttpReply(value.toByteArray())
        }.use { server ->
            val control = control(server, owner, this)
            withTimeout(45_000) {
                assertIs<NativePlaybackStart.Native>(control.start(input(), true, true, owner))
                // No player progress/transport activity is needed to retain the paused lease.
                assertTrue(withContext(Dispatchers.IO) { twice.await(23, TimeUnit.SECONDS) })
                assertTrue(heartbeats.get() >= 2)
                assertEquals(100_000, control.firstGrantAcceptedAtMillis())
                control.stop()
            }
            assertTrue(server.requests.any { it.target.endsWith("heartbeat") })
        }
        assertTrue(owner.closeScope())
    }

    @Test fun authenticationRefreshIsBoundedAndNeverReusesRejectedBearer() = runBlocking {
        val owner = cache()
        var refreshes = 0
        NativeControlHttpFixture { request ->
            NativeHttpReply("{}".toByteArray(), if (request.headers["authorization"] == "Bearer expired_fixture") 401 else 200)
        }.use { server ->
            val transport = NativePlaybackTransport(server.origin, { "expired_fixture" }, { true }, owner,
                { token -> assertEquals("expired_fixture", token); refreshes++; "fresh_fixture" })
            transport.request("GET", "/v2/playback-protocol", negotiation = true).use { assertEquals(200, it.status) }
            assertEquals(1, refreshes)
            assertEquals(listOf("Bearer expired_fixture", "Bearer fresh_fixture"), server.requests.map { it.headers["authorization"] })
            val failedRefresh = NativePlaybackTransport(server.origin, { "expired_fixture" }, { true }, owner,
                { throw java.io.IOException("Synthetic refresh failure") })
            assertEquals(401, assertFailsWith<GatewayError> { failedRefresh.request("GET", "/v2/playback-protocol", negotiation = true) }.status)
        }
        assertTrue(owner.closeScope())
    }

    @Test fun failedNetworkRenewalKeepsDeadlineAndOlderPollCannotRegressRenewal() = runBlocking {
        val owner = cache()
        var heartbeatCalls = 0
        val renewalEntered = CountDownLatch(1)
        NativeControlHttpFixture { request ->
            when {
                request.target.endsWith("playback-protocol") -> NativeHttpReply("""{"version":1,"native_torrent_versions":[1]}""".toByteArray())
                request.method == "DELETE" -> NativeHttpReply("""{"ok":true}""".toByteArray())
                request.target.endsWith("heartbeat") -> {
                    heartbeatCalls++
                    if (heartbeatCalls == 1) throw java.io.IOException("Synthetic disconnected fixture")
                    renewalEntered.countDown()
                    NativeHttpReply(ready().replace("1700000000", "1700000001").replace("1700000060", "1700000061").toByteArray(), delayMillis = 300)
                }
                else -> NativeHttpReply(ready().toByteArray())
            }
        }.use { server ->
            val control = control(server, owner, this)
            control.start(input(), true, true, owner)
            val deadline = control.state().deadlineMillis
            assertFailsWith<java.io.IOException> { control.renew() }
            assertEquals(deadline, control.state().deadlineMillis)
            assertNotNull(control.remainingMillis())
            val renewal = async(Dispatchers.Default) { control.renew() }
            assertTrue(withContext(Dispatchers.IO) { renewalEntered.await(5, TimeUnit.SECONDS) })
            val poll = async(Dispatchers.Default) { runCatching { control.poll() } }
            renewal.await()
            assertTrue(poll.await().isFailure)
            assertNull(control.remainingMillis())
            assertEquals(listOf("POST", "GET"), server.requests.takeLast(2).map { it.method })
            control.stop()
        }
        assertTrue(owner.closeScope())
    }

    @Test fun repeatedRetirementCancelsRacingRemoteReleaseBeforeScopeJoin() = runBlocking {
        val owner = cache()
        val releaseEntered = CountDownLatch(1)
        NativeControlHttpFixture { request ->
            when {
                request.target.endsWith("playback-protocol") -> NativeHttpReply("""{"version":1,"native_torrent_versions":[1]}""".toByteArray())
                request.method == "DELETE" -> { releaseEntered.countDown(); NativeHttpReply("""{"ok":true}""".toByteArray(), delayMillis = 5_000) }
                else -> NativeHttpReply(ready().toByteArray())
            }
        }.use { server ->
            val control = control(server, owner, this)
            control.start(input(), true, true, owner)
            control.retireLocal()
            val release = async(Dispatchers.Default) { runCatching { control.releaseRemote() } }
            assertTrue(withContext(Dispatchers.IO) { releaseEntered.await(5, TimeUnit.SECONDS) })
            control.retireLocal()
            assertTrue(withContext(Dispatchers.IO) { control.joinLocal(System.nanoTime() + 2_000_000_000) })
            assertTrue(release.await().isFailure)
            control.closeAfterSettlement()
            assertEquals(0, owner.reservedControlBytes)
        }
        assertTrue(owner.closeScope())
    }

    @Test fun receiptSamplingCannotBeOvertakenByConcurrentReadAuthorization() = runBlocking {
        val owner = cache()
        val entered = CountDownLatch(1)
        val pauseReceipt = java.util.concurrent.atomic.AtomicBoolean()
        val samples = java.util.concurrent.atomic.AtomicLong(100_000)
        val receiptThread = java.util.concurrent.atomic.AtomicReference<Thread>()
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
            Thread(task, "native-receipt-test").also { receiptThread.set(it) }
        }
        val dispatcher = executor.asCoroutineDispatcher()
        try { NativeControlHttpFixture { request ->
            val value = when {
                request.target.endsWith("playback-protocol") -> """{"version":1,"native_torrent_versions":[1]}"""
                request.method == "DELETE" -> """{"ok":true}"""
                else -> { if (request.target.endsWith("heartbeat")) pauseReceipt.set(true); ready() }
            }
            NativeHttpReply(value.toByteArray())
        }.use { server ->
            val clock = NativePlaybackClock {
                val sample = samples.incrementAndGet()
                if (Thread.currentThread() === receiptThread.get() && pauseReceipt.compareAndSet(true, false)) {
                    entered.countDown()
                    Thread.sleep(150)
                }
                sample
            }
            val control = control(server, owner, this, clock = clock)
            control.start(input(), true, true, owner)
            val renewal = async(dispatcher) { control.renew() }
            assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
            assertNotNull(withContext(Dispatchers.Default) { control.authorize() })
            renewal.await()
            assertNotNull(control.remainingMillis())
            control.stop()
        } } finally { dispatcher.close() }
        assertTrue(owner.closeScope())
    }

    @Test fun terminalRenewalInvalidatesPausedWorkWithoutWaitingForAnotherRead() = runBlocking {
        val owner = cache()
        var invalidations = 0
        NativeControlHttpFixture { request ->
            val value = when {
                request.target.endsWith("playback-protocol") -> """{"version":1,"native_torrent_versions":[1]}"""
                request.method == "DELETE" -> """{"ok":true}"""
                request.target.endsWith("heartbeat") -> JSONObject(ready()).put("status", "failed").put("delivery", JSONObject.NULL)
                    .put("error_code", "producer_unavailable").put("error", "Synthetic producer refusal").toString()
                else -> ready()
            }
            NativeHttpReply(value.toByteArray())
        }.use { server ->
            val control = control(server, owner, this, invalidated = { invalidations++ })
            control.start(input(), true, true, owner)
            assertFailsWith<GatewayError> { control.renew() }
            assertEquals(1, invalidations)
            control.stop()
        }
        assertTrue(owner.closeScope())
    }
}

private class NativeHttpReply(val bytes: ByteArray, val status: Int = 200, val headers: Map<String, String> = emptyMap(), val delayMillis: Long = 0)
private class NativeControlHttpFixture(private val reply: (FixtureRequest) -> NativeHttpReply) : AutoCloseable {
    private val listener = ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1"))
    val origin = "http://127.0.0.1:${listener.localPort}"
    val requests = Collections.synchronizedList(mutableListOf<FixtureRequest>())
    private val sockets = Collections.synchronizedList(mutableListOf<Socket>())
    private val worker = thread(isDaemon = true) {
        while (!listener.isClosed) try {
            val connection = listener.accept()
            sockets += connection
            thread(isDaemon = true) { runCatching { handle(connection) } }
        } catch (_: Exception) { break }
    }
    private fun handle(socket: Socket) = socket.use {
        val input = it.getInputStream().bufferedReader()
        val line = input.readLine().split(' ')
        val headers = mutableMapOf<String, String>()
        while (true) {
            val header = input.readLine()
            if (header.isEmpty()) break
            val parts = header.split(':', limit = 2)
            headers[parts[0].lowercase()] = parts[1].trim()
        }
        val chars = CharArray(headers["content-length"]?.toInt() ?: 0)
        var offset = 0
        while (offset < chars.size) { val n = input.read(chars, offset, chars.size - offset); if (n < 0) break; offset += n }
        val request = FixtureRequest(line[0], line[1], chars.concatToString(), headers)
        requests += request
        val response = reply(request)
        Thread.sleep(response.delayMillis)
        val output = it.getOutputStream()
        output.write(("HTTP/1.1 ${response.status} OK\r\nContent-Length: ${response.bytes.size}\r\nConnection: close\r\n" +
            response.headers.entries.joinToString("") { (key, value) -> "$key: $value\r\n" } + "\r\n").toByteArray())
        output.write(response.bytes)
        output.flush()
    }
    override fun close() { listener.close(); sockets.toList().forEach { runCatching { it.close() } }; worker.join(500) }
}
