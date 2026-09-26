package org.viptv.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** HTTP adapter for the documented Rust /api contract. It owns credentials and never logs them. */
class VipTvHttpGateway(
    private val origin: String,
    private var accessToken: String? = null,
    /** Coalesced session refresh for an authenticated 401; returns a fresh access token or null. */
    private val onUnauthorized: (suspend () -> String?)? = null,
) : BackendGateway {
    fun setAccessToken(value: String?) { accessToken = value }
    private val titleArtwork = java.util.concurrent.ConcurrentHashMap<String, Media>()
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
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
    override suspend fun home(profileId: String, onUpdate: (List<HomeShelf>) -> Unit): List<HomeShelf> = coroutineScope {
        val rows = java.util.TreeMap<Int, HomeShelf>()
        val publisher = kotlinx.coroutines.sync.Mutex()
        suspend fun publish(order: Int, shelf: HomeShelf) = publisher.withLock {
            rows[order] = shelf
            onUpdate(rows.values.filter { it.items.isNotEmpty() })
        }
        suspend fun optional(load: suspend () -> List<Media>): List<Media> = try { load() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: GatewayError) { if (error.status in listOf(401, 403)) throw error else emptyList() }
        catch (_: IOException) { emptyList() }
        val catalogList = async {
            try { catalogs() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: GatewayError) { if (error.status in listOf(401, 403)) throw error else emptyList() }
            catch (_: IOException) { emptyList() }
        }
        val metadata = mutableMapOf<String, kotlinx.coroutines.Deferred<Media>>()
        val metadataLock = kotlinx.coroutines.sync.Mutex()
        val hydration = Semaphore(3)
        suspend fun hydrate(items: List<Media>, order: Int, title: String, queue: Boolean) {
            val enriched = items.toMutableList()
            publish(order, HomeShelf(title, items, queue))
            items.mapIndexed { index, item -> async {
                if (item.type != "live") {
                    val lookup = item.copy(id = item.seriesId ?: item.id, type = if (item.type == "episode") "series" else item.type)
                    val key = lookup.type + ":" + lookup.id
                    val pending = metadataLock.withLock { metadata.getOrPut(key) { async { hydration.withPermit { metadataOr(item, lookup) } } } }
                    enriched[index] = CoreModels.enrich(item, pending.await())
                    publish(order, HomeShelf(title, enriched.toList(), queue))
                }
            } }.awaitAll()
        }
        val queue = async { hydrate(optional { json("GET", "/profiles/" + enc(profileId) + "/continue/page?limit=40").mediaArray("items") }, 0, "Continue watching", true) }
        val recent = async {
            publish(1, HomeShelf("Recently watched live TV", optional { json("GET", "/live?view=us&collection=recent&limit=24").liveChannels().map(LiveChannel::asMedia) }))
        }
        val saved = async { hydrate(optional { favorites(profileId) }, 1000, "My List", false) }
        val live = async { publish(1001, HomeShelf("Live now", optional { this@VipTvHttpGateway.live().map(LiveChannel::asMedia) })) }
        val catalogs = catalogList.await().filter { catalog ->
            catalog.key.type != "live" && catalog.filters.none { it.required && DiscoverPolicy.defaults(catalog)[it.name].isNullOrBlank() }
        }
        val catalogGate = Semaphore(3)
        catalogs.mapIndexed { index, catalog -> async {
            val items = optional { catalogGate.withPermit { discover(DiscoverPolicy.request(catalog, DiscoverPolicy.defaults(catalog), 0)).items } }
            val title = listOfNotNull(catalog.addonName, catalog.name).joinToString(" · ")
            publish(index + 2, HomeShelf(title, items, id = catalog.key.stableId))
        } }.awaitAll()
        queue.await(); recent.await(); saved.await(); live.await()
        rows.values.filter { it.items.isNotEmpty() }
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
    override suspend fun search(query: String, onUpdate: (SearchResults) -> Unit): SearchResults {
        val term = query.trim()
        if (term.isEmpty()) return SearchResults(emptyList(), false)
        return coroutineScope {
            val gate = Semaphore(6)
            val publisher = kotlinx.coroutines.sync.Mutex()
            val completed = java.util.TreeMap<Int, SearchSection>()
            var partial = false
            suspend fun publish(index: Int, result: SearchAttempt<SearchSection>) = publisher.withLock {
                when (result) {
                    is SearchAttempt.Value -> if (result.value.items.isNotEmpty()) completed[index] = result.value
                    SearchAttempt.Failure -> partial = true
                }
                onUpdate(SearchResults(completed.values.toList(), partial))
            }
            val liveRequest = async {
                publish(128, attempt {
                    SearchSection("Live TV", json("GET", "/live?view=us&limit=80&search=" + enc(term)).liveChannels().map(LiveChannel::asMedia).distinctBy { it.id }.take(24))
                })
            }
            val catalogs = when (val result = attempt { catalogs() }) {
                is SearchAttempt.Value -> result.value.filter { it.supportsSearch && it.key.type != "live" }.take(128)
                SearchAttempt.Failure -> { partial = true; emptyList() }
            }
            catalogs.mapIndexed { index, catalog -> async {
                publish(index, attempt { gate.withPermit {
                    val items = discover(DiscoverPolicy.request(catalog, DiscoverPolicy.defaults(catalog) + ("search" to term), 0)).items
                    SearchSection(catalog.name, items.distinctBy { HomeFocusPolicy.mediaKey(it) }.take(24))
                } })
            } }.awaitAll()
            liveRequest.await()
            SearchResults(completed.values.toList(), partial)
        }
    }
    override suspend fun seriesProgress(profileId: String, seriesId: String): List<Media> =
        jsonArray("GET", "/profiles/${enc(profileId)}/progress/series?series_id=${enc(seriesId)}").objects().take(2000).map { it.media() }
    override suspend fun metadata(media: Media): Media {
        val type = if (media.type == "episode") "series" else media.type
        val id = if (type == "series") media.seriesId ?: media.id else media.id
        titleArtwork[type + "\u0000" + id]?.let { return it }
        if (titleArtwork.size >= 256) titleArtwork.keys.firstOrNull()?.let { titleArtwork.remove(it) }
        val result = json("GET", "/meta/${enc(type)}/${enc(id)}").optJSONObject("meta")?.media() ?: media
        titleArtwork[type + "\u0000" + id] = result
        return result
    }
    /** A failed enrichment lookup must never discard the item the caller already has. */
    private suspend fun metadataOr(fallback: Media, lookup: Media = fallback): Media = try {
        metadata(lookup)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        fallback
    }
    override suspend fun sources(media: Media, onUpdate: (List<Source>) -> Unit): List<Source> {
        // The discovery request body, poll path, cursor, deduplication, budget
        // and completion rules all come from the shared Rust core; this loop
        // owns only transport, the update callback, cancellation and the fixed
        // poll interval. Roku's three-minute discovery budget lives in Rust.
        val request = org.viptv.core.wire.CoreJson.decode<org.viptv.core.wire.ApiRequest>(
            uniffi.viptv_core.normalize("request", JSONObject()
                .put("operation", "sources")
                .put("item", JSONObject(media.normalizedJson())).toString(), origin)
        )
        val id = json(request.method, request.path.removePrefix("/api"), request.body?.let { JSONObject(org.viptv.core.wire.CoreJson.encode(it)) }).getString("id")
        var state = jsonStepState()
        while (true) {
            val poll = json("GET", pollPath(id, state))
            val output = step(state, poll)
            val accumulated = output.sources()
            onUpdate(accumulated)
            if (output.optBoolean("done")) return accumulated
            state = output.getJSONObject("state")
            delay(1_500)
        }
    }
    private fun pollPath(id: String, state: JSONObject): String {
        val request = org.viptv.core.wire.CoreJson.decode<org.viptv.core.wire.ApiRequest>(
            uniffi.viptv_core.normalize("request", JSONObject()
                .put("operation", "sourcesPoll")
                .put("id", id)
                .put("after", state.optLong("after", 0L)).toString(), origin)
        )
        return request.path.removePrefix("/api")
    }
    private fun jsonStepState(): JSONObject = JSONObject().put("after", 0L).put("sources", JSONArray()).put("polls", 0L)
    private fun step(state: JSONObject, poll: JSONObject): JSONObject {
        val output = JSONObject(
            uniffi.viptv_core.normalize(
                "sourcesPollStep",
                JSONObject().put("state", state).put("poll", poll).toString(),
                origin,
            )
        )
        return output.put("sources", output.optJSONArray("sources") ?: JSONArray())
    }
    private fun JSONObject.sources(): List<Source> {
        val array = optJSONArray("sources") ?: return emptyList()
        // The reducer's sources are already Rust-normalized: decode the wire
        // type directly, with the display projection as the only second pass.
        val result = ArrayList<Source>(array.length())
        for (index in 0 until array.length()) {
            array.optJSONObject(index) ?: continue
            val normalized = org.viptv.core.wire.CoreJson.decode<org.viptv.core.wire.MediaSource>(array.getJSONObject(index).toString())
            val display = org.viptv.core.wire.CoreJson.decode<org.viptv.core.wire.SourcePresentation>(
                uniffi.viptv_core.normalize("sourceDisplay", org.viptv.core.wire.CoreJson.encode(normalized), "")
            )
            result.add(Source(normalized.id, normalized.provider.orEmpty(), display.title, display.body, normalized.sourceAddonId, normalized.sourceFingerprint, normalized.quality, normalized.audio, displayResolved = true))
        }
        return result
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
            .put(if (source.channelId != null) "channel_id" else "stream_id", source.channelId ?: source.id)
            .put("position", seconds(positionMillis))
            .put("capabilities", capabilities.toWireJson())
            .putOpt("audio_track_index", audioTrackIndex)
            .putOpt("subtitle_track_index", subtitleTrackIndex)
            .put("subtitles_off", subtitlesOff)
        val root = json("POST", "/playback", body)
        return CoreModels.playback(root, origin)
    }
    override suspend fun heartbeat(playbackId: String) { json("POST", "/playback/${enc(playbackId)}/heartbeat", JSONObject()) }
    override suspend fun stopPlayback(playbackId: String) { json("DELETE", "/playback/${enc(playbackId)}") }
    override suspend fun updateProgress(profileId: String, media: Media, positionMillis: Long) {
        coreRequest("saveProgress", profileId, media, JSONObject().put("position", seconds(positionMillis)).putOpt("duration", media.durationMillis?.let(::seconds)))
    }
    override suspend fun nextEpisode(profileId: String, media: Media): NextResult {
        val result = coreRequest("nextEpisode", profileId, media)
        return NextResult(result.optString("status"), result.optJSONObject("item")?.media())
    }
    override suspend fun favorites(profileId: String): List<Media> = json("GET", "/profiles/${enc(profileId)}/favorites/page?limit=40").mediaArray("items")
    override suspend fun toggleFavorite(profileId: String, media: Media): Boolean = coreRequest("toggleFavorite", profileId, media).optBoolean("saved")
    override suspend fun queue(profileId: String): List<Media> = coroutineScope {
        val items = json("GET", "/profiles/${enc(profileId)}/continue/page?limit=40").mediaArray("items")
        val slots = Semaphore(3)
        items.map { item -> async {
            slots.withPermit {
                val rich = metadataOr(item)
                CoreModels.enrich(item, rich)
            }
        } }.awaitAll()
    }
    override suspend fun setQueueVisibility(profileId: String, media: Media, hidden: Boolean) {
        coreRequest("setQueueVisibility", profileId, media, JSONObject().put("hidden", hidden))
    }
    override suspend fun correctProgress(profileId: String, media: Media, action: String) {
        coreRequest("correctProgress", profileId, media, JSONObject().put("action", action).putOpt("duration", media.durationMillis?.let(::seconds)))
    }
    override suspend fun live(): List<LiveChannel> = json("GET", "/live?view=us&limit=80").liveChannels()
    override suspend fun livePage(request: LiveBrowseRequest): LiveBrowsePage {
        val root = JSONObject(uniffi.viptv_core.normalize("live", json("GET", livePath(request)).toString(), origin))
        return LiveBrowsePage(
            channels = root.array("channels").mapNotNull { it.optJSONObject()?.normalizedChannel() },
            total = root.optInt("total", 0).coerceAtLeast(0),
            request = request,
            searchScope = root.optString("searchScope").ifBlank { null },
        )
    }
    override suspend fun liveCategories(): List<LiveCategory> = JSONObject(uniffi.viptv_core.normalize("liveCategories", json("GET", "/live/categories?view=us").toString(), origin))
        .array("categories").mapNotNull { it.optJSONObject()?.let { item -> LiveCategory(item.getString("id"), item.getString("name"), item.getInt("count")) } }
    override suspend fun guide(channelId: String): List<GuideProgramme> {
        val response = JSONObject(uniffi.viptv_core.normalize("guide", json("GET", "/guide/${enc(channelId)}").toString(), origin))
        val labels = response.array("timeline").mapNotNull { value -> value.optJSONObject()?.let { tick -> (tick.getDouble("time") * 1000).toLong() to tick.getString("displayTime") } }.toMap()
        return response.array("programs").mapNotNull { it.optJSONObject()?.let { programme ->
            GuideProgramme(programme.getString("title"), (programme.getDouble("start") * 1000).toLong(), (programme.getDouble("end") * 1000).toLong(), programme.optString("description").ifBlank { null }, displayTime = programme.optString("displayTime").ifBlank { null }, timezone = response.getString("timezone"), timelineLabels = labels)
        } }
    }
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
        return DeviceSession(token, value.getString("refresh_token"), value.opt("profile_id")?.toString(), uniffi.viptv_core.normalize("tokens", value.toString(), origin))
    }
    private suspend fun coreRequest(operation: String, profileId: String, media: Media, values: JSONObject = JSONObject()): JSONObject {
        values.put("operation", operation).put("profileId", profileId).put("item", JSONObject(media.normalizedJson()))
        val request = org.viptv.core.wire.CoreJson.decode<org.viptv.core.wire.ApiRequest>(uniffi.viptv_core.normalize("request", values.toString(), origin))
        return json(request.method, request.path.removePrefix("/api"), request.body?.let { JSONObject(org.viptv.core.wire.CoreJson.encode(it)) })
    }
    private suspend fun json(method: String, path: String, body: JSONObject? = null): JSONObject = JSONObject(responseText(method, path, body))
    /** `/addons` is deliberately a raw JSON array in the Rust API. */
    private suspend fun jsonArray(method: String, path: String, body: JSONObject? = null): JSONArray = JSONArray(responseText(method, path, body))
    private sealed interface CallResult
    private class CallText(val text: String) : CallResult
    private class CallFailure(val status: Int, val message: String) : CallResult

    /**
     * A cancellable OkHttp boundary works in Android and host-JVM wire tests,
     * including PATCH. Calls are bounded and cancelled with their coroutine.
     */
    private suspend fun awaitResult(method: String, path: String, body: JSONObject?, bearer: String?): CallResult =
        suspendCancellableCoroutine { continuation ->
            val request = Request.Builder()
                .url(origin.trimEnd('/') + "/api" + path)
                .header("Accept", "application/json")
                .apply { bearer?.let { header("Authorization", "Bearer $it") } }
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
                        val text = try {
                            val source = it.body?.source()
                            if (source != null && source.request(2L * 1024 * 1024 + 1)) throw IOException("Response exceeds the size limit")
                            source?.readUtf8().orEmpty()
                        } catch (_: IOException) {
                            if (continuation.isActive) continuation.resumeWithException(IOException("Could not read server response"))
                            return
                        }
                        if (!it.isSuccessful) {
                            val message = runCatching { JSONObject(text.ifBlank { "{}" }).optString("error", "Request failed") }
                                .getOrDefault("Request failed")
                            if (continuation.isActive) continuation.resume(CallFailure(it.code, message))
                        } else if (continuation.isActive) {
                            continuation.resume(CallText(text))
                        }
                    }
                }
            })
        }

    /**
     * An authenticated 401 refreshes the session once and replays the request
     * with the rotated bearer; auth endpoints treat 401 as protocol rather
     * than expiry and never retry. Mirrors the TV client's single-refresh rule.
     */
    private suspend fun responseText(method: String, path: String, body: JSONObject? = null): String {
        val bearer = accessToken
        when (val first = awaitResult(method, path, body, bearer)) {
            is CallText -> return first.text
            is CallFailure -> {
                val refresher = onUnauthorized
                if (first.status == 401 && refresher != null && !path.startsWith("/auth/")) {
                    val refreshed = refresher()
                    if (refreshed != null && refreshed != bearer) {
                        when (val retry = awaitResult(method, path, body, refreshed)) {
                            is CallText -> return retry.text
                            is CallFailure -> throw GatewayError(retry.status, retry.message)
                        }
                    }
                }
                throw GatewayError(first.status, first.message)
            }
        }
    }
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

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

