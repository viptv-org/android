package org.viptv.app

import com.getair.video.PlayerCapabilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToLong

interface BackendGateway {
    suspend fun startDevicePairing(deviceName: String): DeviceCode
    suspend fun exchangeDeviceCode(code: String): DevicePollResult
    suspend fun refresh(refreshToken: String): DeviceSession
    suspend fun profiles(): Pair<List<Profile>, String?>
    suspend fun selectProfile(profileId: String)
    suspend fun home(profileId: String): List<HomeShelf>
    suspend fun discover(type: String = "movie", search: String? = null): List<Media>
    suspend fun catalogs(): List<DiscoverCatalog>
    suspend fun discover(request: CatalogDiscoverRequest): DiscoverPage
    suspend fun search(query: String): SearchResults
    suspend fun metadata(media: Media): Media
    suspend fun sources(media: Media, onUpdate: (List<Source>) -> Unit = {}): List<Source>
    suspend fun playback(
        source: Source,
        positionMillis: Long,
        capabilities: PlaybackClientCapabilities,
        audioTrackIndex: Int? = null,
        subtitleTrackIndex: Int? = null,
        subtitlesOff: Boolean = false,
    ): PlaybackLaunch
    suspend fun heartbeat(playbackId: String)
    suspend fun stopPlayback(playbackId: String)
    suspend fun updateProgress(profileId: String, media: Media, positionMillis: Long)
    suspend fun nextEpisode(profileId: String, media: Media): NextResult
    suspend fun favorites(profileId: String): List<Media>
    suspend fun toggleFavorite(profileId: String, media: Media): Boolean
    suspend fun queue(profileId: String): List<Media>
    suspend fun setQueueVisibility(profileId: String, media: Media, hidden: Boolean)
    suspend fun correctProgress(profileId: String, media: Media, action: String)
    suspend fun live(): List<LiveChannel>
    /** Canonical EPG filter/page contract; legacy implementations may supply the all-channel list only. */
    suspend fun livePage(request: LiveBrowseRequest): LiveBrowsePage {
        val channels = live()
        return LiveBrowsePage(channels, total = channels.size, request = request)
    }
    /** US guide categories are server-declared section IDs, never display-name guesses. */
    suspend fun liveCategories(): List<LiveCategory> = emptyList()
    suspend fun guide(channelId: String): List<GuideProgramme>
    suspend fun preferences(profileId: String): PlaybackPreferences
    suspend fun savePreferences(profileId: String, preferences: PlaybackPreferences)
    suspend fun addons(): List<Addon>
    suspend fun addAddon(manifestUrl: String): Addon
    suspend fun serverAbout(): ServerAbout
    suspend fun setAddonEnabled(addon: Addon, enabled: Boolean)
    suspend fun removeAddon(addon: Addon)
    suspend fun createProfile(name: String, avatarStyle: String, avatarChoice: Int?): Profile
    suspend fun updateProfile(profile: Profile, name: String, avatarStyle: String, avatarChoice: Int?): Profile
    suspend fun deleteProfile(profile: Profile)
    suspend fun unlockParent(pin: String)
    suspend fun logout()
}

/** Device polling failures with a server-defined retry action; other errors remain failures. */
sealed interface DevicePollResult {
    data class Authorized(val session: DeviceSession) : DevicePollResult
    data object Pending : DevicePollResult
    data object RateLimited : DevicePollResult
}

/** One Guide filter is active at a time, matching the Roku EPG filter column. */
sealed interface LiveChannelFilter {
    data object AllUs : LiveChannelFilter
    data object MyChannels : LiveChannelFilter
    data object Recent : LiveChannelFilter
    data class Category(val id: String) : LiveChannelFilter {
        init { require(id.isNotBlank()) }
    }
    data class Search(val query: String) : LiveChannelFilter {
        init {
            require(query == query.trim() && query.isNotBlank())
            require(query.length <= 128)
        }
    }
}

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

