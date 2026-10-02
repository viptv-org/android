package org.viptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class TitleSourceControlTest {
    @get:Rule val compose = createComposeRule()
    private val source = Source("s", "LordStreams", quality = "1080p", displayResolved = true,
        providerLabel = "LordStreams", providerKey = "addon:1")

    @Test fun phoneShowsProviderQualityAndCountWithManualPickerAction() {
        var selected = 0
        render(false, SourceSummary("k", source, 12, false)) { selected++ }
        compose.onNodeWithText("1080p", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("LordStreams", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("12 sources", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose source").performClick()
        compose.runOnIdle { assertEquals(1, selected) }
    }

    @Test fun televisionShowsSourceFactsAndKeepsManualPickerAction() {
        var selected = 0
        render(true, SourceSummary("k", source, 1, true)) { selected++ }
        compose.onNodeWithText("1080p", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("LordStreams", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose source").performClick()
        compose.runOnIdle { assertEquals(1, selected) }
    }

    @Test fun failureHasSafeCopyAndRemainsActionable() {
        var selected = 0
        render(false, SourceSummary("k", null, 0, true, failed = true)) { selected++ }
        compose.onNodeWithText("Sources unavailable", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose source").performClick()
        compose.runOnIdle { assertEquals(1, selected) }
    }

    @Test fun emptyDiscoveryHasRecoveryCopy() {
        render(false, SourceSummary("k", null, 0, true)) {}
        compose.onNodeWithText("No sources found", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun render(tv: Boolean, summary: SourceSummary?, onClick: () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalTv provides tv) {
                ViptvTheme(false, Color.White) {
                    Box(Modifier.width(350.dp)) { TitleSourceControl(summary, onClick) }
                }
            }
        }
    }
}
