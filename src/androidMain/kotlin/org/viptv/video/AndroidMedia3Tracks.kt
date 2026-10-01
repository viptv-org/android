@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package org.viptv.video

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup

internal data class TrackTarget(val type: Int, val group: TrackGroup, val trackIndex: Int)
internal data class TrackSnapshot(
    val audio: List<AudioTrack>,
    val subtitles: List<SubtitleTrack>,
    val video: List<VideoTrack>,
    val selectedAudio: String?,
    val selectedSubtitle: String?,
    val selectedVideo: String?,
)

internal fun Format.toAudioTrack(id: String, index: Int) = AudioTrack(
    id = id,
    label = trackLabel("Audio", index),
    language = language,
    isDefault = selectionFlags and C.SELECTION_FLAG_DEFAULT != 0,
    isForced = selectionFlags and C.SELECTION_FLAG_FORCED != 0,
    channels = channelCount.takeIf { it > 0 },
    codec = codecs ?: sampleMimeType,
)

internal fun Format.toSubtitleTrack(id: String, index: Int, externalIds: Set<String>) = SubtitleTrack(
    id = id,
    label = trackLabel("Subtitles", index),
    language = language,
    isDefault = selectionFlags and C.SELECTION_FLAG_DEFAULT != 0,
    isForced = selectionFlags and C.SELECTION_FLAG_FORCED != 0,
    format = media3SubtitleFormat(sampleMimeType, codecs),
    external = media3IsExternalSubtitle(this.id, externalIds),
)

/**
 * Media3 parses text during extraction and re-labels the sample MIME type as its internal cue
 * format, keeping the original subtitle MIME type in `codecs`; report the original.
 */
internal fun media3SubtitleFormat(sampleMimeType: String?, codecs: String?): String? =
    if (sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES) codecs ?: sampleMimeType else sampleMimeType

/** A merged sideload's format ID is prefixed with its source index, e.g. `1:<id>`. */
internal fun media3IsExternalSubtitle(formatId: String?, externalIds: Set<String>): Boolean =
    formatId != null && (formatId in externalIds || formatId.substringAfter(':', "") in externalIds)

internal fun Format.toVideoTrack(id: String, index: Int) = VideoTrack(
    id = id,
    label = media3VideoTrackLabel(label, height, codecs, index),
    language = language,
    isDefault = selectionFlags and C.SELECTION_FLAG_DEFAULT != 0,
    isForced = selectionFlags and C.SELECTION_FLAG_FORCED != 0,
    width = width.takeIf { it > 0 },
    height = height.takeIf { it > 0 },
    bitrate = bitrate.takeIf { it > 0 }?.toLong(),
    codec = codecs ?: sampleMimeType,
)

internal fun media3VideoTrackLabel(label: String?, height: Int, codec: String?, index: Int): String =
    label?.takeIf(String::isNotBlank)
        ?: height.takeIf { it > 0 }?.let { "${it}p" }
        ?: codec
        ?: "Video ${index + 1}"

internal fun Format.trackLabel(type: String, index: Int): String = label ?: language ?: "$type ${index + 1}"