/** HTTP adapter for the documented Rust /api contract. It owns credentials and never logs them. */
class VipTvHttpGateway(private val origin: String, private var accessToken: String? = null) : BackendGateway {
    private val titleArtwork = java.util.concurrent.ConcurrentHashMap<String, Media>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    override suspend fun startDevicePairing(deviceName: String): DeviceCode = json("POST", "/auth/device/code", JSONObject().put("device_name", deviceName)).let {
        DeviceCode(it.getString("device_code"), it.getString("user_code"), it.getString("verification_uri"), it.optString("verification_uri_complete").ifBlank { null }, it.optString("qr_uri").ifBlank { null }, it.optLong("interval", 5))
    }
    override suspend fun exchangeDeviceCode(code: String): DevicePollResult = try {
        DevicePollResult.Authorized(session(json("POST", "/auth/device/token", JSONObject().put("device_code", code))))
    } catch (error: GatewayError) {
        when {
            error.status == 400 && error.message == "authorization_pending" -> DevicePollResult.Pending
            error.status == 429 -> DevicePollResult.RateLimited
            else -> throw error
        }
    }
    override suspend fun refresh(refreshToken: String): DeviceSession = session(json("POST", "/auth/device/refresh", JSONObject().put("refresh_token", refreshToken)))
    override suspend fun profiles(): Pair<List<Profile>, String?> {
        val root = json("GET", "/auth/me")
        val array = root.optJSONArray("profiles") ?: JSONArray()
        return (0 until array.length()).map { index -> array.getJSONObject(index).profile() } to root.opt("profile_id")?.toString()
    }
    override suspend fun selectProfile(profileId: String) { json("POST", "/auth/profile", JSONObject().put("profile_id", profileId)) }
    override suspend fun home(profileId: String): List<HomeShelf> = coroutineScope {
        suspend fun optionalFeed(load: suspend () -> List<Media>): List<Media> = try { load() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: GatewayError) { if (error.status == 401 || error.status == 403) throw error else emptyList() }
        catch (_: java.io.IOException) { emptyList() }
        val catalogList = async { catalogs() }
        suspend fun catalogFeed(type: String): List<Media> {
            val catalog = catalogList.await().firstOrNull { it.key.type == type } ?: return emptyList()
            return json("GET", "/discover?type=${enc(type)}&addon_id=${enc(catalog.key.addonId)}&catalog=${enc(catalog.key.id)}&skip=0").mediaArray("metas", "items", "rows")
        }
        suspend fun channels(path: String): List<Media> = json("GET", path).array("items", "channels").mapNotNull { it.optJSONObject()?.channel()?.asMedia() }
        val queue = async { optionalFeed { json("GET", "/profiles/${enc(profileId)}/continue/page?limit=13").mediaArray("items", "rows", "metas") } }
        val recent = async { optionalFeed { channels("/live?view=us&collection=recent&limit=24") } }
        val movies = async { optionalFeed { catalogFeed("movie") } }
        val series = async { optionalFeed { catalogFeed("series") } }
        val live = async { optionalFeed { channels("/live?view=us&offset=0&limit=12&search=") } }
        val saved = async { optionalFeed { json("GET", "/profiles/${enc(profileId)}/favorites/page?limit=13&exclude_live=true").mediaArray("items", "rows", "metas") } }
        val favoriteChannels = async { optionalFeed { channels("/live?view=us&collection=favorites&limit=24") } }
        val movieItems = movies.await().map { item -> titleArtwork[item.type + "\u0000" + item.id]?.let(item::withArtworkFrom) ?: item }
        val seriesItems = series.await().map { item -> titleArtwork[item.type + "\u0000" + item.id]?.let(item::withArtworkFrom) ?: item }
        val known = (movieItems + seriesItems).associateBy { it.type + "\u0000" + it.id }
        val hydrationSlots = Semaphore(3)
        suspend fun enrich(items: List<Media>): List<Media> = coroutineScope {
            items.map { item -> async {
                hydrationSlots.withPermit {
                    val lookup = item.copy(id = item.seriesId ?: item.id, type = if (item.type == "episode") "series" else item.type)
                    val rich = known[lookup.type + "\u0000" + lookup.id] ?: try { metadata(lookup) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { item }
                    item.copy(
                        name = item.name.ifBlank { rich.name }, poster = rich.poster ?: item.poster,
                        backdrop = rich.backdrop ?: item.backdrop, thumbnail = rich.thumbnail ?: item.thumbnail,
                        description = rich.description ?: item.description, year = rich.year ?: item.year,
                        runtime = rich.runtime ?: item.runtime, imdbRating = rich.imdbRating ?: item.imdbRating,
                        genres = rich.genres.ifEmpty { item.genres }, credits = rich.credits ?: item.credits,
                        episodes = rich.episodes.ifEmpty { item.episodes },
                    )
                }
            } }.awaitAll()
        }
        val richQueue = async { enrich(queue.await()) }
        val richSaved = async { enrich(saved.await()) }
        listOf(
            HomeShelf("Continue Watching", richQueue.await(), isQueueShelf = true),
            HomeShelf("Recently Watched Live TV", recent.await()),
            HomeShelf("Trending Movies", movieItems),
            HomeShelf("Popular Series", seriesItems),
            HomeShelf("Live Now", live.await()),
            HomeShelf("My List", richSaved.await()),
            HomeShelf("Favorite Channels", favoriteChannels.await()),
        ).filter { it.items.isNotEmpty() }
    }
    override suspend fun discover(type: String, search: String?): List<Media> = json("GET", discoverPath(type, search = search)).mediaArray("metas", "items", "rows")
    override suspend fun catalogs(): List<DiscoverCatalog> = jsonArray("GET", "/catalogs")
        .objects()
        .mapNotNull(JSONObject::discoverCatalog)
    override suspend fun discover(request: CatalogDiscoverRequest): DiscoverPage {
        val catalog = request.catalog
        val root = json(
            "GET",
            discoverPath(
                type = catalog.key.type,
                catalog = catalog.key.id,
                addonId = catalog.key.addonId,
                skip = request.skip,
                search = request.search,
                genre = request.genre,
                extras = request.extras,
            ),
        )
        val hasMore = root.optBoolean("has_more", false)
        val nextSkip = (root.opt("next_skip") as? Number)?.toInt()?.takeIf { it in 0..10_000 }
        return DiscoverPage(
            catalog = catalog.key,
            items = root.mediaArray("metas", "items", "rows"),
            requestedSkip = request.skip,
            nextSkip = nextSkip,
            hasMore = hasMore,
        )
    }
    override suspend fun search(query: String): SearchResults {
        val term = query.trim()
        if (term.isEmpty()) return SearchResults(emptyList(), partialFailure = false)
        val gate = Semaphore(3)
        return coroutineScope {
            // Live search starts independently: a failed catalog index must not
            // suppress the user's live matches.
            val catalogIndex = async { attempt { gate.withPermit { jsonArray("GET", "/catalogs") } } }
            val liveSearch = async { attempt { gate.withPermit {
                val live = json("GET", "/live?view=us&limit=80&search=${enc(term)}")
                    .array("items", "channels")
                    .mapNotNull { it.optJSONObject()?.channel()?.asMedia() }
                    .distinctBy { it.id }
                    .take(24)
                SearchSection("Live TV", live)
            } } }
            var partialFailure = false
            val catalogs = when (val result = catalogIndex.await()) {
                is SearchAttempt.Value -> result.value.objects()
                    .mapNotNull { it.discoverCatalog() }
                    .filter { it.supportsSearch && it.key.type != "live" }
                    .take(128)
                SearchAttempt.Failure -> {
                    partialFailure = true
                    emptyList()
                }
            }
            val catalogResults = catalogs.map { catalog -> async {
                catalog to attempt { gate.withPermit {
                    val items = json("GET", discoverPath(catalog.key.type, catalog.key.id, catalog.key.addonId, search = term))
                        .mediaArray("metas", "items", "rows")
                        .distinctBy { "${it.type}\u0000${it.id}" }
                        .take(24)
                    SearchSection(catalog.name, items)
                } }
            } }.awaitAll()
            val sections = buildList {
                for ((_, result) in catalogResults) when (result) {
                    is SearchAttempt.Value -> if (result.value.items.isNotEmpty()) add(result.value)
                    SearchAttempt.Failure -> partialFailure = true
                }
                when (val result = liveSearch.await()) {
                    is SearchAttempt.Value -> result.value.takeIf { it.items.isNotEmpty() }?.let(::add)
                    SearchAttempt.Failure -> partialFailure = true
                }
            }
            SearchResults(sections, partialFailure)
        }
    }
    override suspend fun metadata(media: Media): Media {
        val type = if (media.type == "episode") "series" else media.type
        val id = if (type == "series") media.seriesId ?: media.id else media.id
        val result = json("GET", "/meta/${enc(type)}/${enc(id)}").optJSONObject("meta")?.media() ?: media
        titleArtwork[type + "\u0000" + id] = Media(id, type, poster = result.poster, backdrop = result.backdrop, thumbnail = result.thumbnail, posterShape = result.posterShape)
        return result
    }
    override suspend fun sources(media: Media, onUpdate: (List<Source>) -> Unit): List<Source> {
        val job = json("POST", "/streams", JSONObject().put("id", media.id).put("type", media.type).put("name", media.name).putOpt("series_id", media.seriesId).putOpt("season", media.season).putOpt("episode", media.episode))
        val id = job.getString("id")
        var after = 0
        val accumulated = LinkedHashMap<String, Source>()
        // Match Roku's three-minute discovery budget: late providers may still
        // contribute sources, but cancellation remains cooperative between polls.
        repeat(120) {
            val poll = json("GET", "/streams/${enc(id)}?after=$after")
            val events = poll.optJSONArray("events") ?: JSONArray()
            for (i in 0 until events.length()) {
                val event = events.getJSONObject(i); after = maxOf(after, event.optInt("seq", after))
                val streams = event.optJSONArray("streams") ?: JSONArray()
                for (index in 0 until streams.length()) {
                    val stream = streams.optJSONObject(index) ?: continue
                    stream.source(event.optString("source").ifBlank { null }).also { source -> if (source.id.isNotBlank()) accumulated.putIfAbsent(source.id, source) }
                }
            }
            onUpdate(accumulated.values.toList())
            if (poll.optBoolean("done")) return accumulated.values.toList()
            delay(1_500)
        }
        return accumulated.values.toList()
    }
    override suspend fun playback(
        source: Source,
        positionMillis: Long,
        capabilities: PlaybackClientCapabilities,
        audioTrackIndex: Int?,
        subtitleTrackIndex: Int?,
        subtitlesOff: Boolean,
    ): PlaybackLaunch {
        val body = JSONObject()
            .put("stream_id", source.id)
            .put("position", seconds(positionMillis))
            .put("capabilities", capabilities.toWireJson())
            .putOpt("audio_track_index", audioTrackIndex)
            .putOpt("subtitle_track_index", subtitleTrackIndex)
            .put("subtitles_off", subtitlesOff)
        val root = json("POST", "/playback", body)
        return PlaybackLaunch(
            sessionId = root.getString("id"),
            url = capabilityUrl(root.getString("url")),
            headers = root.optJSONObject("headers")?.headers() ?: emptyMap(),
            format = root.optString("format", "hls"),
            mode = root.optString("mode", "direct"),
            videoMode = root.optString("video_mode", "copy"),
            audioMode = root.optString("audio_mode", "copy"),
            positionMillis = millis(root.optDouble("position", 0.0)),
            durationMillis = root.optDouble("duration", 0.0).takeIf { it > 0 }?.let(::millis),
            live = root.optBoolean("live"),
            audioTracks = root.array("audio_tracks").mapNotNull { it.optJSONObject()?.track() },
            subtitleTracks = root.array("subtitle_tracks").mapNotNull { it.optJSONObject()?.track() },
            subtitlesSupported = root.optBoolean("subtitles_supported"),
        )
    }
    override suspend fun heartbeat(playbackId: String) { json("POST", "/playback/${enc(playbackId)}/heartbeat", JSONObject()) }
    override suspend fun stopPlayback(playbackId: String) { json("DELETE", "/playback/${enc(playbackId)}") }
    override suspend fun updateProgress(profileId: String, media: Media, positionMillis: Long) {
        json("PUT", "/profiles/${enc(profileId)}/progress", media.body().put("position", seconds(positionMillis)))
    }
    override suspend fun nextEpisode(profileId: String, media: Media): NextResult {
        val result = json("POST", "/profiles/${enc(profileId)}/continue/next", media.body())
        return NextResult(result.optString("status"), result.optJSONObject("item")?.media())
    }
    override suspend fun favorites(profileId: String): List<Media> = json("GET", "/profiles/${enc(profileId)}/favorites/page?limit=40").mediaArray("items")
    override suspend fun toggleFavorite(profileId: String, media: Media): Boolean = json("POST", "/profiles/${enc(profileId)}/favorites/toggle", media.body()).optBoolean("saved")
    override suspend fun queue(profileId: String): List<Media> = json("GET", "/profiles/${enc(profileId)}/continue/page?limit=40").mediaArray("items")
    override suspend fun setQueueVisibility(profileId: String, media: Media, hidden: Boolean) {
        json("PUT", "/profiles/${enc(profileId)}/continue/visibility", media.body().put("hidden", hidden))
    }
    override suspend fun correctProgress(profileId: String, media: Media, action: String) {
        json("PUT", "/profiles/${enc(profileId)}/progress/correct", media.body().put("action", action))
    }
    override suspend fun live(): List<LiveChannel> = json("GET", "/live?view=us&limit=80").array("items", "channels").mapNotNull { it.optJSONObject()?.channel() }
    override suspend fun livePage(request: LiveBrowseRequest): LiveBrowsePage {
        val root = json("GET", livePath(request))
        return LiveBrowsePage(
            channels = root.array("channels", "items").mapNotNull { it.optJSONObject()?.channel() },
            total = root.optInt("total", 0).coerceAtLeast(0),
            request = request,
            searchScope = root.optString("search_scope").ifBlank { null },
        )
    }
    override suspend fun liveCategories(): List<LiveCategory> = json("GET", "/live/categories?view=us")
        .array("categories")
        .mapNotNull { it.optJSONObject()?.liveCategory() }
    override suspend fun guide(channelId: String): List<GuideProgramme> = json("GET", "/guide/${enc(channelId)}").array("programmes", "programs", "items").mapNotNull { it.optJSONObject()?.programme() }
    override suspend fun preferences(profileId: String): PlaybackPreferences = json("GET", "/profiles/${enc(profileId)}/preferences").preferences()
    override suspend fun savePreferences(profileId: String, preferences: PlaybackPreferences) {
        json("PUT", "/profiles/${enc(profileId)}/preferences", preferences.body())
    }
    override suspend fun addons(): List<Addon> = jsonArray("GET", "/addons").objects().mapNotNull { it.addon() }
    override suspend fun addAddon(manifestUrl: String): Addon = json("POST", "/addons", JSONObject().put("manifest_url", manifestUrl)).addon()
    override suspend fun serverAbout(): ServerAbout = ServerAbout(json("GET", "/status").optBoolean("ffmpeg_available"))
    override suspend fun setAddonEnabled(addon: Addon, enabled: Boolean) { json("PATCH", "/addons/${enc(addon.id)}", JSONObject().put("enabled", enabled)) }
    override suspend fun removeAddon(addon: Addon) { json("DELETE", "/addons/${enc(addon.id)}") }
    override suspend fun createProfile(name: String, avatarStyle: String, avatarChoice: Int?): Profile =
        json("POST", "/profiles", profilePayload(name, avatarStyle, avatarChoice, setupComplete = false)).profile()
    override suspend fun updateProfile(profile: Profile, name: String, avatarStyle: String, avatarChoice: Int?): Profile =
        json("PATCH", "/profiles/${enc(profile.id)}", profilePayload(name, avatarStyle, avatarChoice, setupComplete = true)).profile()
    override suspend fun deleteProfile(profile: Profile) { json("DELETE", "/profiles/${enc(profile.id)}") }
    override suspend fun unlockParent(pin: String) { json("POST", "/parent/unlock", JSONObject().put("pin", pin)) }
    override suspend fun logout() { json("POST", "/auth/logout", JSONObject()) }
    private fun session(value: JSONObject): DeviceSession {
        val token = value.getString("access_token"); accessToken = token
        return DeviceSession(token, value.getString("refresh_token"), value.opt("profile_id")?.toString())
    }
    private suspend fun json(method: String, path: String, body: JSONObject? = null): JSONObject = JSONObject(responseText(method, path, body))
    /** `/addons` is deliberately a raw JSON array in the Rust API. */
    private suspend fun jsonArray(method: String, path: String, body: JSONObject? = null): JSONArray = JSONArray(responseText(method, path, body))
    /**
     * A cancellable OkHttp boundary works in Android and host-JVM wire tests,
     * including PATCH. Calls are bounded and cancelled with their coroutine.
     */
    private suspend fun responseText(method: String, path: String, body: JSONObject? = null): String = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder()
            .url(origin.trimEnd('/') + "/api" + path)
            .header("Accept", "application/json")
            .apply { accessToken?.let { header("Authorization", "Bearer $it") } }
            .method(method, body?.toString()?.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val text = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        val message = runCatching { JSONObject(text.ifBlank { "{}" }).optString("error", "Request failed") }
                            .getOrDefault("Request failed")
                        if (continuation.isActive) continuation.resumeWithException(GatewayError(it.code, message))
                    } else if (continuation.isActive) {
                        continuation.resume(text)
                    }
                }
            }
        })
    }
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    /** Playback URLs are server capabilities. Reject an unexpected external URL before Media3 sees it. */
    private fun capabilityUrl(value: String): String {
        val base = URL(origin.trimEnd('/') + "/")
        val resolved = URL(base, value)
        require(resolved.protocol == base.protocol && resolved.host == base.host && resolved.port == base.port && resolved.path.startsWith("/media/")) {
            "Invalid playback capability URL"
        }
        return resolved.toString()
    }
}
class GatewayError(val status: Int, override val message: String) : IllegalStateException(message)

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
/**
 * `avatar_seed` is server storage, never a profile mutation field. The public
 * wire contract exposes a bounded avatar choice and setup completion only.
 */
