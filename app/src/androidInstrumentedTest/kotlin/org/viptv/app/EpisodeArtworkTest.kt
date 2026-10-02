package org.viptv.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.Collections
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual episode card image request and Core fallback after HTTP failure. */
@RunWith(AndroidJUnit4::class)
class EpisodeArtworkTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failedEpisodeThumbnailRequestsParentLandscape() {
        ArtworkServer().use { server ->
            val parent = Media("show", "series", name = "Fixture Show", backdrop = server.url("parent"))
            val episode = Media("show:1:1059", "episode", name = "Fixture Show", season = 1, episode = 1059,
                episodeTitle = "The Future", thumbnail = server.url("child"), positionMillis = 30_000)
            compose.setContent {
                CompositionLocalProvider(LocalTv provides true, LocalDensity provides Density(1f, 1f)) {
                    ViptvTheme(false, Color.White) {
                        EpisodeCard(episode, Modifier.width(360.dp), {}, {}, artworkContext = parent)
                    }
                }
            }
            compose.waitUntil(5_000) { server.requests.contains("/child") }
            compose.waitUntil(5_000) { server.requests.contains("/parent") }
            assertTrue(server.requests.indexOf("/child") < server.requests.indexOf("/parent"))
            compose.waitUntil(5_000) {
                val image = compose.onNodeWithTag("episode-artwork", useUnmergedTree = true).captureToImage()
                val pixel = image.toPixelMap()[image.width / 2, image.height / 2]
                pixel.green > .7f && pixel.red < .2f && pixel.blue < .2f
            }
        }
    }

    @Test fun failedEpisodeAndParentArtworkShowsReadablePlaceholder() {
        ArtworkServer(parentFound = false).use { server ->
            val parent = Media("show", "series", name = "Fixture Show", backdrop = server.url("parent"))
            val episode = Media("show:1:1059", "episode", name = "Fixture Show", season = 1, episode = 1059,
                episodeTitle = "The Future", thumbnail = server.url("child"))
            compose.setContent {
                CompositionLocalProvider(LocalTv provides true, LocalDensity provides Density(1f, 1f)) {
                    ViptvTheme(false, Color.White) {
                        EpisodeCard(episode, Modifier.width(360.dp), {}, {}, artworkContext = parent)
                    }
                }
            }
            compose.waitUntil(5_000) { server.requests.contains("/child") && server.requests.contains("/parent") }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Fixture Show").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Fixture Show").assertIsDisplayed()
        }
    }
}

private class ArtworkServer(private val parentFound: Boolean = true) : AutoCloseable {
    private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 500 }
    val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val image = ByteArrayOutputStream().use { output ->
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.GREEN)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        output.toByteArray()
    }
    private val worker = Thread {
        while (!socket.isClosed) {
            try {
                socket.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader()
                    val path = reader.readLine()?.split(' ')?.getOrNull(1).orEmpty()
                    while (!reader.readLine().isNullOrEmpty()) Unit
                    requests += path
                    val found = path == "/parent" && parentFound
                    val body = if (found) image else "missing".toByteArray()
                    val status = if (found) "200 OK" else "404 Not Found"
                    val type = if (found) "image/png" else "text/plain"
                    client.getOutputStream().write("HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    client.getOutputStream().write(body)
                    client.getOutputStream().flush()
                }
            } catch (_: SocketTimeoutException) { /* Check the close flag. */ }
            catch (error: java.net.SocketException) { if (!socket.isClosed) throw error }
        }
    }.apply { isDaemon = true; start() }

    fun url(path: String) = "http://127.0.0.1:${socket.localPort}/$path"
    override fun close() { socket.close(); worker.join(2_000) }
}
