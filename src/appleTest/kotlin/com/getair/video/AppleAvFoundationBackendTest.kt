package com.getair.video

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppleAvFoundationBackendTest {
    @Test
    fun reportsOnlyCapabilitiesOwnedByTheBackend() = runTest {
        val capabilities = AppleAvFoundationBackendFactory().probe()

        assertTrue(capabilities.supportsMovableSurface)
        assertTrue(capabilities.supportsSurfaceReattachment)
        assertTrue(capabilities.supportsCompositedOverlays)
        assertTrue(capabilities.supportsLive)
        assertTrue(capabilities.supportsSeekableLive)
        assertFalse(capabilities.supportsExternalSubtitles)
        assertFalse(capabilities.supportsVideoTrackSelection)
        assertFalse(capabilities.supportsPictureInPicture)
        assertFalse("mkv" in capabilities.containers)
    }

    @Test
    fun explicitLiveHintNeverCreatesASeekableTimeline() {
        val timeline = appleTimeline(
            durationMillis = 60_000,
            seekableRange = SeekableRange(10_000, 60_000),
            hint = PlaybackKind.Live,
        )

        assertEquals(PlaybackKind.Live, timeline.kind)
        assertNull(timeline.seekableRange)
        assertFalse(timeline.showSeekBar)
    }

    @Test
    fun dvrRangeCreatesSeekableLiveWithoutFiniteDuration() {
        val timeline = appleTimeline(
            durationMillis = null,
            seekableRange = SeekableRange(20_000, 80_000),
            hint = null,
        )

        assertEquals(PlaybackKind.SeekableLive, timeline.kind)
        assertEquals(80_000, timeline.liveEdgeMillis)
        assertTrue(timeline.showSeekBar)
    }
}
