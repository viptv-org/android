package org.viptv.app

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlin.test.*

/** The real HTTP/JSON/native-core path, with independent upstream latency. */
class ProgressiveDesignWireTest {
    @Test fun savedQueueAppearsBeforeCataloguesAndSharedMetadataIsFetchedOnce() = runBlocking {
        val gate = CountDownLatch(1)
        val counts = ConcurrentHashMap<String, AtomicInteger>()
        val server = ParallelFixture { path, _ ->
            counts.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
            when {
                path == "/api/catalogs" -> { gate.await(3, TimeUnit.SECONDS); "[]" }
                path.endsWith("/continue/page") -> """{"items":[{"id":"tt-series:1:1","type":"episode","series_id":"tt-series","name":"Series","season":1,"episode":1,"position":12,"duration":600},{"id":"tt-series:1:2","type":"episode","series_id":"tt-series","name":"Series","season":1,"episode":2,"position":20,"duration":600}]}"""
                path.endsWith("/favorites/page") -> """{"items":[]}"""
                path == "/api/live" -> """{"channels":[]}"""
                path == "/api/meta/series/tt-series" -> { gate.await(3, TimeUnit.SECONDS); """{"meta":{"id":"tt-series","type":"series","name":"Series","description":"Hydrated description","videos":[]}}""" }
                else -> error("Unexpected fixture path")
            }
        }
        try {
            val first = CompletableDeferred<List<HomeShelf>>()
            val gateway = VipTvHttpGateway(server.origin)
            val result = async(Dispatchers.IO) { gateway.home("1") { rows ->
                if (!first.isCompleted && rows.any { it.isQueueShelf && it.items.size == 2 }) first.complete(rows)
            } }
            val initial = withTimeout(2000) { first.await() }.first { it.isQueueShelf }
            assertEquals(listOf("tt-series:1:1", "tt-series:1:2"), initial.items.map { it.id })
            assertEquals(12_000, initial.items.first().positionMillis)
            assertFalse(result.isCompleted)
            gate.countDown()
            val complete = withTimeout(3000) { result.await() }.first { it.isQueueShelf }
            assertEquals("Hydrated description", complete.items.first().description)
            assertEquals(1, counts["/api/meta/series/tt-series"]?.get())
            assertEquals(1, counts["/api/catalogs"]?.get())
        } finally { gate.countDown(); server.close() }
    }

    @Test fun searchPublishesTheFastCatalogueWithoutWaitingForTheSlowCatalogue() = runBlocking {
        val gate = CountDownLatch(1)
        val server = ParallelFixture { path, query ->
            when (path) {
                "/api/catalogs" -> """[{"id":"slow","type":"movie","name":"Slow","addon_id":1,"supports_search":true,"extra":[{"name":"search"}]},{"id":"fast","type":"movie","name":"Fast","addon_id":1,"supports_search":true,"extra":[{"name":"search"}]}]"""
                "/api/live" -> """{"channels":[]}"""
                "/api/discover" -> {
                    if (query.contains("catalog=slow")) { gate.await(3, TimeUnit.SECONDS); """{"metas":[{"id":"tt-slow","type":"movie","name":"Slow match"}]}""" }
                    else """{"metas":[{"id":"tt-fast","type":"movie","name":"Fast match"}]}"""
                }
                else -> error("Unexpected fixture path")
            }
        }
        try {
            val first = CompletableDeferred<SearchResults>()
            val result = async(Dispatchers.IO) { VipTvHttpGateway(server.origin).search("title") { update ->
                if (!first.isCompleted && update.sections.any { it.items.any { item -> item.id == "tt-fast" } }) first.complete(update)
            } }
            val partial = withTimeout(2000) { first.await() }
            assertEquals(listOf("tt-fast"), partial.sections.flatMap { it.items }.map { it.id })
            assertFalse(result.isCompleted)
            gate.countDown()
            assertEquals(listOf("Slow", "Fast"), withTimeout(3000) { result.await() }.sections.map { it.source })
        } finally { gate.countDown(); server.close() }
    }
    @Test fun sameNamedCataloguesKeepTheirIdentityAndRepeatedTitles() = runBlocking {
        val server = ParallelFixture { path, _ -> when (path) {
            "/api/catalogs" -> """[{"id":"search","type":"movie","name":"Search","addon_id":1,"addon_name":"Provider One","supports_search":true},{"id":"search","type":"movie","name":"Search","addon_id":2,"addon_name":"Provider Two","supports_search":true}]"""
            "/api/live" -> """{"channels":[]}"""
            "/api/discover" -> """{"metas":[{"id":"shared","type":"movie","name":"Shared title"},{"id":"shared","type":"movie","name":"Shared title"}]}"""
            else -> error("Unexpected request")
        } }
        try {
            val result = VipTvHttpGateway(server.origin).search("title")
            assertEquals(listOf("Provider One · Search", "Provider Two · Search"), result.sections.map { it.source })
            assertEquals(2, result.sections.map { it.id }.toSet().size)
            assertEquals(listOf(listOf("shared"), listOf("shared")), result.sections.map { it.items.map(Media::id) })
        } finally { server.close() }
    }

}

private class ParallelFixture(respond: (String, String) -> String) : AutoCloseable {
    private val executor = Executors.newFixedThreadPool(8)
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        this.executor = this@ParallelFixture.executor
        createContext("/") { exchange ->
            val bytes = respond(exchange.requestURI.path, exchange.requestURI.rawQuery.orEmpty()).toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        start()
    }
    val origin get() = "http://127.0.0.1:" + server.address.port
    override fun close() { server.stop(0); executor.shutdownNow() }
}
