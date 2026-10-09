package org.viptv.app

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.json.JSONArray
import org.json.JSONObject
import uniffi.viptv_core.CoreBridge
import uniffi.viptv_core.CoreException
import uniffi.viptv_core.normalize
import tv.viptv.core.HttpTransport

/** Actual generated UniFFI/JNA + host Rust. No replacement core or remote API. */
class SharedCoreNativeStressTest {
    private val fixtureOrigin = "https://native-stress.invalid"
    private fun begin(core: CoreBridge, origin: String = fixtureOrigin, preview: Boolean = false): JSONArray =
        JSONArray(core.update(JSONObject().put("Begin", JSONObject().put("origin", origin)
            .put("allowInsecurePreview", preview)).toString()))

    private fun effect(requests: JSONArray, kind: String): JSONObject =
        (0 until requests.length()).map(requests::getJSONObject).single { it.getJSONObject("effect").has(kind) }

    private fun phase(core: CoreBridge) = JSONObject(core.view()).getString("phase")

    @Test(timeout = 60_000) fun loopbackFixtureShutdownJoinsWaitingReplyWorkers() {
        repeat(100) {
            StalledReads(3).use { server ->
                val port = java.net.URI(server.origin).port
                val clients = List(3) { Socket("127.0.0.1", port) }
                try {
                    clients.forEach { socket ->
                        socket.getOutputStream().write("GET /api/auth/me HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
                    }
                    assertTrue(server.entered.await(5, TimeUnit.SECONDS))
                } finally {
                    clients.forEach { it.close() }
                }
            }
        }
    }

    @Test(timeout = 60_000) fun repeatedNativeHandlesAndStringBufferSuccessErrorPathsRemainUsable() {
        // Returned strings and typed errors exercise generated RustBuffer lifting/freeing.
        // This observes bounded functionality, not allocator/sanitizer leak freedom.
        var warmedRss: Long? = null
        var warmedHeap: Long? = null
        repeat(300) { index ->
            if (index == 20) { warmedRss = ownRssKb(); warmedHeap = usedHeapKb() }
            CoreBridge().use { core ->
                val load = effect(begin(core), "Storage")
                assertEquals("Restoring", phase(core))
                assertFailsWith<CoreException> { core.update("{") }
                core.resolve(load.getLong("id").toUInt(), """{"Ok":null}""")
                assertEquals("Pairing", phase(core))
                core.close()
                core.close()
                assertFailsWith<IllegalStateException> { core.view() }
            }
            val label = "Native $index λ"
            val input = JSONObject().put("id", "native-$index").put("type", "movie")
                .put("name", label).put("description", "λ".repeat(2048)).toString()
            assertEquals(label, JSONObject(normalize("media", input, fixtureOrigin)).getString("name"))
            assertFailsWith<CoreException> { normalize("media", "{", fixtureOrigin) }
            if (index % 30 == 0) assertFailsWith<CoreException> {
                normalize("media", "x".repeat(2 * 1024 * 1024 + 1), fixtureOrigin)
            }
            // A subsequent call must remain usable after the error-buffer path.
            assertEquals("native-$index", JSONObject(normalize("media", input, fixtureOrigin)).getString("id"))
        }
        val finalRss = ownRssKb()
        val finalHeap = usedHeapKb()
        System.gc()
        Thread.sleep(100)
        println("Native stress KiB RSS warmed=$warmedRss final=$finalRss postGc=${ownRssKb()}; heap warmed=$warmedHeap final=$finalHeap postGc=${usedHeapKb()} (observation, not leak proof)")
    }

    @Test(timeout = 60_000) fun concurrentNativeReadsAndRepeatedCloseHaveOnlyDefinedOutcomes() {
        val executor = Executors.newFixedThreadPool(5)
        val nativeSuccesses = AtomicInteger()
        val closedRefusals = AtomicInteger()
        try {
            repeat(40) {
                CoreBridge().use { core ->
                    begin(core)
                    val start = CountDownLatch(1)
                    val entered = CountDownLatch(4)
                    val completedCalls = AtomicInteger()
                    val readers = (0 until 4).map {
                        executor.submit {
                            assertTrue(start.await(5, TimeUnit.SECONDS))
                            assertEquals("Restoring", phase(core))
                            nativeSuccesses.incrementAndGet()
                            entered.countDown()
                            repeat(100) {
                                try {
                                    assertEquals("Restoring", phase(core))
                                    nativeSuccesses.incrementAndGet()
                                } catch (closed: IllegalStateException) {
                                    // Never swallow native/JSON errors, only generated closed-handle refusal.
                                    assertTrue(closed.message.orEmpty().contains("object has already been destroyed"))
                                    closedRefusals.incrementAndGet()
                                }
                                completedCalls.incrementAndGet()
                            }
                        }
                    }
                    val closer = executor.submit {
                        assertTrue(entered.await(5, TimeUnit.SECONDS))
                        repeat(3) { core.close() }
                    }
                    start.countDown()
                    readers.forEach { it.get(10, TimeUnit.SECONDS) }
                    closer.get(10, TimeUnit.SECONDS)
                    assertEquals(400, completedCalls.get())
                    assertFailsWith<IllegalStateException> { core.view() }
                }
            }
            assertTrue(nativeSuccesses.get() >= 160)
            assertTrue(closedRefusals.get() > 0, "Close never overlapped the worker read attempts")
            println("Native close-race views=${nativeSuccesses.get()} expectedClosedRefusals=${closedRefusals.get()}")
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Native stress workers not reclaimed")
        }
    }

    @Test(timeout = 60_000) fun concurrentActualLoopbackReadsCancelAndCannotPublishIntoSupersedingNativeEpoch() {
        repeat(10) {
            StalledReads(3).use { server ->
                val executor = Executors.newFixedThreadPool(3)
                val transport = HttpTransport(setOf(server.origin), executor, timeoutMs = 5000)
                val cores = List(3) { CoreBridge() }
                try {
                    val complete = CountDownLatch(3)
                    val completionCount = AtomicInteger()
                    val results = List(3) { AtomicReference<JSONObject>() }
                    val pending = cores.map { core ->
                        val load = effect(begin(core, server.origin, true), "Storage")
                        val tokens = JSONObject().put("sessionId", "fixture").put("accountId", "1")
                            .put("profileId", JSONObject.NULL).put("accessToken", "synthetic-access")
                            .put("refreshToken", "synthetic-refresh").put("expiresIn", 3600)
                        effect(JSONArray(core.resolve(load.getLong("id").toUInt(),
                            JSONObject().put("Ok", tokens.toString()).toString())), "Http")
                    }
                    val calls = pending.mapIndexed { index, request ->
                        transport.execute(request.getJSONObject("effect").getJSONObject("Http")) {
                            results[index].set(it)
                            completionCount.incrementAndGet()
                            complete.countDown()
                        }
                    }
                    assertTrue(server.entered.await(5, TimeUnit.SECONDS), "Actual loopback requests never entered")
                    val freshViews = cores.map { core ->
                        val load = effect(begin(core, server.origin, true), "Storage")
                        core.resolve(load.getLong("id").toUInt(), """{"Ok":null}""")
                        assertEquals("Pairing", phase(core))
                        core.view()
                    }
                    calls.take(2).forEach { it.cancel() }
                    // The third real read succeeds late; neither kind may publish into the new epoch.
                    server.release.countDown()
                    assertTrue(complete.await(5, TimeUnit.SECONDS), "HTTP workers did not complete")
                    cores.forEachIndexed { index, core ->
                        val result = results[index].get()
                        if (index < 2) assertEquals("Request cancelled", result.getJSONObject("Err").getString("Io"))
                        else assertEquals(200, result.getJSONObject("Ok").getInt("status"))
                        assertEquals(0, JSONArray(core.resolve(pending[index].getLong("id").toUInt(), result.toString())).length())
                        assertEquals(freshViews[index], core.view(), "Stale native HTTP completion published")
                        assertEquals(index < 2, calls[index].cancelled.get())
                        assertNull(calls[index].connection.get())
                    }
                    executor.shutdown()
                    assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
                    assertEquals(3, completionCount.get())
                } finally {
                    cores.forEach { it.close() }
                    executor.shutdownNow()
                    assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
                }
            }
        }
    }

    private fun ownRssKb(): Long? = File("/proc/self/status").takeIf { it.isFile }?.useLines { lines ->
        lines.firstOrNull { it.startsWith("VmRSS:") }?.substringAfter(':')?.trim()?.substringBefore(' ')?.toLongOrNull()
    }
    private fun usedHeapKb(): Long = Runtime.getRuntime().let { (it.totalMemory() - it.freeMemory()) / 1024 }

    private class StalledReads(private val count: Int) : AutoCloseable {
        private val listener = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val origin = "http://127.0.0.1:${listener.localPort}"
        val entered = CountDownLatch(count)
        val release = CountDownLatch(1)
        private val sockets = ConcurrentHashMap.newKeySet<Socket>()
        private val workers = Executors.newFixedThreadPool(count)
        private val failure = AtomicReference<Throwable>()
        private val acceptor = thread(name = "core-native-stress-loopback") {
            try {
                repeat(count) {
                    val socket = listener.accept()
                    sockets.add(socket)
                    workers.submit {
                        try {
                            socket.use {
                                socket.soTimeout = 5000
                                val input = socket.getInputStream().bufferedReader()
                                assertTrue(input.readLine().startsWith("GET /api/auth/me "))
                                while (!input.readLine().isNullOrEmpty()) { /* drain request headers */ }
                                entered.countDown()
                                assertTrue(release.await(10, TimeUnit.SECONDS))
                                val body = """{"account":{"id":"1"},"profiles":[{"id":"stale","name":"Must not publish","setup_complete":true}]}""".toByteArray()
                                val output = socket.getOutputStream()
                                output.write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                output.write(body)
                            }
                        } catch (_: SocketException) {
                            // The cancelled real client may reset its socket before the delayed reply.
                        } catch (problem: Throwable) { failure.compareAndSet(null, problem) }
                        finally { sockets.remove(socket) }
                    }
                }
            } catch (problem: Throwable) {
                if (!listener.isClosed) failure.compareAndSet(null, problem)
            }
        }
        override fun close() {
            release.countDown()
            listener.close()
            sockets.forEach { it.close() }
            acceptor.join(5000)
            // Release and close IO before joining. Interrupting a released
            // latch can still throw until its waiter has resumed.
            workers.shutdown()
            val settled = workers.awaitTermination(5, TimeUnit.SECONDS)
            if (!settled) {
                workers.shutdownNow()
                workers.awaitTermination(5, TimeUnit.SECONDS)
            }
            assertTrue(settled, "Loopback reply workers not reclaimed")
            assertTrue(!acceptor.isAlive && sockets.isEmpty(), "Loopback fixture resources not reclaimed")
            failure.get()?.let { throw AssertionError("Loopback fixture failed", it) }
        }
    }
}
