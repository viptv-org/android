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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackDeliveryFallbackTest {
    private fun failure(code: PlaybackErrorCode) = PlaybackFailure(PlaybackError(code, "Safe native failure", true))
    private fun launch(id: String, direct: Boolean) = PlaybackLaunch(id, "https://fixture.invalid/media", deliveryKind = if (direct) "direct" else "gateway", positionMillis = 42_000)

    @Test fun `selected delivery progresses at most direct then gateway then conversion`() = runTest {
        val attempts = mutableListOf<PlaybackDeliveryOptions>()
        val effects = mutableListOf<String>()
        val result = openPlaybackDelivery(PlaybackDeliveryOptions(),
            prepare = { options -> attempts.add(options); launch("lease-${attempts.size}", !options.forceGateway) },
            open = { delivery -> effects.add("open:${delivery.sessionId}"); if (attempts.size < 3) throw failure(PlaybackErrorCode.UnsupportedCodec) },
            release = { effects.add("release:$it") }, isCurrent = { true })
        assertEquals(listOf(PlaybackDeliveryOptions(), PlaybackDeliveryOptions(true), PlaybackDeliveryOptions(true, true)), attempts)
        assertEquals(listOf("open:lease-1", "release:lease-1", "open:lease-2", "release:lease-2", "open:lease-3"), effects)
        assertEquals(42_000, result.first.positionMillis)
        assertEquals(PlaybackDeliveryOptions(true, true), result.second)
    }

    @Test fun `native network failure permits proxy once but never forces encoding`() = runTest {
        val attempts = mutableListOf<PlaybackDeliveryOptions>()
        val released = mutableListOf<String>()
        assertFailsWith<PlaybackFailure> {
            openPlaybackDelivery(PlaybackDeliveryOptions(),
                prepare = { attempts.add(it); launch("lease-${attempts.size}", !it.forceGateway) },
                open = { throw failure(PlaybackErrorCode.Network) },
                release = { released.add(it) }, isCurrent = { true })
        }
        assertEquals(listOf(PlaybackDeliveryOptions(), PlaybackDeliveryOptions(true)), attempts)
        assertEquals(listOf("lease-1", "lease-2"), released)
    }

    @Test fun `access and internal failures do not request another delivery`() {
        for (code in listOf(PlaybackErrorCode.Source, PlaybackErrorCode.Internal)) {
            assertNull(nextPlaybackDelivery(PlaybackDeliveryOptions(), true, code))
            assertNull(nextPlaybackDelivery(PlaybackDeliveryOptions(true), false, code))
        }
        assertNull(nextPlaybackDelivery(PlaybackDeliveryOptions(true, true), false, PlaybackErrorCode.Decode))
    }

    @Test fun `no gateway failure reaches the caller without another request`() = runTest {
        var count = 0
        val released = mutableListOf<String>()
        val error = assertFailsWith<GatewayError> {
            openPlaybackDelivery(PlaybackDeliveryOptions(),
                prepare = { if (++count == 2) throw GatewayError(409, "Configure a playback gateway.", "gateway_required"); launch("direct", true) },
                open = { throw failure(PlaybackErrorCode.UnsupportedContainer) },
                release = { released.add(it) }, isCurrent = { true })
        }
        assertEquals("gateway_required", error.code)
        assertEquals(2, count)
        assertEquals(listOf("direct"), released)
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
        val result = async {
            openPlaybackDelivery(PlaybackDeliveryOptions(), prepare = { launch("lease-${++count}", count == 1) },
                open = { if (count == 1) throw failure(PlaybackErrorCode.Decode) },
                release = { awaitCancellation() }, isCurrent = { true })
        }
        advanceUntilIdle()
        assertEquals(2, count)
        assertEquals(5_000, testScheduler.currentTime)
        assertEquals(PlaybackDeliveryOptions(true), result.await().second)
    }
}
