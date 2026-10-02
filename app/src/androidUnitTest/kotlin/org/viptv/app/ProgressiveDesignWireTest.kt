package org.viptv.app

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlin.test.*

/** The real HTTP/JSON/native-core path, with independent upstream latency. */
class ProgressiveDesignWireTest {
    @Test fun failedExistingCatalogKeepsItsRowWhileNewCatalogAppears() = runBlocking {
        val includeOld = AtomicBoolean(true)
        val failOld = AtomicBoolean(true)
        val failNew = AtomicBoolean(false)
        val executor = Executors.newFixedThreadPool(8)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            this.executor = executor
            createContext("/") { exchange ->
                val path = exchange.requestURI.path
                val query = exchange.requestURI.rawQuery.orEmpty()
                val failed = path == "/api/discover" &&
                    ((query.contains("catalog=old") && failOld.get()) || (query.contains("catalog=new") && failNew.get()))
                val body = when {
                    path == "/api/catalogs" -> if (includeOld.get()) """[{"id":"old","type":"movie","name":"Old","addon_id":1},{"id":"new","type":"movie","name":"New","addon_id":2}]""" else """[{"id":"new","type":"movie","name":"New","addon_id":2}]"""
                    failed -> """{"message":"Temporarily unavailable"}"""
                    path == "/api/discover" -> """{"metas":[{"id":"new-title","type":"movie","name":"New title"}]}"""
                    path.endsWith("/continue/page") || path.endsWith("/favorites/page") -> """{"items":[]}"""
                    path == "/api/live" -> """{"channels":[]}"""
                    else -> """{"items":[]}"""
                }.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(if (failed) 503 else 200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        try {
            val old = Media("old-title", "movie")
            val previous = listOf(HomeShelf("Old", listOf(old), id = "1\u0000movie\u0000old"))
            val gateway = VipTvHttpGateway("http://127.0.0.1:${server.address.port}")
            var incomplete = false
            val rows = gateway.refreshHome("1", previous, { incomplete = true })
            assertTrue(incomplete)
            assertEquals(listOf("old-title"), rows.first { it.id == "1\u0000movie\u0000old" }.items.map { it.id })
            assertEquals(listOf("new-title"), rows.first { it.id == "2\u0000movie\u0000new" }.items.map { it.id })
            failOld.set(false)
            failNew.set(true)
            incomplete = false
            val partial = gateway.refreshHome("1", previous, { incomplete = true })
            assertTrue(incomplete)
            assertTrue(partial.none { it.id == "2\u0000movie\u0000new" })
            failNew.set(false)
            incomplete = false
            val recovered = gateway.refreshHome("1", partial, { incomplete = true })
            assertTrue(!incomplete)
            assertEquals(listOf("new-title"), recovered.first { it.id == "2\u0000movie\u0000new" }.items.map { it.id })
            includeOld.set(false)
            val removed = gateway.refreshHome("1", recovered)
            assertEquals(listOf("2\u0000movie\u0000new"), removed.map { it.id })
        } finally { server.stop(0); executor.shutdownNow() }
    }
    @Test fun revisionCheckUsesThePrivateEndpointWithoutLoadingCatalogs() = runBlocking {
        val paths = mutableListOf<String>()
        ParallelFixture { path, _ ->
            synchronized(paths) { paths += path }
            assertEquals("/api/catalogs/revision", path)
            """{"revision":"opaque-2"}"""
        }.use { server ->
            val gateway = VipTvHttpGateway(server.origin, "private-token")
            assertEquals("opaque-2", gateway.catalogRevision())
            assertEquals("opaque-2", gateway.catalogRevision())
            assertEquals(listOf("/api/catalogs/revision", "/api/catalogs/revision"), paths)
        }
    }
    @Test fun initialHomeMetadataIsBoundedToVisibleCardsAndLookahead() = runBlocking {
        val metadata = AtomicInteger()
        val items = (1..30).joinToString(",") { """{"id":"movie-$it","type":"movie","name":"Movie $it"}""" }
        ParallelFixture { path, _ -> when {
            path == "/api/catalogs" -> "[]"
            path.endsWith("/continue/page") -> """{"items":[$items]}"""
            path.endsWith("/favorites/page") -> """{"items":[]}"""
            path == "/api/live" -> """{"channels":[]}"""
            path.startsWith("/api/meta/") -> { metadata.incrementAndGet(); """{"meta":{"id":"${path.substringAfterLast('/')}","type":"movie","name":"Enriched"}}""" }
            else -> error("Unexpected fixture path")
        } }.use { server ->
            val rows = VipTvHttpGateway(server.origin).home("1")
            assertEquals(30, rows.first { it.isQueueShelf }.items.size)
            assertEquals(6, metadata.get())
        }
    }
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
