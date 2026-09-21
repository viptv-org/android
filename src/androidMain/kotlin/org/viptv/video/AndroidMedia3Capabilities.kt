@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package org.viptv.video

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaDrm
import android.os.Build
import androidx.media3.common.C

internal fun probeMedia3Capabilities(): PlayerCapabilities {
    val decoders = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot(MediaCodecInfo::isEncoder)
    }.getOrDefault(emptyList())
    val mimeTypes = decoders.flatMap { it.supportedTypes.asIterable() }.map(String::lowercase).toSet()
    val directVideoLimits = media3DirectVideoLimits(decoders)
    val hardwareMimeTypes = decoders.filter { info ->
        if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated else !info.name.isSoftwareCodecName()
    }.flatMap { it.supportedTypes.asIterable() }.filter { it.startsWith("video/", ignoreCase = true) }
        .map(String::lowercase).toSet()
    val hardwareVideoCodecs = videoCodecs(hardwareMimeTypes)
    return PlayerCapabilities(
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
        subtitleFormats = setOf("vtt", "srt", "ssa", "ass", "ttml", "tx3g", "cea608", "cea708", "dvb", "pgs"),
        adaptiveProtocols = setOf("hls", "dash"),
        drmSchemes = buildSet {
            if (runCatching { MediaDrm.isCryptoSchemeSupported(C.WIDEVINE_UUID) }.getOrDefault(false)) add("widevine")
            if (runCatching { MediaDrm.isCryptoSchemeSupported(C.CLEARKEY_UUID) }.getOrDefault(false)) add("clearkey")
        },
        hardwareAcceleratedVideoCodecs = hardwareVideoCodecs,
        supportsAudioTrackSelection = true,
        supportsSubtitleTrackSelection = true,
        supportsVideoTrackSelection = true,
        supportsExternalSubtitles = true,
        supportsLive = true,
        supportsSeekableLive = true,
        supportsPlaybackRate = true,
        supportsPictureInPicture = Build.VERSION.SDK_INT >= 26,
        supportsHdr = false,
        supportsAudioPassthrough = false,
        supportsMovableSurface = true,
        supportsSurfaceReattachment = true,
        supportsCompositedOverlays = true,
        supportedLivePolicies = LivePlaybackPolicy.entries.toSet(),
        hardwareAcceleration = if (hardwareVideoCodecs.isNotEmpty()) {
            HardwareAcceleration.DecodeAndRender
        } else {
            HardwareAcceleration.Unknown
        },
        maxVideoWidth = directVideoLimits?.maxWidth,
        maxVideoHeight = directVideoLimits?.maxHeight,
        supportsHevcSdr = directVideoLimits?.supportsHevcSdr == true,
    )
}

/**
 * The server accepts one size limit for both H264 and HEVC direct delivery. Use the
 * intersection of the actual H264 decoder and the HEVC Main decoder when both are
 * advertised, rather than claiming a size supported by only one codec.
 */
private fun media3DirectVideoLimits(decoders: List<MediaCodecInfo>): Media3DirectVideoLimits? {
    val codecLimits = decoders.flatMap { decoder ->
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
    }
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

private data class Media3DecoderLimit(
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

private fun String.isSoftwareCodecName(): Boolean {
    val normalized = lowercase()
    return normalized.startsWith("omx.google.") || normalized.startsWith("c2.android.") ||
        normalized.startsWith("c2.google.") || ".sw." in normalized
}
