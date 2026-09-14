package org.viptv.app

import org.json.JSONObject
import org.viptv.core.wire.CoreJson
import org.viptv.core.wire.MediaItem
import org.viptv.core.wire.MediaSource
import org.viptv.core.wire.CardPresentation
import org.viptv.core.wire.MediaPresentation
import org.viptv.core.wire.MediaTrack
import org.viptv.core.wire.PlaybackSession
import uniffi.viptv_core.normalize
import kotlin.math.roundToLong

/** View adapters only: every backend alias, default and policy is normalized in native Rust. */
internal object CoreModels {
    fun mediaNormalized(item: MediaItem): Media = item.view()
    fun media(value: JSONObject): Media = CoreJson.decode<MediaItem>(normalize("media", value.toString(), "")).view()
    fun source(value: JSONObject): Source = CoreJson.decode<MediaSource>(normalize("source", value.toString(), "")).let {
        val display = CoreJson.decode<org.viptv.core.wire.SourcePresentation>(normalize("sourceDisplay", CoreJson.encode(it), ""))
        Source(it.id, it.provider.orEmpty(), display.title, display.body, it.sourceAddonId, it.sourceFingerprint, it.quality, it.audio)
    }
    fun catalog(value: JSONObject): DiscoverCatalog? {
        val item = CoreJson.decode<org.viptv.core.wire.Catalog>(normalize("catalog", value.toString(), ""))
        val addon = item.addonKey ?: return null
        return DiscoverCatalog(CatalogKey(addon, item.type.name.lowercase(), item.id), item.name, item.supportsSearch, item.supportsSkip,
            item.extras.filterNot { it.name == "skip" }.map {
                CatalogFilter(it.name, when (it.name) { "search" -> CatalogFilterKind.Search; "genre" -> CatalogFilterKind.Genre; else -> if (it.options.isEmpty()) CatalogFilterKind.FreeText else CatalogFilterKind.Choice },
                    it.required, it.options, it.defaultValue, it.optionsLimit?.toInt())
            })
    }
    fun profileNormalized(item: org.viptv.core.wire.Profile): Profile {
        return Profile(item.id, item.name, item.avatar, item.kid == true, item.primary == true, item.avatarStyle.orEmpty(), item.avatarChoice?.toInt(), item.setupComplete == true)
    }
    fun profile(value: JSONObject): Profile = profileNormalized(CoreJson.decode<org.viptv.core.wire.Profile>(normalize("profile", value.toString(), "")))
    fun playback(value: JSONObject, origin: String): PlaybackLaunch = CoreJson.decode<PlaybackSession>(normalize("playback", value.toString(), origin)).let {
        PlaybackLaunch(it.id, it.url, headers = it.headers, format = it.format, mode = it.mode, videoMode = it.videoMode, audioMode = it.audioMode,
            positionMillis = (it.position * 1000).roundToLong(), durationMillis = it.duration.takeIf { duration -> duration > 0 }?.let { duration -> (duration * 1000).roundToLong() }, live = it.live,
            audioTracks = it.audioTracks.map(::track), subtitleTracks = it.subtitleTracks.map(::track), subtitlesSupported = it.subtitlesSupported)
    }
    private fun track(item: MediaTrack) = PlaybackTrack(item.inputIndex.toInt(), item.codec, item.language, item.languageStatus, item.title, item.selected, item.supported, item.selectable)
    fun enrich(original: Media, metadata: Media): Media = CoreJson.decode<MediaItem>(normalize("enrichHome", JSONObject().put("original", JSONObject(original.normalizedJson())).put("metadata", JSONObject(metadata.normalizedJson())).toString(), "")).view()
    fun card(media: Media, queue: Boolean = false): CardPresentation = CoreJson.decode(normalize("cardPresentation", JSONObject().put("item", JSONObject(media.normalizedJson())).put("context", if (queue) "queue" else "catalog").toString(), ""))
    fun presentation(media: Media): MediaPresentation = CoreJson.decode(normalize("presentation", media.normalizedJson(), ""))
    fun itemRequest(media: Media): JSONObject = JSONObject(normalize("itemRequest", media.normalizedJson(), ""))
}

private fun MediaItem.view(): Media = Media(
    id = id, type = type.name.lowercase(), name = name, poster = poster, description = description,
    positionMillis = ((position ?: 0.0) * 1000).roundToLong(), durationMillis = duration?.let { (it * 1000).roundToLong() },
    seriesId = seriesId, season = season?.toInt(), episode = episode?.toInt(), sourceAddonId = sourceAddonId,
    sourceFingerprint = sourceFingerprint, episodes = episodes.map { it.view() }, episodeTitle = episodeTitle,
    queueStatus = queueStatus, previousEpisode = previousEpisode?.view(), backdrop = background, thumbnail = thumbnail,
    year = year?.toInt()?.toString(), runtime = runtime, genres = genres, credits = credits, watched = watched == true,
    imdbRating = imdbRating, posterShape = posterShape, updatedAtMillis = updatedAtMillis?.toLong(), releasedAtMillis = releasedAtMillis?.toLong(), coreItem = this,
)

/** Copies carry current progress/artwork into the generated DTO without re-reading backend JSON. */
internal fun Media.normalizedJson(): String {
    val item = coreItem ?: CoreJson.decode<MediaItem>(normalize("media", JSONObject().put("id", id).put("type", type).put("name", name).toString(), ""))
    return CoreJson.encode(item.copy(id = id, type = org.viptv.core.wire.MediaKind.valueOf(type.uppercase()), name = name, poster = poster, background = backdrop, thumbnail = thumbnail,
        position = positionMillis / 1000.0, duration = durationMillis?.let { it / 1000.0 }, season = season?.toDouble(), episode = episode?.toDouble(),
        seriesId = seriesId, sourceAddonId = sourceAddonId, sourceFingerprint = sourceFingerprint, queueStatus = queueStatus,
        previousEpisode = previousEpisode?.let { CoreJson.decode<MediaItem>(it.normalizedJson()) }, episodeTitle = episodeTitle, watched = watched, description = description, genres = genres, credits = credits, runtime = runtime, imdbRating = imdbRating, posterShape = posterShape))
}

internal object CorePolicy {
    fun value(kind: String, input: JSONObject): Any? = org.json.JSONTokener(normalize(kind, input.toString(), "")).nextValue().takeUnless { it == JSONObject.NULL }
    fun source(source: Source) = JSONObject().put("id", source.id).putOpt("sourceAddonId", source.addonId).putOpt("sourceFingerprint", source.fingerprint)
    fun sources(sources: List<Source>) = org.json.JSONArray().also { array -> sources.forEach { array.put(source(it)) } }
}
