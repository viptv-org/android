package org.viptv.app

import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeMetadataLoadingTest {
    @Test fun visibleQueueMetadataStartsWhileOtherHomeRowsAreLoading() {
        val requested = CountDownLatch(1)
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val worker = thread(isDaemon = true) {
            try {
                while (!server.isClosed && requested.count > 0) server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    val request = reader.readLine().orEmpty()
                    while (!reader.readLine().isNullOrEmpty()) Unit
                    if (request.contains("/meta/movie/queue")) requested.countDown()
                    val body = """{"meta":{"id":"queue","type":"movie","name":"Queue","description":"Loaded description"}}"""
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n" + body).toByteArray())
                }
            } catch (_: java.net.SocketException) { }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var controller: AppController
        instrumentation.runOnMainSync {
            controller = AppController(instrumentation.targetContext, "http://127.0.0.1:${server.localPort}")
        }
        try {
            // The saved queue is published, while an unrelated catalog remains pending.
            instrumentation.runOnMainSync {
                val media = Media("queue", "movie", "Queue")
                controller._state.value = AppState(route = Route.Browse(Destination.Home),
                    selectedProfile = Profile("fixture", "Fixture"), sessionRestoring = false,
                    homeLoading = true, shelves = listOf(HomeShelf("Continue watching", listOf(media), isQueueShelf = true)))
                controller.scope.launch { controller.enrichVisibleHomeItem(media) }
            }
            assertTrue("Visible Continue Watching metadata must start before all catalog rows finish", requested.await(2, TimeUnit.SECONDS))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (controller.state.value.shelves.first().items.first().description != "Loaded description" && System.nanoTime() < deadline) Thread.sleep(10)
            assertEquals("Loaded description", controller.state.value.shelves.first().items.first().description)
            instrumentation.runOnMainSync {
                controller.publishHomeShelves(listOf(HomeShelf("Continue watching", listOf(Media("queue", "movie", "Queue", positionMillis = 90_000)), isQueueShelf = true)))
            }
            assertEquals("Partial catalog publications must preserve metadata already fetched for the queue", "Loaded description", controller.state.value.shelves.first().items.first().description)
            assertEquals("New saved progress remains authoritative", 90_000L, controller.state.value.shelves.first().items.first().positionMillis)
        } finally {
            instrumentation.runOnMainSync { controller.close() }
            server.close()
            worker.join(1_000)
        }
    }
}
