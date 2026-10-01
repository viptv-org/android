@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package org.viptv.video

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaDrm
import android.os.Build
import androidx.media3.common.C

/**
 * Reads the platform facts once and maps them through [media3Capabilities]. Every claim is either
 * derived from a runtime platform API, is a structural property of this library's Media3 wiring,
 * or is reported conservatively as unsupported until SPEC.md's device validation matrix records
 * evidence for it.
 */
internal fun probeMedia3Capabilities(context: Context): PlayerCapabilities =
    media3Capabilities(readMedia3PlatformFacts(context))

/** Runtime platform facts the capability mapping depends on; kept free of Android types for tests. */
internal data class Media3PlatformFacts(
    val sdkInt: Int,
    /** `PackageManager.FEATURE_PICTURE_IN_PICTURE`; low-RAM and many TV devices omit it. */
    val pictureInPictureFeature: Boolean,
    val decoders: List<Media3DecoderFacts>,
    val widevine: Boolean = false,
    val clearKey: Boolean = false,
)

internal data class Media3DecoderFacts(
    /** Lower-case MIME types the decoder advertises. */
    val mimeTypes: Set<String>,
    /**
     * `MediaCodecInfo.isHardwareAccelerated && !isSoftwareOnly` on API 29+. Null below API 29,
     * where the platform offers no API and codec-name heuristics are not evidence.
     */
    val hardwareAccelerated: Boolean?,
    val limits: List<Media3DecoderLimit> = emptyList(),
)

private fun readMedia3PlatformFacts(context: Context): Media3PlatformFacts {
    val sdkInt = Build.VERSION.SDK_INT
    val decoders = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot(MediaCodecInfo::isEncoder)
    }.getOrDefault(emptyList()).map { info ->
        Media3DecoderFacts(
            mimeTypes = info.supportedTypes.mapTo(mutableSetOf(), String::lowercase),
            hardwareAccelerated = if (Build.VERSION.SDK_INT >= 29) {
                runCatching { info.isHardwareAccelerated && !info.isSoftwareOnly }.getOrDefault(false)
            } else {
                null
            },
            limits = media3DecoderLimits(info),
        )
    }
    return Media3PlatformFacts(
        sdkInt = sdkInt,
        pictureInPictureFeature = Build.VERSION.SDK_INT >= 26 && runCatching {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        }.getOrDefault(false),
        decoders = decoders,
        widevine = runCatching { MediaDrm.isCryptoSchemeSupported(C.WIDEVINE_UUID) }.getOrDefault(false),
        clearKey = runCatching { MediaDrm.isCryptoSchemeSupported(C.CLEARKEY_UUID) }.getOrDefault(false),
    )
}

/**
 * Text subtitle formats Media3 parses into text cues, including sideloaded files. Bitmap formats
 * (PGS, DVB, VobSub) are deliberately absent: the public API exposes no cue or bitmap presentation
 * path, so claiming them would promise rendering that no embedding view can perform.
 */
internal val MEDIA3_TEXT_SUBTITLE_FORMATS: Set<String> =
    setOf("vtt", "srt", "ssa", "ass", "ttml", "tx3g", "cea608", "cea708")

