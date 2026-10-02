package org.viptv.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A successful manual correction must use the profile's updated progress, not the old resume position. */
@RunWith(AndroidJUnit4::class)
class EpisodeCorrectionProgressTest {
    @Test fun markUnwatchedClearsTheOldResumePositionInOpenDetails() {
        CorrectionProgressServer().use { server ->
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val controller = AppController(instrumentation.targetContext, server.origin)
            val episode = Media("show:1:1059", "episode", name = "Fixture Show", seriesId = "show",
                season = 1, episode = 1059, positionMillis = 30_000, durationMillis = 120_000, watched = true)
            try {
                instrumentation.runOnMainSync {
                    controller._state.value = AppState(route = Route.Details(Media("show", "series", episodes = listOf(episode))),
                        selectedProfile = Profile("profile-1", "Fixture"), sessionRestoring = false)
                    controller.correctEpisode(episode, watched = false)
                }
                waitUntil(5_000) { controller.state.value.message == "Marked unwatched." }
                waitUntil(5_000) {
                    val item = (controller.state.value.route as Route.Details).media.episodes.single()
                    !item.watched && item.positionMillis == 0L
                }
                assertEquals(listOf("PUT /api/profiles/profile-1/progress/correct",
                    "GET /api/profiles/profile-1/progress/series?series_id=show"), server.requests.toList())
            } finally { controller.scope.cancel() }
        }
    }

    @Test fun pendingCorrectionReadCannotWriteToAnotherProfile() {
        CorrectionProgressServer(holdProgress = true).use { server ->
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val controller = AppController(instrumentation.targetContext, server.origin)
            val episode = episode()
            val details = Route.Details(Media("show", "series", episodes = listOf(episode)))
            try {
                instrumentation.runOnMainSync {
                    controller._state.value = AppState(route = details, selectedProfile = Profile("profile-1", "Fixture"), sessionRestoring = false)
                    controller.correctEpisode(episode, watched = false)
                }
                assertTrue(server.progressRequested.await(5, TimeUnit.SECONDS))
                val accepted = controller.state.value.route
                instrumentation.runOnMainSync {
                    controller._state.value = controller._state.value.copy(selectedProfile = Profile("profile-2", "Other"))
                }
                server.releaseProgress.countDown()
                assertTrue(server.progressResponded.await(5, TimeUnit.SECONDS))
                Thread.sleep(250)
                assertTrue(controller.state.value.route === accepted)
                assertTrue(!(controller.state.value.route as Route.Details).media.episodes.single().watched)
                assertEquals(30_000L, (controller.state.value.route as Route.Details).media.episodes.single().positionMillis)
            } finally { controller.scope.cancel() }
        }
    }

    @Test fun pendingCorrectionReadCannotReplaceAnotherDetailsRoute() {
        CorrectionProgressServer(holdProgress = true).use { server ->
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val controller = AppController(instrumentation.targetContext, server.origin)
            val episode = episode()
            val other = Route.Details(Media("another", "series", name = "Another"))
            try {
                instrumentation.runOnMainSync {
                    controller._state.value = AppState(route = Route.Details(Media("show", "series", episodes = listOf(episode))),
                        selectedProfile = Profile("profile-1", "Fixture"), sessionRestoring = false)
                    controller.correctEpisode(episode, watched = false)
                }
                assertTrue(server.progressRequested.await(5, TimeUnit.SECONDS))
                instrumentation.runOnMainSync { controller._state.value = controller._state.value.copy(route = other) }
                server.releaseProgress.countDown()
                assertTrue(server.progressResponded.await(5, TimeUnit.SECONDS))
                Thread.sleep(250)
                assertTrue(controller.state.value.route === other)
            } finally { controller.scope.cancel() }
        }
    }