private fun JSONObject.profile() = CoreModels.profile(this)
private fun JSONObject.discoverCatalog(): DiscoverCatalog? = CoreModels.catalog(this)
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

private fun JSONObject.media(): Media = CoreModels.media(this)
private fun JSONObject.mediaArray(vararg keys: String): List<Media> {
    val page = org.viptv.core.wire.CoreJson.decode<org.viptv.core.wire.DiscoverPage>(uniffi.viptv_core.normalize("discover", toString(), ""))
    return page.items.map { CoreModels.mediaNormalized(it) }
}
private fun JSONObject.source(eventProvider: String? = null): Source = CoreModels.source(JSONObject(toString()).also {
    if (!it.has("provider") && eventProvider != null) it.put("provider", eventProvider)
})
private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
private fun JSONObject.array(vararg keys: String): List<Any?> = (keys.firstNotNullOfOrNull { optJSONArray(it) } ?: JSONArray()).let { array -> (0 until array.length()).map { index -> array.opt(index) } }
private fun Any?.optJSONObject(): JSONObject? = this as? JSONObject
private fun JSONObject.liveChannels(): List<LiveChannel> = JSONObject(uniffi.viptv_core.normalize("live", toString(), "")).array("channels").mapNotNull { it.optJSONObject()?.normalizedChannel() }
private fun JSONObject.normalizedChannel() = LiveChannel(getString("id"), getString("name"), optString("poster").takeUnless { it.isBlank() || it == "null" }, optString("category").takeUnless { it.isBlank() || it == "null" })
private fun JSONObject.addon() = Addon(get("id").toString(), optString("name"), optString("manifest_url"), optBoolean("enabled", true))
private fun PlaybackPreferences.body() = JSONObject().put("audio_language", audioLanguage).put("subtitle_language", subtitleLanguage).put("subtitles_enabled", subtitlesEnabled).put("subtitle_size", subtitleSize).put("subtitle_style", subtitleStyle).put("quality", quality).put("autoplay", autoplay)
private fun JSONObject.preferences(): PlaybackPreferences {
    val item = JSONObject(uniffi.viptv_core.normalize("androidPreferences", toString(), ""))
    return PlaybackPreferences(item.getString("audioLanguage"), item.getString("subtitleLanguage"), item.getBoolean("subtitlesEnabled"), item.getString("subtitleSize"), item.getString("subtitleStyle"), item.getString("quality"), item.getBoolean("autoplay"))
}
private fun seconds(millis: Long): Double = millis / 1_000.0
