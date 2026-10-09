package org.viptv.app

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Rule
import org.junit.Test
import org.viptv.video.PlaybackKind
import org.viptv.video.PlaybackSource
import java.io.File
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PlayerSeekInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fourForwardActivationsCompoundAndKeepTheTransportSelection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val controller = AppController(instrumentation.targetContext, "https://example.invalid")
        val file = File(instrumentation.targetContext.cacheDir, "owned-seek-clock.m4a")
        instrumentation.context.assets.open("seek-clock.m4a").use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        val media = Media("owned-seek", "movie", name = "Owned seek clock", durationMillis = 300_000)
        var mounted by mutableStateOf(true)
        try {
            runBlocking { controller.player.open(PlaybackSource(file.toURI().toString(), kindHint = PlaybackKind.OnDemand), false) }
            compose.runOnIdle {
                controller.playbackTitleDurationMillis = 300_000
                controller._state.value = AppState(route = Route.Player(media, Source("owned", "Owned fixture")), sessionRestoring = false,
                    playerChromeVisible = true, playbackDeliveryMode = "direct")
            }
            compose.setContent {
                CompositionLocalProvider(LocalTv provides true, LocalDensity provides Density(1f, 1f)) {
                    ViptvTheme(false, Color.White) {
                        if (mounted) {
                            val state by controller.state.collectAsState()
                            PlaybackScreen(media, state.playerChromeVisible, state.seekPreview, state.playbackTracks, controller)
                        }
                    }
                }
            }
            compose.onRoot().performKeyInput {
                pressKey(Key.DirectionRight)
                repeat(4) { pressKey(Key.DirectionCenter) }
            }
            compose.onNodeWithContentDescription("Forward 30 seconds").assertIsSelected()
            compose.runOnIdle { assertEquals(120_000L, controller.state.value.seekPreview?.targetMillis) }
            compose.waitUntil(3000) { controller.state.value.seekPreview == null }
            compose.onNodeWithContentDescription("Forward 30 seconds").assertIsSelected()
            compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
            compose.runOnIdle { assertEquals(150_000L, controller.state.value.seekPreview?.targetMillis) }
        } finally {
            compose.runOnIdle { mounted = false }
            runBlocking { controller.player.close() }
            controller.scope.cancel()
            file.delete()
        }
    }

    @Test fun bufferedRangeRemainsAtActualPlaybackWhenScrubbingBeyondIt() {
        compose.setContent {
            CompositionLocalProvider(LocalTv provides true, LocalDensity provides Density(1f, 1f)) {
                ViptvTheme(false, Color.White) { PlayerTimeline(10_000, 100_000, 30_000, previewPosition = 80_000) }
            }
        }
        compose.onNodeWithContentDescription("Playback position")
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Buffered to 0:30"))
        compose.onNodeWithText("1:20").assertExists()
        compose.onNodeWithText("0:20 buffered").assertExists()
    }
}