    @Test fun failedProgressReadKeepsOnlyTheAcceptedWatchedCorrection() {
        CorrectionProgressServer(failProgress = true).use { server ->
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val controller = AppController(instrumentation.targetContext, server.origin)
            val episode = episode()
            try {
                instrumentation.runOnMainSync {
                    controller._state.value = AppState(route = Route.Details(Media("show", "series", episodes = listOf(episode))),
                        selectedProfile = Profile("profile-1", "Fixture"), sessionRestoring = false)
                    controller.correctEpisode(episode, watched = false)
                }
                waitUntil(5_000) { !(controller.state.value.route as Route.Details).media.episodes.single().watched }
                val current = (controller.state.value.route as Route.Details).media.episodes.single()
                assertTrue(!current.watched)
                assertEquals(30_000L, current.positionMillis)
            } finally { controller.scope.cancel() }
        }
    }

    @Test fun missingProgressRecordKeepsTheAcceptedWatchedCorrection() {
        CorrectionProgressServer(omitProgress = true).use { server ->
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val controller = AppController(instrumentation.targetContext, server.origin)
            val episode = episode()
            try {
                instrumentation.runOnMainSync {
                    controller._state.value = AppState(route = Route.Details(Media("show", "series", episodes = listOf(episode))),
                        selectedProfile = Profile("profile-1", "Fixture"), sessionRestoring = false)
                    controller.correctEpisode(episode, watched = false)
                }
                waitUntil(5_000) { server.progressResponded.count == 0L }
                waitUntil(5_000) { !(controller.state.value.route as Route.Details).media.episodes.single().watched }
                assertEquals(30_000L, (controller.state.value.route as Route.Details).media.episodes.single().positionMillis)
            } finally { controller.scope.cancel() }
        }
    }

    private fun episode() = Media("show:1:1059", "episode", name = "Fixture Show", seriesId = "show",
        season = 1, episode = 1059, positionMillis = 30_000, durationMillis = 120_000, watched = true)

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("Expected corrected episode position", condition())
    }
}

private class CorrectionProgressServer(holdProgress: Boolean = false, private val failProgress: Boolean = false,
    private val omitProgress: Boolean = false) : AutoCloseable {
    private val socket = ServerSocket(0, 2, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 500 }
    val origin = "http://127.0.0.1:${socket.localPort}"
    val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val progressRequested = CountDownLatch(1)
    val releaseProgress = CountDownLatch(if (holdProgress) 1 else 0)
    val progressResponded = CountDownLatch(1)
    private val worker = Thread {
        while (!socket.isClosed) {
            try {
                socket.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader()
                    val request = reader.readLine()?.split(' ').orEmpty()
                    val method = request.getOrNull(0).orEmpty()
                    val path = request.getOrNull(1).orEmpty()
                    requests += "$method $path"
                    var length = 0
                    while (true) {
                        val header = reader.readLine().orEmpty()
                        if (header.isEmpty()) break
                        if (header.startsWith("Content-Length:", ignoreCase = true)) length = header.substringAfter(':').trim().toInt()
                    }
                    repeat(length) { reader.read() }
                    if (method == "GET") {
                        progressRequested.countDown()
                        releaseProgress.await(5, TimeUnit.SECONDS)
                    }
                    val body = if (method == "PUT") "{}" else if (failProgress) "{\"error\":\"offline\"}" else if (omitProgress) "[]" else
                        """[{"id":"show:1:1059","type":"episode","series_id":"show","season":1,"episode":1059,"position":0,"duration":120,"watched":false}]"""
                    val bytes = body.toByteArray()
                    val status = if (method == "GET" && failProgress) "500 Internal Server Error" else "200 OK"
                    client.getOutputStream().write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    client.getOutputStream().write(bytes)
                    client.getOutputStream().flush()
                    if (method == "GET") progressResponded.countDown()
                }
            } catch (_: SocketTimeoutException) { /* Check the close flag. */ }
            catch (error: java.net.SocketException) { if (!socket.isClosed) throw error }
        }
    }.apply { isDaemon = true; start() }

    override fun close() { releaseProgress.countDown(); socket.close(); worker.join(2_000) }
}
