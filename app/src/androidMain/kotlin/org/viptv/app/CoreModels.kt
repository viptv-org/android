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
    fun source(value: JSONObject): Source = sourceNormalized(CoreJson.decode<MediaSource>(normalize("source", value.toString(), "")))
    fun sourceNormalized(it: MediaSource): Source {
        val display = CoreJson.decode<org.viptv.core.wire.SourcePresentation>(normalize("sourceDisplay", CoreJson.encode(it), ""))
        return Source(it.id, it.provider.orEmpty(), display.title, display.body, it.sourceAddonId, it.sourceFingerprint, it.quality, it.audio, displayResolved = true, providerKey = display.providerKey, providerLabel = display.providerLabel)
    }
    fun catalog(value: JSONObject): DiscoverCatalog? {
        val item = CoreJson.decode<org.viptv.core.wire.Catalog>(normalize("catalog", value.toString(), ""))
        val addon = item.addonKey ?: return null
        return DiscoverCatalog(CatalogKey(addon, item.type, item.id), item.name, item.supportsSearch, item.supportsSkip,
            item.extras.filterNot { it.name == "skip" }.map {
                CatalogFilter(it.name, when (it.name) { "search" -> CatalogFilterKind.Search; "genre" -> CatalogFilterKind.Genre; else -> if (it.options.isEmpty()) CatalogFilterKind.FreeText else CatalogFilterKind.Choice },
                    it.required, it.options, it.defaultValue, it.optionsLimit?.toInt())
            }, item.addonName)
    }
    fun profileNormalized(item: org.viptv.core.wire.Profile): Profile {
        return Profile(item.id, item.name, item.avatar, item.kid == true, item.primary == true, item.avatarStyle.orEmpty(), item.avatarChoice?.toInt(), item.setupComplete == true)
    }
    fun profile(value: JSONObject): Profile = profileNormalized(CoreJson.decode<org.viptv.core.wire.Profile>(normalize("profile", value.toString(), "")))
    fun playback(value: JSONObject, origin: String): PlaybackLaunch = playbackNormalized(CoreJson.decode<PlaybackSession>(normalize("playback", value.toString(), origin)))
    fun playbackNormalized(it: PlaybackSession): PlaybackLaunch {
        return PlaybackLaunch(it.id, it.url, headers = buildMap {
            putAll(it.headers)
            it.authorization?.let { auth ->
                putAll(auth.headers.orEmpty())
                auth.cookie?.let { value -> put("Cookie", value) }
                auth.userAgent?.let { value -> put("User-Agent", value) }
            }
        }, format = it.format, mode = it.mode, videoMode = it.videoMode, audioMode = it.audioMode,
            positionMillis = (it.position * 1000).roundToLong(), durationMillis = it.duration.takeIf { duration -> duration > 0 }?.let { duration -> (duration * 1000).roundToLong() }, live = it.live,
            audioTracks = it.audioTracks.map(::track), subtitleTracks = it.subtitleTracks.map(::track), subtitlesSupported = it.subtitlesSupported,
            deliveryKind = it.deliveryKind?.name?.lowercase(), preferredAudioLanguage = it.preferredAudioLanguage, preferredSubtitleLanguage = it.preferredSubtitleLanguage,
            subtitlesEnabled = it.deliveryKind?.let { _ -> it.preferredSubtitleLanguage != null || it.subtitleTracks.any { track -> track.selected } })
    }
    private fun track(item: MediaTrack) = PlaybackTrack(item.inputIndex.toInt(), item.codec, item.language, item.languageStatus, item.title, item.selected, item.supported, item.selectable)
    fun enrich(original: Media, metadata: Media): Media = CoreJson.decode<MediaItem>(normalize("enrichHome", JSONObject().put("original", JSONObject(original.normalizedJson())).put("metadata", JSONObject(metadata.normalizedJson())).toString(), "")).view()
    // The occurrence contributes scalar facts; the lookup contributes the one full episode catalog.
    fun enrichDetail(original: Media, metadata: Media): Media = CoreJson.decode<MediaItem>(normalize("enrichDetail", JSONObject().put("original", JSONObject(original.normalizedJson(includeEpisodes = false))).put("metadata", JSONObject(metadata.normalizedJson())).toString(), "")).view()
    fun mergeEpisodeProgress(details: Media, progress: List<Media>): Media {
        val input = JSONObject().put("seriesId", details.id)
            .put("episodes", org.json.JSONArray().also { rows -> details.episodes.forEach { rows.put(JSONObject(it.normalizedJson())) } })
            .put("history", org.json.JSONArray().also { rows -> progress.forEach { rows.put(JSONObject(it.normalizedJson())) } })
        val episodes = CoreJson.decode<List<MediaItem>>(normalize("mergeEpisodeProgress", input.toString(), "")).map { it.view() }
        return details.copy(episodes = episodes)
    }
    fun card(media: Media, queue: Boolean = false, failedImages: Set<String> = emptySet()): CardPresentation = CoreJson.decode(normalize("cardPresentation", JSONObject().put("item", JSONObject(media.normalizedJson(includeEpisodes = false))).put("context", if (queue) "queue" else "catalog").put("failedImages", org.json.JSONArray(failedImages.toList())).toString(), ""))
    fun presentation(media: Media): MediaPresentation = CoreJson.decode(normalize("presentation", media.normalizedJson(includeEpisodes = false), ""))
    fun initialEpisode(media: Media): Media? {
        val input = JSONObject().put("episodes", org.json.JSONArray().also { array -> media.episodes.forEach { array.put(JSONObject(it.normalizedJson())) } })
            .put("original", JSONObject().putOpt("season", media.season).putOpt("episode", media.episode))
            .put("now", System.currentTimeMillis())
        val result = normalize("initialEpisode", input.toString(), "")
        return if (result == "null") null else CoreJson.decode<MediaItem>(result).view()
    }
    fun itemRequest(media: Media): JSONObject = JSONObject(normalize("itemRequest", media.normalizedJson(includeEpisodes = false), ""))
}

