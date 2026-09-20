package org.viptv.app

import com.getair.video.PlayerCapabilities
import org.json.JSONObject

/** `/live?view=us` paging inputs. Offset stays zero-based and page size is bounded by Rust's 200-channel limit. */
data class LiveBrowseRequest(
    val filter: LiveChannelFilter = LiveChannelFilter.AllUs,
    val offset: Int = 0,
    val limit: Int = GuidePolicy.PAGE_SIZE,
) {
    init {
        require(offset >= 0)
        require(limit in 1..200)
    }
}

data class LiveBrowsePage(
    val channels: List<LiveChannel>,
    val total: Int,
    val request: LiveBrowseRequest,
    val searchScope: String? = null,
) {
    val nextOffset: Int? get() = (request.offset + channels.size).takeIf { it < total }
    val hasMore: Boolean get() = nextOffset != null
}

data class LiveCategory(val id: String, val name: String, val count: Int) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(count >= 0)
    }
}

/** A catalog identifier is only unique within an add-on and media type. */
data class CatalogKey(
    val addonId: String,
    val type: String,
    val id: String,
) {
    init {
        require(addonId.isNotBlank())
        require(type.isNotBlank())
        require(id.isNotBlank())
    }

    val stableId: String get() = "$addonId\u0000$type\u0000$id"
}

enum class CatalogFilterKind { Search, Genre, Choice, FreeText }

/** A normalized server-declared filter. `skip` is pagination metadata, not a UI filter. */
data class CatalogFilter(
    val name: String,
    val kind: CatalogFilterKind,
    val required: Boolean,
    val options: List<String> = emptyList(),
    val defaultValue: String? = null,
    val optionsLimit: Int? = null,
)

data class DiscoverCatalog(
    val key: CatalogKey,
    val name: String,
    val supportsSearch: Boolean,
    val supportsSkip: Boolean,
    val filters: List<CatalogFilter>,
    val addonName: String? = null,
)

/** Query fields map exactly to the Rust `/discover` envelope. */
data class CatalogDiscoverRequest(
    val catalog: DiscoverCatalog,
    val skip: Int = 0,
    val search: String? = null,
    val genre: String? = null,
    /** Declared non-search/non-genre extra values, sent as the `extras` JSON object. */
    val extras: Map<String, String> = emptyMap(),
) {
    init {
        require(skip in 0..10_000)
        require(extras.keys.none { it == "skip" || it == "search" || it == "genre" })
    }
}

/** The server returns only a forward cursor; callers retain earlier requested offsets. */
data class DiscoverPage(
    val catalog: CatalogKey,
    val items: List<Media>,
    val requestedSkip: Int,
    val nextSkip: Int?,
    val hasMore: Boolean,
)

/**
 * Measured local decoder facts sent to the server's deliberately small playback contract.
 * Zero dimensions mean the adapter could not establish a usable video limit; they are sent
 * unchanged so the server declines safely instead of receiving an invented fallback.
 */
data class PlaybackClientCapabilities(
    val maxWidth: Int,
    val maxHeight: Int,
    val h264: Boolean,
    val hevc: Boolean,
    val hevcSdr: Boolean,
    val aac: Boolean,
    val directPlay: Boolean,
) {
    init {
        require(maxWidth >= 0)
        require(maxHeight >= 0)
        require(!hevcSdr || hevc)
    }

    companion object {
        fun from(player: PlayerCapabilities): PlaybackClientCapabilities {
            val h264 = "h264" in player.videoCodecs
            val hevc = "hevc" in player.videoCodecs
            val aac = "aac" in player.audioCodecs
            val maxWidth = player.maxVideoWidth ?: 0
            val maxHeight = player.maxVideoHeight ?: 0
            return PlaybackClientCapabilities(
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                h264 = h264,
                hevc = hevc,
                hevcSdr = hevc && player.supportsHevcSdr,
                aac = aac,
                directPlay = h264 && aac && maxWidth >= 2 && maxHeight >= 2 &&
                    ("hls" in player.adaptiveProtocols || "mp4" in player.containers),
            )
        }
    }

    fun toWireJson(): JSONObject = JSONObject()
        .put("max_width", maxWidth)
        .put("max_height", maxHeight)
        .put("h264", h264)
        .put("hevc", hevc)
        .put("hevc_sdr", hevcSdr)
        .put("aac", aac)
        .put("direct_play", directPlay)
}

/** Complete server-owned delivery facts. The controller decides the UX; it never guesses from a URL. */
data class PlaybackLaunch(
    val sessionId: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val format: String = "hls",
    val mode: String = "direct",
    val videoMode: String = "copy",
    val audioMode: String = "copy",
    val positionMillis: Long = 0,
    val durationMillis: Long? = null,
    val live: Boolean = false,
    val audioTracks: List<PlaybackTrack> = emptyList(),
    val subtitleTracks: List<PlaybackTrack> = emptyList(),
    val subtitlesSupported: Boolean = false,
)

/** Input-stream track facts: index is server/ffprobe input index, never output order. */
data class PlaybackTrack(
    val inputIndex: Int,
    val codec: String? = null,
    val language: String? = null,
    val languageStatus: String = "unknown",
    val title: String = "",
    val selected: Boolean = false,
    val supported: Boolean = false,
    val selectable: Boolean = false,
)