internal fun media3Capabilities(facts: Media3PlatformFacts): PlayerCapabilities {
    val mimeTypes = facts.decoders.flatMapTo(mutableSetOf()) { it.mimeTypes }
    val directVideoLimits = media3DirectVideoLimits(facts.decoders.flatMap { it.limits })
    val hardwareKnown = facts.sdkInt >= 29 && facts.decoders.isNotEmpty()
    val hardwareVideoCodecs = if (hardwareKnown) {
        videoCodecs(
            facts.decoders.filter { it.hardwareAccelerated == true }
                .flatMapTo(mutableSetOf()) { decoder -> decoder.mimeTypes.filter { it.startsWith("video/") } },
        )
    } else {
        emptySet()
    }
    return PlayerCapabilities(
        // Media3's bundled extractors; structural, not a device decoder claim.
        containers = setOf("mp4", "m4v", "mov", "mkv", "webm", "mpegts", "ts", "flv", "ogg"),
        videoCodecs = videoCodecs(mimeTypes),
        audioCodecs = buildSet {
            if ("audio/mp4a-latm" in mimeTypes) add("aac")
            if ("audio/opus" in mimeTypes) add("opus")
            if ("audio/vorbis" in mimeTypes) add("vorbis")
            if ("audio/flac" in mimeTypes) add("flac")
            if ("audio/ac3" in mimeTypes) add("ac3")
            if ("audio/eac3" in mimeTypes || "audio/eac3-joc" in mimeTypes) add("eac3")
            if ("audio/mpeg" in mimeTypes) add("mp3")
        },
        subtitleFormats = MEDIA3_TEXT_SUBTITLE_FORMATS,
        adaptiveProtocols = setOf("hls", "dash"),
        drmSchemes = buildSet {
            if (facts.widevine) add("widevine")
            if (facts.clearKey) add("clearkey")
        },
        hardwareAcceleratedVideoCodecs = hardwareVideoCodecs,
        supportsAudioTrackSelection = true,
        supportsSubtitleTrackSelection = true,
        supportsVideoTrackSelection = true,
        supportsExternalSubtitles = true,
        supportsLive = true,
        // Media3 can expose DVR windows and the timeline reports them per source, but no platform
        // API proves seekable-live behaviour; claim it only after device validation.
        supportsSeekableLive = false,
        // VideoPlayer has no playback-rate control, so the capability cannot be exercised.
        supportsPlaybackRate = false,
        supportsPictureInPicture = facts.sdkInt >= 26 && facts.pictureInPictureFeature,
        supportsHdr = false,
        supportsAudioPassthrough = false,
        supportsMovableSurface = true,
        // Reattaching a released surface depends on codec setOutputSurface/reinit behaviour that
        // has no capability API; claim it only after device validation.
        supportsSurfaceReattachment = false,
        supportsCompositedOverlays = true,
        supportedLivePolicies = LivePlaybackPolicy.entries.toSet(),
        // The platform reports hardware decoders (API 29+) but no API proves a zero-copy render
        // path, so the strongest runtime-derived claim is Decode.
        hardwareAcceleration = when {
            !hardwareKnown -> HardwareAcceleration.Unknown
            hardwareVideoCodecs.isNotEmpty() -> HardwareAcceleration.Decode
            else -> HardwareAcceleration.None
        },
        maxVideoWidth = directVideoLimits?.maxWidth,
        maxVideoHeight = directVideoLimits?.maxHeight,
        supportsHevcSdr = directVideoLimits?.supportsHevcSdr == true,
    )
}

private fun media3DecoderLimits(decoder: MediaCodecInfo): List<Media3DecoderLimit> =
    decoder.supportedTypes.asIterable()
        .filter { it.equals("video/avc", ignoreCase = true) || it.equals("video/hevc", ignoreCase = true) }
        .mapNotNull { mimeType ->
            runCatching {
                val capabilities = decoder.getCapabilitiesForType(mimeType)
                val video = capabilities.videoCapabilities ?: return@runCatching null
                val maxWidth = video.supportedWidths.upper
                val maxHeight = video.supportedHeights.upper
                if (maxWidth < 2 || maxHeight < 2) return@runCatching null
                Media3DecoderLimit(
                    mimeType = mimeType.lowercase(),
                    maxWidth = maxWidth,
                    maxHeight = maxHeight,
                    supportsHevcSdr = mimeType.equals("video/hevc", ignoreCase = true) &&
                        capabilities.profileLevels.any { it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain },
                )
            }.getOrNull()
        }

/**
 * The server accepts one size limit for both H264 and HEVC direct delivery. Use the
 * intersection of the actual H264 decoder and the HEVC Main decoder when both are
 * advertised, rather than claiming a size supported by only one codec.
 */
private fun media3DirectVideoLimits(codecLimits: List<Media3DecoderLimit>): Media3DirectVideoLimits? {
    val h264 = codecLimits.filter { it.mimeType == "video/avc" }.maxByOrNull(Media3DecoderLimit::area)
        ?: return null
    val hevcSdr = codecLimits.filter { it.mimeType == "video/hevc" && it.supportsHevcSdr }
        .maxByOrNull(Media3DecoderLimit::area)
    return if (hevcSdr == null) {
        Media3DirectVideoLimits(h264.maxWidth, h264.maxHeight, supportsHevcSdr = false)
    } else {
        Media3DirectVideoLimits(
            maxWidth = minOf(h264.maxWidth, hevcSdr.maxWidth),
            maxHeight = minOf(h264.maxHeight, hevcSdr.maxHeight),
            supportsHevcSdr = true,
        )
    }
}

internal data class Media3DecoderLimit(
    val mimeType: String,
    val maxWidth: Int,
    val maxHeight: Int,
    val supportsHevcSdr: Boolean,
) {
    val area: Long get() = maxWidth.toLong() * maxHeight
}

private data class Media3DirectVideoLimits(
    val maxWidth: Int,
    val maxHeight: Int,
    val supportsHevcSdr: Boolean,
)

private fun videoCodecs(mimeTypes: Set<String>): Set<String> = buildSet {
    if ("video/avc" in mimeTypes) add("h264")
    if ("video/hevc" in mimeTypes) add("hevc")
    if ("video/av01" in mimeTypes) add("av1")
    if ("video/x-vnd.on2.vp9" in mimeTypes) add("vp9")
    if ("video/x-vnd.on2.vp8" in mimeTypes) add("vp8")
    if ("video/dolby-vision" in mimeTypes) add("dolby-vision")
}
