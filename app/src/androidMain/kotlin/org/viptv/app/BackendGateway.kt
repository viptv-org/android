package org.viptv.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

interface BackendGateway {
    suspend fun startDevicePairing(deviceName: String): DeviceCode
    suspend fun exchangeDeviceCode(code: String): DeviceSession?
    suspend fun refresh(refreshToken: String): DeviceSession
    suspend fun profiles(): Pair<List<Profile>, String?>
    suspend fun selectProfile(profileId: String)
    suspend fun home(profileId: String): List<HomeShelf>
    suspend fun discover(type: String = "movie", search: String? = null): List<Media>
    suspend fun metadata(media: Media): Media
    suspend fun sources(media: Media): List<Source>
    suspend fun playback(source: Source, positionMillis: Long): PlaybackLaunch
    suspend fun updateProgress(profileId: String, media: Media, positionMillis: Long)
    suspend fun favorites(profileId: String): List<Media>
    suspend fun toggleFavorite(profileId: String, media: Media): Boolean
    suspend fun queue(profileId: String): List<Media>
    suspend fun setQueueVisibility(profileId: String, media: Media, hidden: Boolean)
    suspend fun correctProgress(profileId: String, media: Media, action: String)
    suspend fun live(): List<LiveChannel>
    suspend fun guide(channelId: String): List<GuideProgramme>
    suspend fun preferences(profileId: String): PlaybackPreferences
    suspend fun savePreferences(profileId: String, preferences: PlaybackPreferences)
    suspend fun addons(): List<Addon>
    suspend fun setAddonEnabled(addon: Addon, enabled: Boolean)
    suspend fun removeAddon(addon: Addon)
    suspend fun createProfile(name: String, avatarStyle: String, avatarSeed: String?): Profile
    suspend fun updateProfile(profile: Profile, name: String, avatarStyle: String, avatarSeed: String?): Profile
    suspend fun deleteProfile(profile: Profile)
    suspend fun unlockParent(pin: String)
    suspend fun logout()
}

data class PlaybackLaunch(val sessionId: String, val url: String, val headers: Map<String, String> = emptyMap())