private fun profilePayload(name: String, avatarStyle: String, avatarChoice: Int?, setupComplete: Boolean): JSONObject =
    JSONObject().put("name", name).put("avatar_style", avatarStyle).also { body ->
        avatarChoice?.let { body.put("avatar_choice", it) }
        // Create rejects setup_complete. Saving the profile editor completes an
        // imported profile, so updates send the only valid value: true.
        if (setupComplete) body.put("setup_complete", true)
    }

private fun JSONObject.profile() = Profile(
    id = get("id").toString(),
    name = getString("name"),
    avatarUrl = optString("avatar_url").ifBlank { null },
    kids = optBoolean("kids"),
    primary = optBoolean("is_primary"),
    avatarStyle = optString("avatar_style", "critters"),
    avatarChoice = (opt("avatar_choice") as? Number)?.toInt()?.takeIf { it in 1..48 },
    setupComplete = optBoolean("setup_complete"),
)
private fun JSONObject.discoverCatalog(): DiscoverCatalog? {
    val id = optString("id")
    val type = optString("type")
    val addonId = opt("addon_id")?.toString().orEmpty()
    if (id.isBlank() || type.isBlank() || addonId.isBlank()) return null
    val filters = array("extra")
        .mapNotNull { it.optJSONObject()?.catalogFilter() }
        .filterNot { it.name == "skip" }
    return DiscoverCatalog(
        key = CatalogKey(addonId, type, id),
        name = optString("name", id),
        supportsSearch = optBoolean("supports_search", false),
        supportsSkip = optBoolean("supports_skip", false),
        filters = filters,
    )
}
private fun JSONObject.catalogFilter(): CatalogFilter? {
    val name = optString("name").trim()
    if (name.isBlank()) return null
    val options = array("options").mapNotNull { it as? String }
    val kind = when (name) {
        "search" -> CatalogFilterKind.Search
        "genre" -> CatalogFilterKind.Genre
        else -> if (options.isEmpty()) CatalogFilterKind.FreeText else CatalogFilterKind.Choice
    }
    return CatalogFilter(
        name = name,
        kind = kind,
        required = optBoolean("is_required", false),
        options = options,
        defaultValue = (opt("default") as? String)?.takeIf(String::isNotBlank),
        optionsLimit = (opt("options_limit") as? Number)?.toInt()?.takeIf { it > 0 },
    )
}
private fun discoverPath(
    type: String,
    catalog: String? = null,
    addonId: String? = null,
    skip: Int? = null,
    search: String? = null,
    genre: String? = null,
    extras: Map<String, String> = emptyMap(),
): String = buildString {
    append("/discover?type=").append(URLEncoder.encode(type, "UTF-8"))
    catalog?.takeIf(String::isNotBlank)?.let { append("&catalog=").append(URLEncoder.encode(it, "UTF-8")) }
    addonId?.takeIf(String::isNotBlank)?.let { append("&addon_id=").append(URLEncoder.encode(it, "UTF-8")) }
    skip?.let { append("&skip=").append(it.coerceIn(0, 10_000)) }
    search?.takeIf(String::isNotBlank)?.let { append("&search=").append(URLEncoder.encode(it, "UTF-8")) }
    genre?.takeIf(String::isNotBlank)?.let { append("&genre=").append(URLEncoder.encode(it, "UTF-8")) }
    extras
        .filterKeys { it.isNotBlank() && it !in setOf("skip", "search", "genre") }
        .filterValues(String::isNotBlank)
        .takeIf { it.isNotEmpty() }
        ?.let { values ->
            val encoded = JSONObject().also { json ->
                values.toSortedMap().forEach { (name, value) -> json.put(name, value) }
            }.toString()
            append("&extras=").append(URLEncoder.encode(encoded, "UTF-8"))
        }
}
private fun livePath(request: LiveBrowseRequest): String = buildString {
    append("/live?view=us")
    when (val filter = request.filter) {
        LiveChannelFilter.AllUs -> Unit
        LiveChannelFilter.MyChannels -> append("&collection=favorites")
        LiveChannelFilter.Recent -> append("&collection=recent")
        is LiveChannelFilter.Category -> append("&category=").append(URLEncoder.encode(filter.id, "UTF-8"))
        is LiveChannelFilter.Search -> append("&search=").append(URLEncoder.encode(filter.query, "UTF-8"))
    }
    append("&offset=").append(request.offset)
    append("&limit=").append(request.limit)
}
private sealed interface SearchAttempt<out T> {
    data class Value<T>(val value: T) : SearchAttempt<T>
    data object Failure : SearchAttempt<Nothing>
}
private suspend fun <T> attempt(request: suspend () -> T): SearchAttempt<T> = try {
    SearchAttempt.Value(request())
} catch (error: CancellationException) {
    throw error
} catch (_: Throwable) {
    SearchAttempt.Failure
}
private fun LiveChannel.asMedia() = Media(id, "live", name, poster = logo)

