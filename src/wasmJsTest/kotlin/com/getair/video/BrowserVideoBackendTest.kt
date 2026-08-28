package com.getair.video

import kotlinx.browser.document
import kotlinx.coroutines.test.runTest
import org.w3c.dom.HTMLVideoElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BrowserVideoBackendTest {
    @Test
    fun ownsCallerElementAndReportsOnlyRuntimeContainerCapabilities() = runTest {
        if (!isBrowserRuntime()) return@runTest
        val element = document.createElement("video") as HTMLVideoElement
        val factory = BrowserVideoBackendFactory()
        val player = factory.createBrowserPlayer(element)
        try {
            assertSame(element, player.videoElement)
            assertFalse(element.controls)
            assertTrue(element.playsInline)
            val capabilities = factory.probe()
            assertTrue(capabilities.supportsMovableSurface)
            assertTrue(capabilities.supportsSurfaceReattachment)
            assertTrue(capabilities.supportsCompositedOverlays)
            assertFalse(capabilities.supportsPictureInPicture)
        } finally {
            player.close()
        }
    }

    @Test
    fun rejectsPrivateHeadersBeforeStartingBrowserNetworkWork() = runTest {
        if (!isBrowserRuntime()) return@runTest
        val player = BrowserVideoBackendFactory().createBrowserPlayer()
        try {
            val failure = assertFailsWith<PlaybackFailure> {
                player.open(
                    PlaybackSource(
                        uri = "https://media.invalid/live.m3u8",
                        headers = mapOf("Authorization" to "secret"),
                        kindHint = PlaybackKind.Live,
                    ),
                )
            }
            assertEquals(PlaybackErrorCode.Source, failure.error.code)
            assertFalse("secret" in failure.message.orEmpty())
            assertFalse("media.invalid" in failure.message.orEmpty())
        } finally {
            player.close()
        }
    }
}

private fun isBrowserRuntime(): Boolean = js("typeof document !== 'undefined'")