private fun MediaItem.view(): Media = Media(
    id = id, type = type.name.lowercase(), name = name, poster = poster, description = description,
    positionMillis = ((position ?: 0.0) * 1000).roundToLong(), durationMillis = duration?.let { (it * 1000).roundToLong() },
    seriesId = seriesId, season = season?.toInt(), episode = episode?.toInt(), sourceAddonId = sourceAddonId,
    sourceFingerprint = sourceFingerprint, episodes = episodes.map { it.view() }, episodeTitle = episodeTitle,
    queueStatus = queueStatus, previousEpisode = previousEpisode?.view(), backdrop = background, thumbnail = thumbnail,
    year = year?.toInt()?.toString(), runtime = runtime, genres = genres, credits = credits, watched = watched == true,
    resumeActive = resumeActive, completionOnly = completionOnly, watchDateKnown = watchDateKnown,
    imdbRating = imdbRating, posterShape = posterShape, updatedAtMillis = updatedAtMillis?.toLong(), releasedAtMillis = releasedAtMillis?.toLong(), coreItem = this,
)

/** Copies carry current progress/artwork into the generated DTO without re-reading backend JSON. */
internal fun Media.normalizedJson(includeEpisodes: Boolean = true): String = CoreJson.encode(wireItem(includeEpisodes))

private fun Media.wireItem(includeEpisodes: Boolean = true): MediaItem {
    val item = coreItem ?: CoreJson.decode<MediaItem>(normalize("media", JSONObject().put("id", id).put("type", type).put("name", name).toString(), ""))
    return item.copy(id = id, type = org.viptv.core.wire.MediaKind.valueOf(type.uppercase()), name = name, poster = poster, background = backdrop, thumbnail = thumbnail,
        position = positionMillis / 1000.0, duration = durationMillis?.let { it / 1000.0 }, season = season?.toDouble(), episode = episode?.toDouble(),
        seriesId = seriesId, sourceAddonId = sourceAddonId, sourceFingerprint = sourceFingerprint, queueStatus = queueStatus,
        previousEpisode = previousEpisode?.wireItem(includeEpisodes), episodeTitle = episodeTitle, watched = watched,
        resumeActive = resumeActive, completionOnly = completionOnly, watchDateKnown = watchDateKnown,
        description = description, genres = genres, credits = credits, runtime = runtime, imdbRating = imdbRating, posterShape = posterShape,
        year = year?.toDoubleOrNull(), updatedAtMillis = updatedAtMillis?.toDouble(), releasedAtMillis = releasedAtMillis?.toDouble(),
        episodes = if (includeEpisodes) episodes.map { it.wireItem() } else emptyList())
}

internal object CorePolicy {
    fun value(kind: String, input: JSONObject): Any? = org.json.JSONTokener(normalize(kind, input.toString(), "")).nextValue().takeUnless { it == JSONObject.NULL }
    fun source(source: Source) = JSONObject().put("id", source.id).putOpt("sourceAddonId", source.addonId).putOpt("sourceFingerprint", source.fingerprint)
    fun sources(sources: List<Source>) = org.json.JSONArray().also { array -> sources.forEach { array.put(source(it)) } }
}