/** HTTP adapter for the documented Rust /api contract. It owns credentials and never logs them. */
class VipTvHttpGateway(private val origin: String, private var accessToken: String? = null) : BackendGateway {
    override suspend fun startDevicePairing(deviceName: String): DeviceCode = json("POST", "/auth/device/code", JSONObject().put("device_name", deviceName)).let {
        DeviceCode(it.getString("device_code"), it.getString("user_code"), it.getString("verification_uri"), it.optString("qr_uri").ifBlank { null }, it.optLong("interval", 5))
    }
    override suspend fun exchangeDeviceCode(code: String): DeviceSession? = try {
        session(json("POST", "/auth/device/token", JSONObject().put("device_code", code)))
    } catch (error: GatewayError) { if (error.status == 428 || error.status == 404) null else throw error }
    override suspend fun refresh(refreshToken: String): DeviceSession = session(json("POST", "/auth/device/refresh", JSONObject().put("refresh_token", refreshToken)))
    override suspend fun profiles(): Pair<List<Profile>, String?> {
        val root = json("GET", "/auth/me")
        val array = root.optJSONArray("profiles") ?: JSONArray()
        return (0 until array.length()).map { index -> array.getJSONObject(index).profile() } to root.opt("profile_id")?.toString()
    }
    override suspend fun selectProfile(profileId: String) { json("POST", "/auth/profile", JSONObject().put("profile_id", profileId)) }
    override suspend fun home(profileId: String): List<HomeShelf> {
        val continuing = json("GET", "/profiles/${enc(profileId)}/continue/page?limit=40").mediaArray("items", "rows", "metas")
        val recent = json("GET", "/profiles/${enc(profileId)}/progress/page?limit=40").mediaArray("items", "rows", "metas")
        val movies = discover("movie")
        return listOf(HomeShelf("Continue Watching", continuing), HomeShelf("Recently Watched", recent), HomeShelf("Trending", movies)).filter { it.items.isNotEmpty() }
    }
    override suspend fun discover(type: String, search: String?): List<Media> = json("GET", buildString { append("/discover?type="); append(enc(type)); if (!search.isNullOrBlank()) append("&search=").append(enc(search)) }).mediaArray("metas", "items", "rows")
    override suspend fun metadata(media: Media): Media = json("GET", "/meta/${enc(media.type)}/${enc(media.id)}").optJSONObject("meta")?.media() ?: media
    override suspend fun sources(media: Media): List<Source> {
        val job = json("POST", "/streams", JSONObject().put("id", media.id).put("type", media.type).put("name", media.name).putOpt("series_id", media.seriesId).putOpt("season", media.season).putOpt("episode", media.episode))
        val id = job.getString("id")
        var after = 0
        repeat(20) {
            val poll = json("GET", "/streams/${enc(id)}?after=$after")
            val events = poll.optJSONArray("events") ?: JSONArray()
            val sources = mutableListOf<Source>()
            for (i in 0 until events.length()) {
                val event = events.getJSONObject(i); after = maxOf(after, event.optInt("seq", after))
                val stream = event.optJSONObject("source") ?: event.optJSONObject("stream") ?: continue
                sources += stream.source()
            }
            if (sources.isNotEmpty() || poll.optBoolean("done")) return sources.distinctBy(Source::id)
            Thread.sleep(250)
        }
        return emptyList()
    }
    override suspend fun playback(source: Source, positionMillis: Long): PlaybackLaunch {
        val root = json("POST", "/playback", JSONObject().put("stream_id", source.id).put("position", positionMillis).put("capabilities", JSONObject().put("platform", "android-tv").put("direct", true)))
        return PlaybackLaunch(root.getString("id"), root.optString("url"), root.optJSONObject("headers")?.headers() ?: emptyMap())
    }
    override suspend fun updateProgress(profileId: String, media: Media, positionMillis: Long) {
        json("PUT", "/profiles/${enc(profileId)}/progress", JSONObject().put("id", media.id).put("type", media.type).put("name", media.name).put("position", positionMillis).put("duration", media.durationMillis ?: 0))
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
    override suspend fun guide(channelId: String): List<GuideProgramme> = json("GET", "/guide/${enc(channelId)}").array("programmes", "programs", "items").mapNotNull { it.optJSONObject()?.programme() }
    override suspend fun preferences(profileId: String): PlaybackPreferences = json("GET", "/profiles/${enc(profileId)}/preferences").preferences()
    override suspend fun savePreferences(profileId: String, preferences: PlaybackPreferences) {
        json("PUT", "/profiles/${enc(profileId)}/preferences", preferences.body())
    }
    override suspend fun addons(): List<Addon> = json("GET", "/addons").array("items").mapNotNull { it.optJSONObject()?.addon() }
    override suspend fun setAddonEnabled(addon: Addon, enabled: Boolean) { json("PATCH", "/addons/${enc(addon.id)}", JSONObject().put("enabled", enabled)) }
    override suspend fun removeAddon(addon: Addon) { json("DELETE", "/addons/${enc(addon.id)}") }
    override suspend fun createProfile(name: String, avatarStyle: String, avatarSeed: String?): Profile = json("POST", "/profiles", JSONObject().put("name", name).put("avatar_style", avatarStyle).putOpt("avatar_seed", avatarSeed)).profile()
    override suspend fun updateProfile(profile: Profile, name: String, avatarStyle: String, avatarSeed: String?): Profile = json("PATCH", "/profiles/${enc(profile.id)}", JSONObject().put("name", name).put("avatar_style", avatarStyle).putOpt("avatar_seed", avatarSeed)).profile()
    override suspend fun deleteProfile(profile: Profile) { json("DELETE", "/profiles/${enc(profile.id)}") }
    override suspend fun unlockParent(pin: String) { json("POST", "/parent/unlock", JSONObject().put("pin", pin)) }
    override suspend fun logout() { json("POST", "/auth/logout", JSONObject()) }
    private fun session(value: JSONObject): DeviceSession {
        val token = value.getString("access_token"); accessToken = token
        return DeviceSession(token, value.getString("refresh_token"), value.opt("profile_id")?.toString())
    }
    private suspend fun json(method: String, path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL(origin.trimEnd('/') + "/api" + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 10_000; readTimeout = 20_000; setRequestProperty("Accept", "application/json")
            accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json"); outputStream.bufferedWriter().use { it.write(body.toString()) } }
        }
        val status = connection.responseCode; val text = (if (status in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
        if (status !in 200..299) throw GatewayError(status, JSONObject(text.ifBlank { "{}" }).optString("error", "Request failed"))
        JSONObject(text)
    }
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}
class GatewayError(val status: Int, override val message: String) : IllegalStateException(message)
private fun JSONObject.profile() = Profile(get("id").toString(), getString("name"), optString("avatar_url").ifBlank { null }, optBoolean("kids"), optBoolean("is_primary"), optString("avatar_style", "critters"), optString("avatar_seed").ifBlank { null })
private fun JSONObject.media() = Media(get("id").toString(), optString("type", "movie"), optString("name", optString("title")), optString("poster").ifBlank { null }, optString("description").ifBlank { null }, optLong("position", 0), optLong("duration").takeIf { it > 0 }, optString("series_id").ifBlank { null }, optInt("season").takeIf { it > 0 }, optInt("episode").takeIf { it > 0 })
private fun JSONObject.mediaArray(vararg keys: String): List<Media> { val a = keys.firstNotNullOfOrNull { optJSONArray(it) } ?: JSONArray(); return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.media() } }
private fun JSONObject.source() = Source(optString("id", optString("stream_id")), optString("provider", optString("source", "Source")), optString("description", optString("filename")), optJSONObject("headers")?.headers() ?: emptyMap())
private fun JSONObject.headers(): Map<String, String> = keys().asSequence().associateWith { get(it).toString() }
private fun JSONObject.array(vararg keys: String): List<Any?> = (keys.firstNotNullOfOrNull { optJSONArray(it) } ?: JSONArray()).let { array -> (0 until array.length()).map { index -> array.opt(index) } }
private fun Any?.optJSONObject(): JSONObject? = this as? JSONObject
private fun JSONObject.channel() = LiveChannel(get("id").toString(), optString("name", optString("title")), optString("logo").ifBlank { null }, optString("category").ifBlank { null })
private fun JSONObject.programme() = GuideProgramme(optString("title", "No schedule available"), optLong("start", optLong("start_time")) * 1000, optLong("end", optLong("end_time")) * 1000, optString("description").ifBlank { null })
private fun JSONObject.addon() = Addon(get("id").toString(), optString("name"), optString("manifest_url"), optBoolean("enabled", true))
private fun Media.body() = JSONObject().put("id", id).put("type", type).put("name", name).putOpt("poster", poster).putOpt("series_id", seriesId).putOpt("season", season).putOpt("episode", episode).putOpt("duration", durationMillis)
private fun PlaybackPreferences.body() = JSONObject().put("audio_language", audioLanguage).put("subtitle_language", subtitleLanguage).put("subtitles_enabled", subtitlesEnabled).put("subtitle_size", subtitleSize).put("subtitle_style", subtitleStyle).put("quality", quality).put("autoplay", autoplay)
private fun JSONObject.preferences() = PlaybackPreferences(optString("audio_language", "en"), optString("subtitle_language", "en"), optBoolean("subtitles_enabled"), optString("subtitle_size", "normal"), optString("subtitle_style", "system"), optString("quality", "auto"), optBoolean("autoplay", true))
