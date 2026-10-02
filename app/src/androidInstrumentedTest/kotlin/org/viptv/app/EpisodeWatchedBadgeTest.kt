package org.viptv.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpisodeWatchedBadgeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun completedEpisodeShowsBadgeOnTvWithoutHidingTitle() = renderEpisode(tv = true, watched = true)

    @Test fun completedEpisodeShowsBadgeOnPhoneWithoutHidingTitle() = renderEpisode(tv = false, watched = true)

    @Test fun partialEpisodeDoesNotClaimCompletion() = renderEpisode(tv = true, watched = false)

    @Test fun changingCardFactsFromWatchedToPartialShowsProgress() {
        var episode by mutableStateOf(Media("show:1:1059", "episode", name = "Fixture Show", season = 1,
            episode = 1059, episodeTitle = "The Future", positionMillis = 30_000,
            durationMillis = 120_000, watched = true))
        compose.setContent {
            CompositionLocalProvider(LocalTv provides true) {
                ViptvTheme(false, Color.White) {
                    EpisodeCard(episode, Modifier.width(360.dp), {}, {})
                }
            }
        }
        compose.onNodeWithTag("episode-watched-badge", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithTag("episode-progress", useUnmergedTree = true).assertCountEquals(0)
        compose.runOnIdle { episode = episode.copy(watched = false) }
        compose.onAllNodesWithTag("episode-watched-badge", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("episode-progress", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("The Future").assertExists()
    }

    @Test fun childCompletionOverridesAnUnwatchedParentArtworkContext() {
        val parent = Media("show", "series", name = "Fixture Show", watched = false)
        val child = Media("show:1:1059", "episode", name = "Fixture Show", seriesId = "show",
            season = 1, episode = 1059, episodeTitle = "The Future", watched = true)
        compose.setContent {
            CompositionLocalProvider(LocalTv provides true) {
                ViptvTheme(false, Color.White) {
                    EpisodeCard(child, Modifier.width(360.dp), {}, {}, artworkContext = parent)
                }
            }
        }
        compose.onNodeWithTag("episode-watched-badge", useUnmergedTree = true).assertExists()
    }

    private fun renderEpisode(tv: Boolean, watched: Boolean) {
        val episode = Media("show:1:1059", "episode", name = "Fixture Show", season = 1, episode = 1059,
            episodeTitle = "The Future", positionMillis = 30_000, durationMillis = 120_000, watched = watched)
        compose.setContent {
            CompositionLocalProvider(LocalTv provides tv) {
                ViptvTheme(false, Color.White) {
                    Box(Modifier.fillMaxSize().background(LocalGround.current)) {
                        EpisodeCard(episode, Modifier.width(if (tv) 360.dp else 320.dp), {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText("The Future").assertExists()
        if (watched) {
            compose.onNodeWithTag("episode-watched-badge", useUnmergedTree = true).assertExists()
            compose.onNodeWithText("Watched").assertExists()
            compose.onAllNodesWithTag("episode-progress", useUnmergedTree = true).assertCountEquals(0)
            val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
            File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                if (tv) "episode-watched-tv.png" else "episode-watched-phone.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } else {
            compose.onAllNodesWithTag("episode-watched-badge", useUnmergedTree = true).assertCountEquals(0)
            compose.onNodeWithTag("episode-progress", useUnmergedTree = true).assertExists()
        }
    }
}
