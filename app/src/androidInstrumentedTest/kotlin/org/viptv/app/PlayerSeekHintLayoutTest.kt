package org.viptv.app

import android.graphics.Bitmap

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real player chrome and remote input; no network or media decoding fixture. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class PlayerSeekHintLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun timelineFocusDoesNotMoveTheTrackOrTitle() = withPlayer {
        val track = compose.onNodeWithContentDescription("Playback position").fetchSemanticsNode().boundsInRoot
        val title = compose.onAllNodesWithText("Seek layout fixture")[1].fetchSemanticsNode().boundsInRoot
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        assertEquals("Timeline focus must not move the track", track,
            compose.onNodeWithContentDescription("Playback position").fetchSemanticsNode().boundsInRoot)
        assertEquals("Timeline focus must not move the title", title,
            compose.onAllNodesWithText("Seek layout fixture")[1].fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Use left or right to seek").assertDoesNotExist()
        val capture = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "player-seek-hint-layout.png")
        capture.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        assertEquals(track, compose.onNodeWithContentDescription("Playback position").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun seekPreviewUsesTheExistingClockWithoutAnExtraTextRow() = withPlayer { controller ->
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        val track = compose.onNodeWithContentDescription("Playback position").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { controller._state.value = controller.state.value.copy(seekPreview = SeekPreview(30_000)) }
        compose.onNodeWithText("0:30").assertExists()
        assertEquals(track, compose.onNodeWithContentDescription("Playback position").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Seeking to", substring = true).assertDoesNotExist()
        compose.runOnIdle { controller.cancelSeek() }
        assertEquals(track, compose.onNodeWithContentDescription("Playback position").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Use left or right to seek").assertDoesNotExist()
    }

    private fun withPlayer(action: (AppController) -> Unit) {
        val controller = AppController(InstrumentationRegistry.getInstrumentation().targetContext, "https://example.invalid")
        val media = Media("seek-layout-fixture", "movie", name = "Seek layout fixture")
        var mounted by mutableStateOf(true)
        try {
            compose.runOnIdle {
                controller.playbackTitleDurationMillis = 60_000
                controller._state.value = AppState(loading = false, sessionRestoring = false, playerChromeVisible = true)
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
            action(controller)
        } finally {
            compose.runOnIdle { mounted = false }
            compose.waitForIdle()
            compose.runOnIdle { controller.player.close(); controller.scope.cancel() }
        }
    }
}
