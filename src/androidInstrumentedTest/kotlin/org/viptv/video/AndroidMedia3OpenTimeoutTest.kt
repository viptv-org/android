package org.viptv.video

import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Real Media3 opening deadline against an owned HTTP stream that never supplies media bytes. */
class AndroidMedia3OpenTimeoutTest {
    @Test fun stalledMediaReportsTheMeasuredOpeningDeadline() = runBlocking {
        for (overrideBudget in listOf<Long?>(null, 750)) {
            val budget = overrideBudget ?: 500
            val accepted = CountDownLatch(1)
            val release = CountDownLatch(1)
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                val worker = Thread {
                    runCatching {
                        server.accept().use { socket ->
                            socket.getOutputStream().apply {
                                write("HTTP/1.1 200 OK\r\nContent-Type: video/mp4\r\nContent-Length: 1048576\r\n\r\n".toByteArray())
                                flush()
                            }
                            accepted.countDown()
                            release.await(5, TimeUnit.SECONDS)
                        }
                    }
                }.apply { isDaemon = true; start() }
                val player = AndroidMedia3BackendFactory(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    openTimeoutMillis = 500,
                ).createAndroidPlayer()
                try {
                    val failure = assertFailsWith<PlaybackFailure> {
                        player.open(
                            PlaybackSource("http://127.0.0.1:${server.localPort}/owned.mp4",
                                options = PlaybackOptions(openTimeoutMillis = overrideBudget, httpReadTimeoutMillis = 1_000)),
                            playWhenReady = false,
                        )
                    }
                    assertTrue(accepted.await(1, TimeUnit.SECONDS), "Opening must reach the stalled HTTP stream")
                    assertEquals(PlaybackErrorCode.Network, failure.error.code)
                    assertEquals("Media3 did not become ready within $budget ms.\n\nDiagnostic: media3_open_timeout", failure.error.message)
                } finally {
                    player.close()
                    release.countDown()
                    server.close()
                    worker.join(2_000)
                }
            }
        }
    }
}
