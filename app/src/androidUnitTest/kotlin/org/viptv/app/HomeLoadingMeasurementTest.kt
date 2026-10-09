package org.viptv.app

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlin.test.*

/** Real HTTP/native projection measurements; injected delay is not a device benchmark. */
class HomeLoadingMeasurementTest {
    @Test fun measureFirstRepeatedAndDelayedHomeDependencies() = runBlocking {
        for (scenario in listOf("baseline", "slow_catalogs", "slow_metadata", "slow_providers")) {
            repeat(3) { iteration ->
                Fixture(scenario).use { fixture ->
                    val gateway = VipTvHttpGateway(fixture.origin)
                    for (pass in listOf("first", "repeat")) {
                        fixture.metadataCalls.set(0)
                        fixture.maxRequests.set(0)
                        val start = System.nanoTime()
                        val firstQueue = AtomicLong(-1)
                        val firstCatalog = AtomicLong(-1)
                        val updates = AtomicInteger()
                        val rows = withTimeout(10_000) { gateway.home("fixture") { shelves ->
                            val elapsed = (System.nanoTime() - start) / 1_000_000
                            updates.incrementAndGet()
                            if (shelves.any { it.isQueueShelf }) firstQueue.compareAndSet(-1, elapsed)
                            if (shelves.any { it.contentType == "movie" }) firstCatalog.compareAndSet(-1, elapsed)
                        } }
                        val complete = (System.nanoTime() - start) / 1_000_000
                        assertEquals(12, rows.first { it.isQueueShelf }.items.size)
                        assertEquals(6, rows.count { it.contentType == "movie" })
                        assertEquals(0, fixture.metadataCalls.get(), "Home loading must not hydrate unfocused cards")
                        println("HOME_MEASURE scenario=$scenario iteration=$iteration pass=$pass queueMs=${firstQueue.get()} catalogMs=${firstCatalog.get()} completeMs=$complete updates=${updates.get()} metadataCalls=${fixture.metadataCalls.get()} maxHttp=${fixture.maxRequests.get()}")
                    }
                }
            }
        }
    }

    @Test fun savedQueueRemainsAvailableWhileCatalogsAreBlockedWithoutFetchingMetadata() = runBlocking {
        Fixture("blocked").use { fixture ->
            val firstQueue = CompletableDeferred<List<HomeShelf>>()
            val result = async { VipTvHttpGateway(fixture.origin).home("fixture") { rows ->
                if (rows.any { it.isQueueShelf } && !firstQueue.isCompleted) firstQueue.complete(rows)
            } }
            try {
                val initial = withTimeout(3_000) { firstQueue.await() }
                assertEquals(12, initial.first { it.isQueueShelf }.items.size)
                assertFalse(result.isCompleted, "Optional work must still be blocked after the queue appears")
            } finally { fixture.gate.countDown() }
            assertEquals(6, withTimeout(5_000) { result.await() }.count { it.contentType == "movie" })
            assertEquals(0, fixture.metadataCalls.get())
        }
    }

    @Test fun requestedMetadataIsFetchedOnceAndCachedWithoutHydratingOtherCards() = runBlocking {
        Fixture("baseline").use { fixture ->
            val gateway = VipTvHttpGateway(fixture.origin)
            val rows = gateway.home("fixture") {}
            val selected = rows.first { it.isQueueShelf }.items.first()
            assertEquals(0, fixture.metadataCalls.get())
            val metadata = gateway.metadata(selected)
            assertEquals(selected.id, metadata.id)
            assertEquals(1, fixture.metadataCalls.get())
            assertEquals(metadata, gateway.metadata(selected))
            assertEquals(1, fixture.metadataCalls.get(), "Repeated metadata access must reuse the cached response")
        }
    }

    private class Fixture(private val scenario: String) : AutoCloseable {
        val gate = CountDownLatch(1)
        val metadataCalls = AtomicInteger()
        val maxRequests = AtomicInteger()
        private val active = AtomicInteger()
        private val failures = ConcurrentHashMap.newKeySet<String>()
        private val executor = Executors.newFixedThreadPool(8)
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            this.executor = this@Fixture.executor
            createContext("/") { exchange ->
                maxRequests.accumulateAndGet(active.incrementAndGet(), ::maxOf)
                try {
                    val path = exchange.requestURI.path
                    val metadata = path.startsWith("/api/meta/")
                    if (metadata) metadataCalls.incrementAndGet()
                    if ((scenario == "slow_catalogs" && path == "/api/catalogs") ||
                        (scenario == "slow_metadata" && metadata) ||
                        (scenario == "slow_providers" && path == "/api/discover")) Thread.sleep(250)
                    if (scenario == "blocked" && (metadata || path == "/api/catalogs")) {
                        check(gate.await(5, TimeUnit.SECONDS)) { "Fixture gate timed out" }
                    }
                    val body = when {
                        path == "/api/catalogs" -> (1..6).joinToString(",", "[", "]") {
                            """{"id":"catalog-$it","type":"movie","name":"Catalog $it","addon_id":1}"""
                        }
                        path.endsWith("/continue/page") -> (1..12).joinToString(",", "{\"items\":[", "]}") {
                            """{"id":"movie-$it","type":"movie","name":"Fixture $it","position":12,"duration":600}"""
                        }
                        path.endsWith("/favorites/page") -> """{"items":[]}"""
                        path == "/api/v2/iptv/live/channels" -> """{"catalog_id":1,"generation":1,"items":[],"next_cursor":null,"previous_cursor":null}"""
                        path == "/api/discover" -> """{"metas":[{"id":"catalog-title","type":"movie","name":"Catalog fixture"}]}"""
                        metadata -> """{"meta":{"id":"${path.substringAfterLast('/')}","type":"movie","name":"Fixture","description":"Metadata fixture"}}"""
                        else -> error("Unexpected synthetic route")
                    }.toByteArray()
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                } catch (error: Exception) {
                    failures.add(error.javaClass.simpleName)
                    exchange.close()
                } finally { active.decrementAndGet() }
            }
            start()
        }
        val origin get() = "http://127.0.0.1:${server.address.port}"
        override fun close() {
            gate.countDown()
            server.stop(0)
            executor.shutdownNow()
            assertTrue(failures.isEmpty(), "Synthetic server failures: $failures")
        }
    }
}