private fun JSONObject.displayString(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
    opt(key)?.takeUnless { it == JSONObject.NULL }?.let { value ->
        if (value is JSONArray) (0 until value.length()).map { value.optString(it) }.filter(String::isNotBlank).joinToString(", ") else value.toString()
    }?.takeIf { it.isNotBlank() && it != "null" }
}
private fun JSONObject.timestampMillis(vararg keys: String): Long? {
    val value = displayString(*keys) ?: return null
    value.toDoubleOrNull()?.let { return if (it < 1_000_000_000_000) (it * 1000).toLong() else it.toLong() }
    for (pattern in listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd")) {
        try {
            return java.text.SimpleDateFormat(pattern, java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
                isLenient = false
            }.parse(value)?.time
        } catch (_: java.text.ParseException) { }
    }
    return null
}
private fun JSONObject.media(): Media {
    val base = Media(get("id").toString(), optString("type", "movie"), optString("name", optString("title")), displayString("poster"), displayString("description"), millis(optDouble("position", 0.0)), optDouble("duration", 0.0).takeIf { it > 0 }?.let(::millis), optString("series_id").ifBlank { null }, optInt("season").takeIf { it > 0 }, optInt("episode").takeIf { it > 0 }, optString("source_addon_id").ifBlank { null }, optString("source_fingerprint").ifBlank { null }, episodeTitle = optString("episode_title", optString("episodeTitle")).ifBlank { null }, queueStatus = optString("queue_status").ifBlank { null },
        backdrop = displayString("backdrop", "background"),
        thumbnail = displayString("thumbnail", "landscape", "image"),
        year = displayString("year", "releaseInfo"),
        runtime = displayString("runtime"),
        genres = (optJSONArray("genres") ?: JSONArray()).let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf { value -> value.isNotBlank() && value != "null" } } },
        credits = displayString("credits") ?: listOfNotNull(displayString("director")?.let { "Director: $it" }, displayString("cast")?.let { "Cast: $it" }).joinToString("  ·  ").ifBlank { null },
        imdbRating = displayString("imdbRating", "imdb_rating", "rating"),
        posterShape = displayString("posterShape", "poster_shape"),
        updatedAtMillis = timestampMillis("updated_at", "updatedAt"),
        releasedAtMillis = timestampMillis("released", "released_at", "releaseDate"),
        watched = optBoolean("watched") || optString("watch_state") == "watched",
    )
    val previous = optJSONObject("previous_episode")?.media()
    val videos = optJSONArray("videos") ?: optJSONArray("episodes") ?: return base.copy(previousEpisode = previous)
    return base.copy(previousEpisode = previous, episodes = (0 until videos.length()).mapNotNull { index -> videos.optJSONObject(index)?.media()?.let { episode ->
        episode.copy(
            type = videos.optJSONObject(index)?.optString("type").orEmpty().ifBlank { base.type },
            seriesId = episode.seriesId ?: base.id,
        )
    } })
}
private fun JSONObject.mediaArray(vararg keys: String): List<Media> { val a = keys.firstNotNullOfOrNull { optJSONArray(it) } ?: JSONArray(); return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.media() } }
private fun JSONObject.source(eventProvider: String? = null): Source {
    val provider = optString("provider", optString("source", eventProvider ?: "Source"))
    return Source(
        id = optString("id", optString("stream_id")),
        provider = provider,
        name = optString("source_name", optString("title", optString("name", provider))).ifBlank { provider },
        description = optString("description", optString("filename")),
        addonId = optString("source_addon_id").ifBlank { null },
        fingerprint = optString("source_fingerprint").ifBlank { null },
        quality = optString("source_quality").ifBlank { null },
        audio = optString("source_audio").ifBlank { null },
    )
}
private fun JSONObject.track() = PlaybackTrack(optInt("input_index"), optString("codec").ifBlank { null }, optString("language").ifBlank { null }, optString("language_status", "unknown"), optString("title"), optBoolean("selected"), optBoolean("supported"), optBoolean("selectable"))
private fun JSONObject.headers(): Map<String, String> = keys().asSequence().associateWith { get(it).toString() }
private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
private fun JSONObject.array(vararg keys: String): List<Any?> = (keys.firstNotNullOfOrNull { optJSONArray(it) } ?: JSONArray()).let { array -> (0 until array.length()).map { index -> array.opt(index) } }
private fun Any?.optJSONObject(): JSONObject? = this as? JSONObject
private fun JSONObject.channel() = LiveChannel(
    id = get("id").toString(),
    name = optString("name", optString("title")),
    logo = optString("logo").ifBlank { null },
    category = optString("section", optString("category")).ifBlank { null },
)
private fun JSONObject.liveCategory(): LiveCategory? {
    val id = optString("id").trim()
    val name = optString("name").trim()
    if (id.isBlank() || name.isBlank()) return null
    return LiveCategory(id, name, optInt("count").coerceAtLeast(0))
}
private fun JSONObject.programme() = GuideProgramme(optString("title", "No schedule available"), optLong("start", optLong("start_time")) * 1000, optLong("end", optLong("end_time")) * 1000, optString("description").ifBlank { null })
private fun JSONObject.addon() = Addon(get("id").toString(), optString("name"), optString("manifest_url"), optBoolean("enabled", true))
private fun Media.body() = JSONObject().put("id", id).put("type", type).put("name", name).putOpt("poster", poster).putOpt("series_id", seriesId).putOpt("season", season).putOpt("episode", episode).putOpt("source_addon_id", sourceAddonId).putOpt("source_fingerprint", sourceFingerprint).putOpt("duration", durationMillis?.let(::seconds))
private fun PlaybackPreferences.body() = JSONObject().put("audio_language", audioLanguage).put("subtitle_language", subtitleLanguage).put("subtitles_enabled", subtitlesEnabled).put("subtitle_size", subtitleSize).put("subtitle_style", subtitleStyle).put("quality", quality).put("autoplay", autoplay)
private fun JSONObject.preferences() = PlaybackPreferences(optString("audio_language", "en"), optString("subtitle_language", "en"), optBoolean("subtitles_enabled"), optString("subtitle_size", "normal"), optString("subtitle_style", "system"), optString("quality", "auto"), optBoolean("autoplay", true))
private fun millis(seconds: Double): Long = (seconds * 1_000).roundToLong()
private fun seconds(millis: Long): Double = millis / 1_000.0
