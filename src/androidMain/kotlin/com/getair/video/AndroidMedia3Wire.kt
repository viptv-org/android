@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.getair.video

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException

internal fun PlaybackSource.toMediaItem(
    resilientBufferConfig: AndroidMedia3ResilientBufferConfig,
): MediaItem {
    val subtitles = externalSubtitles.map { subtitle ->
        var selectionFlags = 0
        if (subtitle.isDefault) selectionFlags = selectionFlags or C.SELECTION_FLAG_DEFAULT
        if (subtitle.isForced) selectionFlags = selectionFlags or C.SELECTION_FLAG_FORCED
        MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.uri))
            .setId(subtitle.id)
            .setMimeType(subtitle.mimeType)
            .setLanguage(subtitle.language)
            .setLabel(subtitle.label)
            .setSelectionFlags(selectionFlags)
            .build()
    }
    return MediaItem.Builder()
        .setUri(Uri.parse(uri))
        .setMimeType(mimeType)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        .setSubtitleConfigurations(subtitles)
        .apply {
            if (kindHint != PlaybackKind.OnDemand) {
                media3LiveConfiguration(options.livePolicy, resilientBufferConfig)?.let(::setLiveConfiguration)
            }
        }
        .build()
}

/** Balanced deliberately leaves manifest/Media3 defaults untouched. Target offset is not a promise
 * about buffered-ahead media; [PlaybackStatistics.bufferedAheadMillis] reports that separately. */
internal fun media3LiveConfiguration(
    policy: LivePlaybackPolicy,
    resilientBufferConfig: AndroidMedia3ResilientBufferConfig = AndroidMedia3ResilientBufferConfig(),
): MediaItem.LiveConfiguration? = media3LivePolicyTuning(policy, resilientBufferConfig)
    .targetLiveOffsetMillis
    ?.let { MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(it.toLong()).build() }

internal fun shouldRecoverMedia3BehindLiveWindow(
    errorCode: Int,
    kindHint: PlaybackKind?,
    recoveryInProgress: Boolean,
): Boolean = errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW &&
    kindHint != PlaybackKind.OnDemand &&
    !recoveryInProgress

internal fun PlaybackException.toAirError(): PlaybackError = media3ErrorCodeToAir(errorCode)

internal fun media3ErrorCodeToAir(errorCode: Int): PlaybackError = when (errorCode) {
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> PlaybackError(
        PlaybackErrorCode.UnsupportedContainer,
        "Media3 does not support this container on the current device",
        recoverable = true,
        suggestedBackend = "mpv",
    )
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> PlaybackError(
        PlaybackErrorCode.UnsupportedCodec,
        "Media3 does not support a required codec on the current device",
        recoverable = true,
        suggestedBackend = "mpv",
    )
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> PlaybackError(
        PlaybackErrorCode.Decode,
        "Media3 could not decode the media",
        recoverable = true,
        suggestedBackend = "mpv",
    )
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> PlaybackError(
        PlaybackErrorCode.Network,
        "Media3 could not load the media",
        recoverable = true,
    )
    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> PlaybackError(
        PlaybackErrorCode.Network,
        "Media3 fell behind the live window",
        recoverable = true,
    )
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> PlaybackError(
        PlaybackErrorCode.Source,
        "Media3 cannot access the media source",
        recoverable = false,
    )
    else -> PlaybackError(PlaybackErrorCode.Internal, "Media3 playback failed", recoverable = false)
}

internal fun media3Timeline(
    isLive: Boolean,
    isSeekable: Boolean,
    durationMillis: Long?,
    kindHint: PlaybackKind? = null,
    windowPositionInFirstPeriodMillis: Long = 0,
): PlaybackTimeline = when {
    kindHint == PlaybackKind.Live -> PlaybackTimeline(PlaybackKind.Live, liveEdgeMillis = durationMillis)
    kindHint == PlaybackKind.SeekableLive -> PlaybackTimeline(
        kind = PlaybackKind.SeekableLive,
        seekableRange = durationMillis?.let { SeekableRange(0, it) },
        liveEdgeMillis = durationMillis,
    )
    kindHint == PlaybackKind.OnDemand -> {
        val windowStart = windowPositionInFirstPeriodMillis
            .takeUnless { it == C.TIME_UNSET || it < 0 }
            ?: 0
        val windowEnd = durationMillis?.let { windowStart.saturatingAdd(it) } ?: windowStart
        PlaybackTimeline(
            kind = PlaybackKind.OnDemand,
            durationMillis = windowEnd,
            seekableRange = SeekableRange(windowStart, windowEnd),
        )
    }
    isLive && isSeekable -> PlaybackTimeline(
        kind = PlaybackKind.SeekableLive,
        seekableRange = durationMillis?.let { SeekableRange(0, it) },
        liveEdgeMillis = durationMillis,
    )
    isLive -> PlaybackTimeline(PlaybackKind.Live, liveEdgeMillis = durationMillis)
    else -> PlaybackTimeline(PlaybackKind.OnDemand, durationMillis = durationMillis ?: 0)
}

/**
 * Converts Media3's current-window coordinate to a stable coordinate within one playback
 * session. `Timeline.Window.positionInFirstPeriodMs` is the native offset of the rolling
 * window; it is not wall-clock time and remains valid while playback is paused.
 */
internal fun media3SessionPositionMillis(
    kindHint: PlaybackKind?,
    nativePositionMillis: Long,
    windowPositionInFirstPeriodMillis: Long,
): Long {
    val native = nativePositionMillis.coerceAtLeast(0)
    if (kindHint != PlaybackKind.OnDemand) return native
    val offset = windowPositionInFirstPeriodMillis.takeUnless { it == C.TIME_UNSET || it < 0 } ?: 0
    return native.saturatingAdd(offset)
}

/** The inverse used for a direct native seek within the currently available VOD HLS window. */
internal fun media3NativeSeekPositionMillis(
    kindHint: PlaybackKind?,
    sessionPositionMillis: Long,
    windowPositionInFirstPeriodMillis: Long,
): Long {
    val session = sessionPositionMillis.coerceAtLeast(0)
    if (kindHint != PlaybackKind.OnDemand) return session
    val offset = windowPositionInFirstPeriodMillis.takeUnless { it == C.TIME_UNSET || it < 0 } ?: 0
    return (session - offset).coerceAtLeast(0)
}

private fun Long.saturatingAdd(other: Long): Long =
    if (Long.MAX_VALUE - this < other) Long.MAX_VALUE else this + other
