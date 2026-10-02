package org.viptv.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises Player exit through the real controller and the profile series-progress HTTP adapter. */
@RunWith(AndroidJUnit4::class)
class EpisodeWatchedReturnTest {
    @Test fun playerExitRefreshesRetainedEpisodeDetailsForSelectedProfile() {
        EpisodeProgressServer().use { server ->
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val controller = AppController(instrumentation.targetContext, server.origin)
            val episode = Media("show:1:1059", "episode", name = "Fixture Show", seriesId = "show",
                season = 1, episode = 1059)
            val details = Route.Details(Media("show", "series", name = "Fixture Show", season = 1,
                episode = 1059, episodes = listOf(episode)))
            val sources = Route.Sources(episode, backRoute = details)
            val player = Route.Player(episode, Source("source", "Fixture"), PlaybackReturn.Sources,
                sourceRoute = sources)
            try {
                instrumentation.runOnMainSync {
                    controller._state.value = AppState(route = player, selectedProfile = Profile("profile-1", "Fixture"),
                        sessionRestoring = false)
                    controller.exitPlayer(player, episode)
                }
                assertTrue(controller.state.value.route is Route.Sources)
                waitUntil(5_000) {
                    val route = controller.state.value.route as? Route.Sources
                    (route?.backRoute as? Route.Details)?.media?.episodes?.singleOrNull()?.watched == true
                }
                assertEquals("/api/profiles/profile-1/progress/series?series_id=show", server.requestTarget)
                val refreshed = ((controller.state.value.route as Route.Sources).backRoute as Route.Details).media
                assertEquals(1059, refreshed.episode)
            } finally {
                controller.scope.cancel()
            }
        }
    }

    @Test fun quickBackToRetainedDetailsStillReceivesCompletedFact() {
        EpisodeProgressServer(holdResponse = true).use { server ->
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val controller = AppController(instrumentation.targetContext, server.origin)
            val (episode, player) = playerFixture()
            try {
                instrumentation.runOnMainSync {
                    controller._state.value = AppState(route = player, selectedProfile = Profile("profile-1", "Fixture"),
                        sessionRestoring = false)
                    controller.exitPlayer(player, episode)
                }
                assertTrue(server.requestStarted.await(5, TimeUnit.SECONDS))
                instrumentation.runOnMainSync { controller.back() }
                assertTrue(controller.state.value.route is Route.Details)
                server.releaseResponse.countDown()
                waitUntil(5_000) { (controller.state.value.route as? Route.Details)?.media?.episodes?.singleOrNull()?.watched == true }
                assertEquals(1059, (controller.state.value.route as Route.Details).media.episode)
            } finally { controller.scope.cancel() }
        }
    }

    @Test fun profileSwitchWhileProgressReadIsPendingCannotChangeEpisodeCard() {
        EpisodeProgressServer(holdResponse = true).use { server ->
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val controller = AppController(instrumentation.targetContext, server.origin)
            val (episode, player) = playerFixture()
            try {
                instrumentation.runOnMainSync {
                    controller._state.value = AppState(route = player, selectedProfile = Profile("profile-1", "Fixture"),
                        sessionRestoring = false)
                    controller.exitPlayer(player, episode)
                }
                assertTrue(server.requestStarted.await(5, TimeUnit.SECONDS))
                instrumentation.runOnMainSync {
                    controller._state.value = controller._state.value.copy(selectedProfile = Profile("profile-2", "Other"))
                }
                server.releaseResponse.countDown()
                assertTrue(server.responseSent.await(5, TimeUnit.SECONDS))
                Thread.sleep(300)
                val details = (controller.state.value.route as Route.Sources).backRoute as Route.Details
                assertTrue(details.media.episodes.single().watched.not())
            } finally { controller.scope.cancel() }
        }
    }

    private fun playerFixture(): Pair<Media, Route.Player> {
        val episode = Media("show:1:1059", "episode", name = "Fixture Show", seriesId = "show",
            season = 1, episode = 1059)
        val details = Route.Details(Media("show", "series", name = "Fixture Show", season = 1,
            episode = 1059, episodes = listOf(episode)))
        val sources = Route.Sources(episode, backRoute = details)
        return episode to Route.Player(episode, Source("source", "Fixture"), PlaybackReturn.Sources, sourceRoute = sources)
    }

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean) {
        val until = System.currentTimeMillis() + timeoutMillis
        while (!condition() && System.currentTimeMillis() < until) Thread.sleep(20)
        assertTrue("Expected profile completion after Player exit", condition())
    }
}

private class EpisodeProgressServer(holdResponse: Boolean = false) : AutoCloseable {
    private val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 500 }
    val origin = "http://127.0.0.1:${socket.localPort}"
    val requestStarted = CountDownLatch(1)
    val releaseResponse = CountDownLatch(if (holdResponse) 1 else 0)
    val responseSent = CountDownLatch(1)
    @Volatile var requestTarget: String? = null
    private val worker = Thread {
        while (!socket.isClosed) {
            try {
                socket.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader()
                    requestTarget = reader.readLine()?.split(' ')?.getOrNull(1)
                    while (!reader.readLine().isNullOrEmpty()) Unit
                    requestStarted.countDown()
                    releaseResponse.await(5, TimeUnit.SECONDS)
                    val body = """[{"id":"show:1:1059","type":"episode","series_id":"show","season":1,"episode":1059,"watched":true}]""".toByteArray()
                    client.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    client.getOutputStream().write(body)
                    client.getOutputStream().flush()
                    responseSent.countDown()
                }
            } catch (_: SocketTimeoutException) { /* Check the close flag. */ }
            catch (error: java.net.SocketException) { if (!socket.isClosed) throw error }
        }
    }.apply { isDaemon = true; start() }

    override fun close() { releaseResponse.countDown(); socket.close(); worker.join(2_000) }
}
