package org.viptv.app

interface BackendGateway {
    suspend fun signIn(username: String, password: String, deviceName: String): DeviceSession
    suspend fun startDevicePairing(deviceName: String): DeviceCode
    suspend fun exchangeDeviceCode(code: String): DevicePollResult
    suspend fun refresh(refreshToken: String): DeviceSession
    suspend fun profiles(): Pair<List<Profile>, String?>
    suspend fun selectProfile(profileId: String)
    suspend fun home(profileId: String, onUpdate: (List<HomeShelf>) -> Unit = {}): List<HomeShelf>
    suspend fun discover(type: String = "movie", search: String? = null): List<Media>
    suspend fun catalogs(): List<DiscoverCatalog>
    suspend fun discover(request: CatalogDiscoverRequest): DiscoverPage
    suspend fun search(query: String, onUpdate: (SearchResults) -> Unit = {}): SearchResults
    suspend fun metadata(media: Media): Media
    suspend fun seriesProgress(profileId: String, seriesId: String): List<Media> = emptyList()
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
