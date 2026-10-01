package org.viptv.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidMedia3CapabilitiesTest {
    private val hardwareAvc = Media3DecoderFacts(
        mimeTypes = setOf("video/avc"),
        hardwareAccelerated = true,
        limits = listOf(Media3DecoderLimit("video/avc", 3840, 2160, supportsHevcSdr = false)),
    )
    private val softwareHevc = Media3DecoderFacts(
        mimeTypes = setOf("video/hevc"),
        hardwareAccelerated = false,
        limits = listOf(Media3DecoderLimit("video/hevc", 1920, 1080, supportsHevcSdr = true)),
    )
    private val aac = Media3DecoderFacts(setOf("audio/mp4a-latm"), hardwareAccelerated = false)

    @Test
    fun pictureInPictureRequiresTheSystemFeatureAndApi26() {
        assertFalse(capabilities(sdkInt = 34, pip = false).supportsPictureInPicture)
        assertFalse(capabilities(sdkInt = 25, pip = true).supportsPictureInPicture)
        assertTrue(capabilities(sdkInt = 26, pip = true).supportsPictureInPicture)
    }

    @Test
    fun hardwareDecodeComesOnlyFromPlatformReportedDecoders() {
        val modern = capabilities(sdkInt = 29, decoders = listOf(hardwareAvc, softwareHevc, aac))
        assertEquals(setOf("h264", "hevc"), modern.videoCodecs)
        assertEquals(setOf("h264"), modern.hardwareAcceleratedVideoCodecs)
        // No API proves the render path, so decoder presence never becomes DecodeAndRender.
        assertEquals(HardwareAcceleration.Decode, modern.hardwareAcceleration)

        val softwareOnly = capabilities(sdkInt = 33, decoders = listOf(softwareHevc, aac))
        assertEquals(emptySet(), softwareOnly.hardwareAcceleratedVideoCodecs)
        assertEquals(HardwareAcceleration.None, softwareOnly.hardwareAcceleration)
    }

    @Test
    fun hardwareAccelerationIsUnknownWithoutAPlatformApiOrDecoderList() {
        val legacy = capabilities(
            sdkInt = 28,
            decoders = listOf(hardwareAvc.copy(hardwareAccelerated = null), aac),
        )
        assertEquals(HardwareAcceleration.Unknown, legacy.hardwareAcceleration)
        assertEquals(emptySet(), legacy.hardwareAcceleratedVideoCodecs)
        assertEquals(setOf("h264"), legacy.videoCodecs)

        val unreadable = capabilities(sdkInt = 34, decoders = emptyList())
        assertEquals(HardwareAcceleration.Unknown, unreadable.hardwareAcceleration)
        assertEquals(emptySet(), unreadable.videoCodecs)
        assertNull(unreadable.maxVideoWidth)
    }

    @Test
    fun subtitleFormatsAreTextOnlyWithoutBitmapClaims() {
        val formats = capabilities().subtitleFormats
        assertEquals(setOf("vtt", "srt", "ssa", "ass", "ttml", "tx3g", "cea608", "cea708"), formats)
        assertFalse("pgs" in formats)
        assertFalse("dvb" in formats)
    }

    @Test
    fun unvalidatedOrUnexposedFeaturesAreReportedConservatively() {
        val capabilities = capabilities(sdkInt = 36, pip = true, decoders = listOf(hardwareAvc, softwareHevc, aac))
        assertFalse(capabilities.supportsSeekableLive)
        assertFalse(capabilities.supportsSurfaceReattachment)
        assertFalse(capabilities.supportsPlaybackRate)
        assertFalse(capabilities.supportsHdr)
        assertFalse(capabilities.supportsAudioPassthrough)
        assertTrue(capabilities.supportsLive)
    }

    @Test
    fun drmAndDirectLimitsAreRuntimeFacts() {
        val none = capabilities(decoders = listOf(hardwareAvc, softwareHevc))
        assertEquals(emptySet(), none.drmSchemes)
        // H264 and HEVC Main share one server limit: the intersection of both decoders.
        assertEquals(1920, none.maxVideoWidth)
        assertEquals(1080, none.maxVideoHeight)
        assertTrue(none.supportsHevcSdr)

        val widevine = media3Capabilities(
            Media3PlatformFacts(sdkInt = 34, pictureInPictureFeature = false, decoders = emptyList(), widevine = true),
        )
        assertEquals(setOf("widevine"), widevine.drmSchemes)
    }

    private fun capabilities(
        sdkInt: Int = 34,
        pip: Boolean = false,
        decoders: List<Media3DecoderFacts> = listOf(hardwareAvc, aac),
    ): PlayerCapabilities = media3Capabilities(Media3PlatformFacts(sdkInt, pip, decoders))
}
