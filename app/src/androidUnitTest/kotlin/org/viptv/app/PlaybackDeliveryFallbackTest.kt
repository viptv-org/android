package org.viptv.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.viptv.video.PlaybackError
import org.viptv.video.PlaybackErrorCode
import org.viptv.video.PlaybackFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackDeliveryFallbackTest {
    private fun failure(code: PlaybackErrorCode) = PlaybackFailure(PlaybackError(code, "Safe native failure", true))
    private fun launch(id: String, direct: Boolean) = PlaybackLaunch(id, "https://fixture.invalid/media", deliveryKind = if (direct) "direct" else "gateway", positionMillis = 42_000)

    @Test fun `native decoder refusal releases selected delivery and reaches explicit recovery`() = runTest {
        val attempts = mutableListOf<PlaybackDeliveryOptions>()
        val effects = mutableListOf<String>()
        val error = assertFailsWith<PlaybackFailure> { openPlaybackDelivery(PlaybackDeliveryOptions(),
            prepare = { options -> attempts.add(options); launch("lease-${attempts.size}", !options.forceGateway) },
            open = { delivery -> effects.add("open:${delivery.sessionId}"); throw failure(PlaybackErrorCode.UnsupportedCodec) },
            release = { effects.add("release:$it") }, isCurrent = { true }) }
        assertEquals(listOf(PlaybackDeliveryOptions()), attempts)
        assertEquals(listOf("open:lease-1", "release:lease-1"), effects)
        assertEquals(PlaybackErrorCode.UnsupportedCodec, error.error.code)
    }

    @Test fun `native network failure reaches explicit recovery without proxy or encoding`() = runTest {
        val attempts = mutableListOf<PlaybackDeliveryOptions>()
        val released = mutableListOf<String>()
        assertFailsWith<PlaybackFailure> {
            openPlaybackDelivery(PlaybackDeliveryOptions(),
                prepare = { attempts.add(it); launch("lease-${attempts.size}", !it.forceGateway) },
                open = { throw failure(PlaybackErrorCode.Network) },
                release = { released.add(it) }, isCurrent = { true })
        }
        assertEquals(listOf(PlaybackDeliveryOptions()), attempts)
        assertEquals(listOf("lease-1"), released)
    }

    @Test fun `all native failure kinds release their exact lease without requesting another delivery`() = runTest {
        for (direct in listOf(true, false)) for (code in listOf(PlaybackErrorCode.Source, PlaybackErrorCode.Internal,
            PlaybackErrorCode.UnsupportedContainer, PlaybackErrorCode.UnsupportedCodec, PlaybackErrorCode.Decode, PlaybackErrorCode.Network)) {
            var requests = 0
            val released = mutableListOf<String>()
            val error = assertFailsWith<PlaybackFailure> { openPlaybackDelivery(PlaybackDeliveryOptions(),
                prepare = { requests++; launch("lease", direct) }, open = { throw failure(code) },
                release = { released.add(it) }, isCurrent = { true }) }
            assertEquals(code, error.error.code)
            assertEquals(1, requests)
            assertEquals(listOf("lease"), released)
        }
    }

    @Test fun `server refusal reaches the caller without starting a native lease`() = runTest {
        var count = 0
        val released = mutableListOf<String>()
        val error = assertFailsWith<GatewayError> {
            openPlaybackDelivery(PlaybackDeliveryOptions(),
                prepare = { count++; throw GatewayError(409, "Configure a playback gateway.", "gateway_required") },
                open = { throw failure(PlaybackErrorCode.UnsupportedContainer) },
                release = { released.add(it) }, isCurrent = { true })
        }
        assertEquals("gateway_required", error.code)
        assertEquals(1, count)
        assertEquals(emptyList(), released)
    }

    @Test fun `cancellation releases admitted media outside the cancelled scope`() = runTest {
        val opened = CompletableDeferred<Unit>()
        val released = mutableListOf<String>()
        val job = launch {
            openPlaybackDelivery(PlaybackDeliveryOptions(), prepare = { launch("lease", true) },
                open = { opened.complete(Unit); awaitCancellation() }, release = { released.add(it) }, isCurrent = { true })
        }
        opened.await()
        job.cancelAndJoin()
        assertEquals(listOf("lease"), released)
    }

    @Test fun `obsolete admission is released without opening or escalating`() = runTest {
        var current = true
        var opened = false
        val released = mutableListOf<String>()
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            openPlaybackDelivery(PlaybackDeliveryOptions(), prepare = { current = false; launch("lease", true) },
                open = { opened = true }, release = { released.add(it) }, isCurrent = { current })
        }
        assertTrue(!opened)
        assertEquals(listOf("lease"), released)
    }

    @Test fun `failed release cannot hang retry beyond five seconds`() = runTest {
        var count = 0
        val result = async { assertFailsWith<PlaybackFailure> {
            openPlaybackDelivery(PlaybackDeliveryOptions(), prepare = { launch("lease-${++count}", true) },
                open = { if (count == 1) throw failure(PlaybackErrorCode.Decode) },
                release = { awaitCancellation() }, isCurrent = { true })
        } }
        advanceUntilIdle()
        assertEquals(1, count)
        assertEquals(5_000, testScheduler.currentTime)
        assertEquals(PlaybackErrorCode.Decode, result.await().error.code)
    }
}
