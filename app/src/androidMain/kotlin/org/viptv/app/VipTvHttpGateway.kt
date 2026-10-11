package org.viptv.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import org.viptv.core.wire.HomeShelfPlan
import org.viptv.core.wire.HomeShelfRole

/** HTTP adapter for the documented Rust /api contract. It owns credentials and never logs them. */
class VipTvHttpGateway(
    private val origin: String,
    private var accessToken: String? = null,
    /** Coalesced session refresh for an authenticated 401; returns a fresh access token or null. */
    private val onUnauthorized: (suspend (String?) -> String?)? = null,
    private val television: Boolean = false,
    dns: okhttp3.Dns = okhttp3.Dns.SYSTEM,
) : BackendGateway {
    private val playbackV2 = V2PlaybackControl(origin, ::json)
    private val nativeControls = java.util.concurrent.ConcurrentHashMap.newKeySet<NativePlaybackControl>()
    private val nativeAuthGeneration = java.util.concurrent.atomic.AtomicLong()
    internal fun invalidateNativeScope() {
        nativeAuthGeneration.incrementAndGet()
        nativeControls.toList().forEach { it.retireLocal() }
        nativeControls.clear()
    }
    /** Qualification is an injected measured fact; production selection remains disabled by its owner. */
    internal fun nativePlaybackControl(
        scope: String,
        generation: Long,
        cache: NativeTorrentCache,
        jobs: kotlinx.coroutines.CoroutineScope,
        preventReads: () -> Unit,
        invalidated: () -> Unit,
        clock: NativePlaybackClock = androidNativePlaybackClock,
        startupStartedAtMillis: Long? = null,
    ): NativePlaybackControl {
        val epoch = nativeAuthGeneration.get()
        val current = { epoch == nativeAuthGeneration.get() }
        lateinit var control: NativePlaybackControl
        control = NativePlaybackControl(origin, scope, generation, current,
            NativePlaybackTransport(origin, { accessToken }, current, cache, onUnauthorized, client),
            playbackV2, jobs, clock, preventReads, invalidated, { nativeControls.remove(control) }, startupStartedAtMillis = startupStartedAtMillis)
        return control.also(nativeControls::add)
    }
    fun playbackRemainingMillis(id: String): Long? = playbackV2.remainingMillis(id)
    fun playbackRenewAfterMillis(id: String): Long? = playbackV2.renewAfterMillis(id)
    fun setAccessToken(value: String?) { if (value == null) invalidateNativeScope(); accessToken = value }
    private val titleArtwork = java.util.concurrent.ConcurrentHashMap<String, Media>()
    private var metadataEpoch = 0L
    private val metadataRequests = java.util.concurrent.atomic.AtomicLong()
    internal val metadataRequestCount: Long get() = metadataRequests.get()
    fun clearProfileCache() = synchronized(titleArtwork) { metadataEpoch++; titleArtwork.clear() }
    private val client = OkHttpClient.Builder()
        .dns(dns)
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    override suspend fun signIn(username: String, password: String, deviceName: String): DeviceSession = session(
        json("POST", "/auth/device/login", JSONObject().put("username", username.trim()).put("password", password).put("device_name", deviceName)))
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
    override suspend fun refresh(refreshToken: String): DeviceSession = session(json("POST", "/auth/device/refresh", JSONObject().put("refresh_token", refreshToken)), adopt = false)
    suspend fun foregroundIdentity(): org.viptv.core.wire.Identity {
        val root = json("GET", "/auth/me")
        return org.viptv.core.wire.CoreJson.decode<org.viptv.core.wire.Identity>(
            uniffi.viptv_core.normalize("identity", root.toString(), origin))
    }
    override suspend fun profiles(): Pair<List<Profile>, String?> {
        val root = json("GET", "/auth/me")
        val array = root.optJSONArray("profiles") ?: JSONArray()
        return (0 until array.length()).map { index -> array.getJSONObject(index).profile() } to root.opt("profile_id")?.toString()
    }
    override suspend fun selectProfile(profileId: String) { json("POST", "/auth/profile", JSONObject().put("profile_id", profileId)); invalidateNativeScope(); clearProfileCache() }
    override suspend fun catalogRevision(): String? = try {
        json("GET", "/catalogs/revision").getString("revision")
    } catch (error: GatewayError) {
        if (error.status == 404) null else throw error
    }
    override suspend fun home(profileId: String, onUpdate: (List<HomeShelf>) -> Unit): List<HomeShelf> = loadHome(profileId, onUpdate, null, {})
    override suspend fun refreshHome(profileId: String, previous: List<HomeShelf>, onIncomplete: () -> Unit, onUpdate: (List<HomeShelf>) -> Unit): List<HomeShelf> = loadHome(profileId, onUpdate, previous, onIncomplete)
    private suspend fun loadHome(profileId: String, onUpdate: (List<HomeShelf>) -> Unit, previous: List<HomeShelf>?, onIncomplete: () -> Unit): List<HomeShelf> = coroutineScope {
        // Shared core owns which shelves exist, their order, titles and limits;
        // this loop runs one fetch per planned shelf and publishes in plan order.
        val liveShelves = !television
        val loaded = mutableMapOf<String, HomeShelf>()
        var catalogs = emptyList<DiscoverCatalog>()
        var plan = SharedPresentation.homeLayout(catalogs, liveShelves).shelves
        val publisher = kotlinx.coroutines.sync.Mutex()
        fun shelfId(shelf: HomeShelfPlan): String =
            shelf.catalogIndex?.let { catalogs[it.toInt()].key.stableId } ?: shelf.title
        fun ordered(): List<HomeShelf> = plan.mapNotNull { loaded[shelfId(it)] }.filter { it.items.isNotEmpty() }
        suspend fun publish(shelf: HomeShelf) = publisher.withLock {
            loaded[shelf.id] = shelf
            onUpdate(ordered())
        }
        fun prior(id: String): List<Media> = previous?.firstOrNull { it.id == id }?.items.orEmpty()
        suspend fun optional(fallback: List<Media> = emptyList(), failed: () -> Unit = {}, load: suspend () -> List<Media>): List<Media> = try { load() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: GatewayError) { if (error.status in listOf(401, 403)) throw error else { failed(); fallback } }
        catch (_: IOException) { failed(); fallback }
        val catalogList = async {
            try { catalogs() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: GatewayError) { if (previous != null || error.status in listOf(401, 403)) throw error else emptyList() }
            catch (error: IOException) { if (previous != null) throw error else emptyList() }
        }
        val fixed = plan.map { shelf -> async {
            val title = shelf.title
            when (shelf.role) {
                HomeShelfRole.CONTINUEWATCHING -> {
                    val savedItems = optional(prior(title)) {
                        val started = System.nanoTime() / 1_000_000
                        json("GET", "/profiles/" + enc(profileId) + "/continue/page?limit=" + (shelf.limit ?: 40)).mediaArray().also {
                            if (BuildConfig.PLAYBACK_DIAGNOSTICS) homeTimingLog("event=queue_response elapsed_ms=${(System.nanoTime() / 1_000_000) - started}")
                        }
                    }
                    val items = if (television) {
                        val metadataGate = Semaphore(3)
                        savedItems.map { media -> async(Dispatchers.Default) {
                            metadataGate.withPermit {
                                val metadata = metadataOr(media)
                                try { CoreModels.enrich(media, metadata) } catch (_: Exception) { media }
                            }
                        } }.awaitAll()
                    } else savedItems
                    publish(HomeShelf(title, items, true))
                }
                HomeShelfRole.RECENTLIVE -> publish(HomeShelf(title, optional(prior(title)) {
                    liveV2(LiveCatalogQuery(collection = "recent", limit = (shelf.limit ?: 24).toInt())).items.map { CoreModels.mediaNormalized(it) }
                }))
                HomeShelfRole.MYLIST -> publish(HomeShelf(title, optional(prior(title)) { favorites(profileId) }, isMyListShelf = true))
                HomeShelfRole.LIVENOW -> publish(HomeShelf(title, optional(prior(title)) { this@VipTvHttpGateway.live().map(LiveChannel::asMedia) }))
                HomeShelfRole.CATALOG -> Unit
            }
        } }
        val available = catalogList.await()
        publisher.withLock {
            catalogs = available
            plan = SharedPresentation.homeLayout(available, liveShelves).shelves
            onUpdate(ordered())
        }
        val catalogGate = Semaphore(3)
        plan.filter { it.role == HomeShelfRole.CATALOG }.map { shelf -> async {
            val catalog = available[requireNotNull(shelf.catalogIndex).toInt()]
            val items = optional(prior(catalog.key.stableId), onIncomplete) { catalogGate.withPermit { discover(DiscoverPolicy.request(catalog, DiscoverPolicy.defaults(catalog), 0)).items } }
            publish(HomeShelf(shelf.title, items, id = catalog.key.stableId, contentType = catalog.key.type, catalogName = catalog.name))
        } }.awaitAll()
        fixed.awaitAll()
        publisher.withLock { ordered() }
    }
    override suspend fun discover(type: String, search: String?): List<Media> = json("GET", discoverPath(type, search = search)).mediaArray()
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
            items = root.mediaArray(),
            requestedSkip = request.skip,
            nextSkip = nextSkip,
            hasMore = hasMore,
        )
    }
    override suspend fun search(query: String, onUpdate: (SearchResults) -> Unit): SearchResults {
        if (query.isBlank()) return SearchResults(emptyList(), false)
        return coroutineScope {
            val gate = Semaphore(3)
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
            val catalogs = when (val result = attempt { catalogs() }) {
                is SearchAttempt.Value -> result.value
                SearchAttempt.Failure -> { partial = true; emptyList() }
            }
            // Shared core selects the searched catalogs, their titles, the live
            // channel search and every limit; Live TV follows the catalog sections.
            val plan = SharedPresentation.searchPlan(query, catalogs)
            if (plan.query.isEmpty()) return@coroutineScope SearchResults(emptyList(), partial)
            val limit = plan.sectionLimit.toInt()
            val liveRequest = async {
                if (plan.live) publish(plan.sections.size, attempt {
                    SearchSection(plan.liveTitle, liveV2(LiveCatalogQuery(search = plan.query, limit = plan.liveRequestLimit.toInt())).items
                        .map { CoreModels.mediaNormalized(it) }.take(limit))
                })
            }
            plan.sections.mapIndexed { index, section -> async {
                val catalog = catalogs[section.catalogIndex.toInt()]
                publish(index, attempt { gate.withPermit {
                    val items = discover(DiscoverPolicy.request(catalog, DiscoverPolicy.defaults(catalog) + ("search" to plan.query), 0)).items
                    SearchSection(section.title, items.distinctBy { HomeFocusPolicy.mediaKey(it) }.take(limit), catalog.key.stableId, catalog.key.type)
                } })
            } }.awaitAll()
            liveRequest.await()
            SearchResults(completed.values.toList(), partial)
        }
    }
    override suspend fun seriesProgress(profileId: String, seriesId: String): List<Media> =
        jsonArray("GET", "/profiles/${enc(profileId)}/progress/series?series_id=${enc(seriesId)}").objects().take(2000).map { it.media() }
    override suspend fun metadata(media: Media): Media {
        val timingStart = System.nanoTime() / 1_000_000
        val target = SharedPresentation.metadataTargets(listOf(media)).single() ?: return media
        val key = target.type + "\u0000" + target.id
        val epoch = synchronized(titleArtwork) { titleArtwork[key]?.let {
            if (BuildConfig.PLAYBACK_DIAGNOSTICS) homeTimingLog("event=metadata_cache_hit")
            return it
        }; metadataEpoch }
        if (BuildConfig.PLAYBACK_DIAGNOSTICS) runCatching {
            android.util.Log.i("MetadataRequestDiagnostic", "request_count=${metadataRequests.incrementAndGet()}")
        }
        val result = try { withContext(Dispatchers.Default) {
            val request = org.viptv.core.wire.CoreJson.decode<org.viptv.core.wire.ApiRequest>(uniffi.viptv_core.normalize("request",
                JSONObject().put("operation", "metadata").put("item", SharedPresentation.metadataFacts(media)).toString(), origin))
            json(request.method, request.path.removePrefix("/api")).optJSONObject("meta")?.media() ?: media
        } } finally {
            if (BuildConfig.PLAYBACK_DIAGNOSTICS) homeTimingLog("event=metadata_complete elapsed_ms=${(System.nanoTime() / 1_000_000) - timingStart}")
        }
        synchronized(titleArtwork) { if (epoch == metadataEpoch) {
            if (titleArtwork.size >= 256) titleArtwork.keys.firstOrNull()?.let { titleArtwork.remove(it) }
            titleArtwork[key] = result
        } }
        return result
    }
    override suspend fun metadataBatch(items: List<Media>): Map<String, Media> {
        val epoch = synchronized(titleArtwork) { metadataEpoch }
        val unique = items.filter { it.type != "live" }.distinctBy { it.type + ":" + it.id }.take(16)
        if (unique.isEmpty()) return emptyMap()
        val body = JSONObject().put("items", JSONArray().also { array -> unique.forEach { media ->
            array.put(JSONObject().put("type", if (media.type == "episode") "series" else media.type)
                .put("id", if (media.type == "episode") media.seriesId ?: media.id else media.id))
        } })
        val response = try { json("POST", "/meta/batch", body) }
        catch (error: GatewayError) {
            if (error.status != 404) throw error
            return emptyMap()
        }
        val resolved = mutableMapOf<String, Media>()
        val results = response.getJSONArray("items")
        for (index in 0 until results.length()) {
            val row = results.getJSONObject(index)
            val meta = row.optJSONObject("meta") ?: continue
            val rich = meta.media()
            unique.filter { media ->
                (if (media.type == "episode") "series" else media.type) == row.optString("type") &&
                    (if (media.type == "episode") media.seriesId ?: media.id else media.id) == row.optString("id")
            }.forEach { media ->
                resolved[media.type + ":" + media.id] = rich
                synchronized(titleArtwork) { if (epoch == metadataEpoch) {
                    val type = if (media.type == "episode") "series" else media.type
                    val id = if (type == "series") media.seriesId ?: media.id else media.id
                    titleArtwork[type + "\u0000" + id] = rich
                } }
            }
        }
        return resolved
    }
    /** A failed enrichment lookup must never discard the item the caller already has. */
    private suspend fun metadataOr(fallback: Media, lookup: Media = fallback): Media = try {
        metadata(lookup)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        fallback
    }
    override suspend fun sources(media: Media, onProducerUpdate: (List<SourceProducerOutcome>) -> Unit, onUpdate: (List<Source>) -> Unit): List<Source> {
        if (media.type == "live") return listOf(liveSourceV2(media.id)).also(onUpdate)
        // The discovery request body, poll path, cursor, deduplication, budget
        // and completion rules all come from the shared Rust core; this loop
        // owns only transport, the update callback, cancellation and the fixed
        // poll interval. Roku's three-minute discovery budget lives in Rust.
        val request = org.viptv.core.wire.CoreJson.decode<org.viptv.core.wire.ApiRequest>(
            uniffi.viptv_core.normalize("request", JSONObject()
                .put("operation", "sourcesV2")
                .put("item", JSONObject(media.normalizedJson())).toString(), origin)
        )
        val id = json(request.method, request.path.removePrefix("/api"), request.body?.let { JSONObject(org.viptv.core.wire.CoreJson.encode(it)) }).getString("id")
        var state = jsonStepState()
        val sourceViews = mutableMapOf<String, Pair<String, Source>>()
        while (true) {
            val poll = json("GET", pollPath(id, state))
            val (output, accumulated, producers) = withContext(Dispatchers.Default) {
                val output = step(state, poll)
                Triple(output, output.sources(sourceViews), SharedPresentation.producers(output))
            }
            onProducerUpdate(producers)
            onUpdate(accumulated)
            if (output.optBoolean("done")) {
                val failure = output.optJSONObject("state")?.optJSONArray("errors")?.optJSONObject(0)
                if (accumulated.isEmpty() && failure != null) throw GatewayError(502, failure.getString("message"), failure.optString("code").takeUnless { it.isBlank() || it == "null" })
                return accumulated
            }
            state = output.getJSONObject("state")
            delay(1_500)
        }
    }
    private fun pollPath(id: String, state: JSONObject): String {
        val request = org.viptv.core.wire.CoreJson.decode<org.viptv.core.wire.ApiRequest>(
            uniffi.viptv_core.normalize("request", JSONObject()
                .put("operation", "sourcesPollV2")
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
    private fun JSONObject.sources(cache: MutableMap<String, Pair<String, Source>>): List<Source> {
        val array = optJSONArray("sources") ?: return emptyList()
        // The reducer's sources are already Rust-normalized: decode the wire
        // type directly, with the display projection as the only second pass.
        val result = ArrayList<Source>(array.length())
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            val encoded = row.toString()
            val id = row.getString("id")
            val cached = cache[id]?.takeIf { it.first == encoded }
            if (cached != null) { result.add(cached.second); continue }
            val normalized = org.viptv.core.wire.CoreJson.decode<org.viptv.core.wire.MediaSource>(encoded)
            val display = org.viptv.core.wire.CoreJson.decode<org.viptv.core.wire.SourcePresentation>(
                uniffi.viptv_core.normalize("sourceDisplay", org.viptv.core.wire.CoreJson.encode(normalized), "")
            )
            val source = Source(normalized.id, normalized.provider.orEmpty(), display.title, display.body, normalized.sourceAddonId, normalized.sourceFingerprint, normalized.quality, normalized.audio, displayResolved = true, providerKey = display.providerKey, providerLabel = display.providerLabel)
            cache[id] = encoded to source
            result.add(source)
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
        delivery: PlaybackDeliveryOptions,
    ): PlaybackLaunch {
        return playbackV2.start(playbackRequest(source, positionMillis, capabilities, audioTrackIndex,
            subtitleTrackIndex, subtitlesOff, delivery))
    }

    /** Both delivery branches use the actual Rust request seam and a fresh idempotency key. */
    internal suspend fun playbackRequest(
        source: Source,
        positionMillis: Long,
        capabilities: PlaybackClientCapabilities,
        audioTrackIndex: Int? = null,
        subtitleTrackIndex: Int? = null,
        subtitlesOff: Boolean = false,
        delivery: PlaybackDeliveryOptions = PlaybackDeliveryOptions(),
        preferredAudioLanguage: String? = null,
        preferredSubtitleLanguage: String? = null,
        preferredSubtitlesEnabled: Boolean? = null,
    ): JSONObject {
        val selectedSource = source.channelId?.let { liveSourceV2(it) } ?: source
        val playback = JSONObject().put("streamId", selectedSource.id)
            .put("position", if (source.channelId != null) 0.0 else seconds(positionMillis))
            .put("capabilities", capabilities.toCoreJson()).putOpt("audioTrackIndex", audioTrackIndex)
            .putOpt("subtitleTrackIndex", subtitleTrackIndex).put("subtitlesOff", subtitlesOff)
            .put("managedOnly", delivery.forceGateway).put("forceTranscode", delivery.forceTranscode)
        val intent = JSONObject().put("requestId", java.util.UUID.randomUUID().toString())
            .put("platform", if (television) "android_tv" else "android").put("playback", playback)
        if (preferredAudioLanguage != null || preferredSubtitleLanguage != null || preferredSubtitlesEnabled != null) {
            intent.put("preferences", JSONObject().putOpt("audioLanguage", preferredAudioLanguage)
                .putOpt("subtitleLanguage", preferredSubtitleLanguage).putOpt("subtitlesEnabled", preferredSubtitlesEnabled))
        }
        return try { JSONObject(uniffi.viptv_core.normalize("playbackV2Intent", intent.toString(), origin)).apply {
            if (source.channelId == null) getJSONObject("client").put("nativeTorrent", JSONObject().put("version", 2).put("networkPolicy", "public_discovery_verified_v2"))
        } }
        catch (_: Exception) { throw GatewayError(400, "This device could not report a supported playback configuration.", "invalid_playback_request") }
    }
    override suspend fun heartbeat(playbackId: String) {
        playbackV2.renew(playbackId)
    }
    override suspend fun stopPlayback(playbackId: String) {
        playbackV2.stop(playbackId)
    }
    override suspend fun updateProgress(profileId: String, media: Media, positionMillis: Long) {
        coreRequest("saveProgress", profileId, media, JSONObject().put("position", seconds(positionMillis)).putOpt("duration", media.durationMillis?.let(::seconds)))
    }
    override suspend fun nextEpisode(profileId: String, media: Media): NextResult {
        val result = coreRequest("nextEpisode", profileId, media)
        return NextResult(result.optString("status"), result.optJSONObject("item")?.media())
    }
    override suspend fun favorites(profileId: String): List<Media> = json("GET", "/profiles/${enc(profileId)}/favorites/page?limit=40").mediaArray()
    override suspend fun toggleFavorite(profileId: String, media: Media): Boolean = coreRequest("toggleFavorite", profileId, media).optBoolean("saved")
    override suspend fun queue(profileId: String): List<Media> = coroutineScope {
        val items = json("GET", "/profiles/${enc(profileId)}/continue/page?limit=40").mediaArray()
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
    override suspend fun live(): List<LiveChannel> = liveV2(LiveCatalogQuery(limit = 24)).items.map { LiveChannel(it.id, it.name, it.poster) }
    override suspend fun liveV2(query: LiveCatalogQuery): org.viptv.core.wire.LiveCatalogPage {
        val page = liveV2Decode<org.viptv.core.wire.LiveCatalogPage>("liveCatalogV2", liveV2Control(query.coreInput("livePageV2")))
        CorePlaybackPolicy.requireLivePage(CorePlaybackPolicy.livePage(
            page.catalogId, page.generation, page.items.map { it.id }, page.items.map { it.name },
            page.nextCursor, page.previousCursor, requestedCatalogId = query.catalogId, limit = query.limit, categories = false,
        ))
        return page
    }
    override suspend fun liveCategoriesV2(query: LiveCatalogQuery): org.viptv.core.wire.LiveCatalogCategories {
        val page = liveV2Decode<org.viptv.core.wire.LiveCatalogCategories>("liveCategoriesV2", liveV2Control(query.coreInput("liveCategoriesV2")))
        CorePlaybackPolicy.requireLivePage(CorePlaybackPolicy.livePage(
            page.catalogId, page.generation, page.items.map { it.id }, page.items.map { it.name },
            page.nextCursor, page.previousCursor, requestedCatalogId = query.catalogId, limit = query.limit, categories = true,
        ))
        return page
    }
    override suspend fun liveSourceV2(channelId: String): Source = CoreModels.sourceNormalized(
        liveV2Decode("liveSourceV2",liveV2Control(JSONObject().put("operation","liveSourceV2").put("id",channelId))))
    override suspend fun guideV2(channelId: String): List<GuideProgramme> {
        val response=liveV2Control(JSONObject().put("operation","liveGuideV2").put("id",channelId))
        val normalized = try { JSONObject(uniffi.viptv_core.normalize("guide",response.toString(),origin)) }
        catch (_: Exception) { throw GatewayError(502,"The server returned invalid guide data. Update the app/server or retry.","invalid_catalog_response") }
        return guideView(normalized)
    }
    private suspend fun liveV2Control(input: JSONObject): JSONObject {
        val request = try { org.viptv.core.wire.CoreJson.decode<org.viptv.core.wire.ApiRequest>(uniffi.viptv_core.normalize("request",input.toString(),origin)) }
        catch (_: Exception) { throw GatewayError(400,"The live playlist request is invalid. Reload the guide.","invalid_catalog_query") }
        val text = responseText(request.method,request.path.removePrefix("/api"),request.body?.let { JSONObject(org.viptv.core.wire.CoreJson.encode(it)) })
        return try { JSONObject(text) }
        catch (_: Exception) { throw GatewayError(502,"The server returned invalid live playlist data. Update the app/server or reload the guide.","invalid_catalog_response") }
    }
    private inline fun <reified T> liveV2Decode(kind: String,value: JSONObject): T = try {
        org.viptv.core.wire.CoreJson.decode(uniffi.viptv_core.normalize(kind,value.toString(),origin))
    } catch (_: Exception) { throw GatewayError(502,"The server returned invalid live playlist data. Update the app/server or reload the guide.","invalid_catalog_response") }
    override suspend fun livePage(request: LiveBrowseRequest): LiveBrowsePage {
        val query = when (val filter = request.filter) {
            LiveChannelFilter.AllUs -> LiveCatalogQuery()
            LiveChannelFilter.MyChannels -> LiveCatalogQuery(collection = "favorites")
            LiveChannelFilter.Recent -> LiveCatalogQuery(collection = "recent")
            is LiveChannelFilter.Category -> LiveCatalogQuery(categoryId = filter.id)
            is LiveChannelFilter.Search -> LiveCatalogQuery(search = filter.query)
        }.copy(cursor = request.cursor, limit = request.limit, catalogId = request.catalogId)
        val page = liveV2(query)
        return LiveBrowsePage(
            channels = page.items.map { LiveChannel(it.id, it.name, it.poster,
                (it.raw["category"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeUnless { category -> category == "null" }) },
            request = request, catalogId = page.catalogId, generation = page.generation,
            nextCursor = page.nextCursor, previousCursor = page.previousCursor,
        )
    }
    override suspend fun liveCategories(): List<LiveCategory> = liveCategoriesV2(LiveCatalogQuery(limit = 200)).items.map { LiveCategory(it.id, it.name) }
    override suspend fun guide(channelId: String): List<GuideProgramme> = guideV2(channelId)
    private fun guideView(response: JSONObject): List<GuideProgramme> {
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
    override suspend fun logout() { json("POST", "/auth/logout", JSONObject()); invalidateNativeScope() }
    private fun session(value: JSONObject, adopt: Boolean = true): DeviceSession {
        val token = value.getString("access_token")
        val session = DeviceSession(token, value.getString("refresh_token"), value.opt("profile_id")?.takeUnless { it == JSONObject.NULL }?.toString(), uniffi.viptv_core.normalize("tokens", value.toString(), origin))
        if (adopt) { invalidateNativeScope(); clearProfileCache(); accessToken = token }
        return session
    }
    private suspend fun coreRequest(operation: String, profileId: String, media: Media, values: JSONObject = JSONObject()): JSONObject {
        values.put("operation", operation).put("profileId", profileId).put("item", JSONObject(media.normalizedJson()))
        val request = org.viptv.core.wire.CoreJson.decode<org.viptv.core.wire.ApiRequest>(uniffi.viptv_core.normalize("request", values.toString(), origin))
        return json(request.method, request.path.removePrefix("/api"), request.body?.let { JSONObject(org.viptv.core.wire.CoreJson.encode(it)) })
    }
    private suspend fun json(method: String, path: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.Default) { JSONObject(responseText(method, path, body)) }
    /** `/addons` is deliberately a raw JSON array in the Rust API. */
    private suspend fun jsonArray(method: String, path: String, body: JSONObject? = null): JSONArray =
        withContext(Dispatchers.Default) { JSONArray(responseText(method, path, body)) }
    private sealed interface CallResult
    private class CallText(val text: String) : CallResult
    private class CallFailure(val status: Int, val message: String, val code: String?) : CallResult

    /**
     * A cancellable OkHttp boundary works in Android and host-JVM wire tests,
     * including PATCH. Calls are bounded and cancelled with their coroutine.
     */
    private suspend fun awaitResult(method: String, path: String, body: JSONObject?, bearer: String?): CallResult =
        suspendCancellableCoroutine { continuation ->
            val request = Request.Builder()
                .url(origin.trimEnd('/') + "/api" + path)
                .header("Accept", "application/json")
                .apply { if (path == "/auth/device/login") header("Origin", origin) }
                .apply { bearer?.let { header("Authorization", "Bearer $it") } }
                .method(method, body?.toString()?.toRequestBody(JSON_MEDIA_TYPE)
                    ?: if (method in listOf("POST", "PUT", "PATCH")) "".toRequestBody(null) else null)
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
                            val failure = runCatching {
                                val error = runCatching { JSONObject(text.ifBlank { "{}" }) }.getOrDefault(JSONObject())
                                error.put("status", it.code)
                                JSONObject(uniffi.viptv_core.normalize("apiError", error.toString(), ""))
                            }.getOrDefault(JSONObject().put("message", "The server could not complete this request."))
                            if (continuation.isActive) continuation.resume(CallFailure(it.code, failure.getString("message"), failure.optString("code").takeUnless { code -> code.isBlank() || code == "null" }))
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
                if (first.status == 401 && refresher != null && (!path.startsWith("/auth/") || path == "/auth/me")) {
                    val refreshed = refresher(bearer)
                    if (refreshed != null && refreshed != bearer) {
                        when (val retry = awaitResult(method, path, body, refreshed)) {
                            is CallText -> return retry.text
                            is CallFailure -> throw GatewayError(retry.status, retry.message, retry.code)
                        }
                    }
                }
                throw GatewayError(first.status, first.message, first.code)
            }
        }
    }
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

}
class GatewayError(val status: Int, override val message: String, val code: String? = null) : IllegalStateException(message)

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
/** Core owns the page-key order (`metas`, then `items`, then `rows`); callers do not choose keys. */
private suspend fun JSONObject.mediaArray(): List<Media> = withContext(Dispatchers.Default) {
    val page = org.viptv.core.wire.CoreJson.decode<org.viptv.core.wire.DiscoverPage>(uniffi.viptv_core.normalize("discover", this@mediaArray.toString(), ""))
    page.items.map { CoreModels.mediaNormalized(it) }
}
private fun JSONObject.source(eventProvider: String? = null): Source = CoreModels.source(JSONObject(toString()).also {
    if (!it.has("provider") && eventProvider != null) it.put("provider", eventProvider)
})
private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
private fun JSONObject.array(vararg keys: String): List<Any?> = (keys.firstNotNullOfOrNull { optJSONArray(it) } ?: JSONArray()).let { array -> (0 until array.length()).map { index -> array.opt(index) } }
private fun Any?.optJSONObject(): JSONObject? = this as? JSONObject
private fun JSONObject.addon() = Addon(get("id").toString(), optString("name"), optString("manifest_url"), optBoolean("enabled", true))
// Ignore server `quality` values; preference reads and writes use only supported fields.
private fun PlaybackPreferences.body() = JSONObject().put("audio_language", audioLanguage).put("subtitle_language", subtitleLanguage).put("subtitles_enabled", subtitlesEnabled).put("subtitle_size", subtitleSize).put("subtitle_style", subtitleStyle).put("autoplay", autoplay)
private fun JSONObject.preferences(): PlaybackPreferences {
    val item = JSONObject(uniffi.viptv_core.normalize("androidPreferences", toString(), ""))
    return PlaybackPreferences(item.getString("audioLanguage"), item.getString("subtitleLanguage"), item.getBoolean("subtitlesEnabled"), item.getString("subtitleSize"), item.getString("subtitleStyle"), item.getBoolean("autoplay"))
}
private fun seconds(millis: Long): Double = millis / 1_000.0
