package com.getair.video

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.awt.Canvas
import java.awt.Color
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.nio.file.Path
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

class LinuxMpvEmbeddedPlayerTest {
    @Test
    fun realMpvRendersIntoEmbeddedCanvasWhenEnabled() = runBlocking {
        if (System.getenv("AIR_MPV_SURFACE_INTEGRATION") != "1") return@runBlocking
        check(!GraphicsEnvironment.isHeadless()) { "The MPV surface test requires an X11 display" }
        val bridge = Path.of(checkNotNull(System.getenv("AIR_JAWT_BRIDGE")))
        val corpus = Path.of(checkNotNull(System.getenv("AIR_VIDEO_CORPUS_DIR")))
        val window = createWindow()
        val player = LinuxMpvBackendFactory(LinuxMpvOptions(jawtBridge = bridge)).createLinuxPlayer()
        try {
            player.attach(window.canvas)
            player.open(
                PlaybackSource(
                    uri = corpus.resolve("h264-multitrack.mkv").toUri().toString(),
                    kindHint = PlaybackKind.OnDemand,
                ),
                playWhenReady = true,
            )
            assertEquals(PlaybackStatus.Ready, player.state.value.status)
            delay(1_500)

            assertTrue(player.diagnosticProperty("vo-configured")?.jsonPrimitive?.booleanOrNull == true)
            assertNotNull(player.diagnosticProperty("video-out-params"))
        } finally {
            player.close()
            SwingUtilities.invokeAndWait { window.frame.dispose() }
        }
    }

    private fun createWindow(): TestWindow {
        var result: TestWindow? = null
        SwingUtilities.invokeAndWait {
            val background = Color(3, 4, 5)
            val canvas = Canvas().apply {
                preferredSize = Dimension(640, 360)
                this.background = background
            }
            val frame = JFrame("Air MPV surface integration").apply {
                isUndecorated = true
                contentPane.add(canvas)
                pack()
                setLocation(32, 32)
                isVisible = true
            }
            result = TestWindow(frame, canvas)
        }
        return checkNotNull(result)
    }

    private data class TestWindow(
        val frame: JFrame,
        val canvas: Canvas,
    )
}
