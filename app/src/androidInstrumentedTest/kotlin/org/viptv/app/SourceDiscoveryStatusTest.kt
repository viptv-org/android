package org.viptv.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceDiscoveryStatusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun progressiveArrivalKeepsBusyStatusUntilDiscoveryCompletes() {
        val loading = mutableStateOf(true)
        val hasSources = mutableStateOf(false)
        compose.setContent { SourceDiscoveryStatus(loading.value, hasSources.value) }
        val status = compose.onNodeWithTag("source-discovery-status")
        val initialHeight = status.fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithText("Finding sources").assertExists()
        compose.onNodeWithTag("source-discovery-spinner").assertExists()
        compose.runOnIdle { hasSources.value = true }
        compose.onNodeWithText("Finding sources").assertDoesNotExist()
        compose.onNodeWithText("Still checking sources").assertExists()
        compose.onNodeWithTag("source-discovery-spinner").assertExists()
        assertEquals(initialHeight, status.fetchSemanticsNode().boundsInRoot.height)
        compose.runOnIdle { loading.value = false }
        compose.onNodeWithText("Still checking sources").assertDoesNotExist()
        compose.onNodeWithTag("source-discovery-spinner").assertDoesNotExist()
        assertEquals(initialHeight, status.fetchSemanticsNode().boundsInRoot.height)
    }
}
