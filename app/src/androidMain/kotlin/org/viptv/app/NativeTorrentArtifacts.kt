package org.viptv.app

import uniffi.playback_gateway_ffi.defaultTorrentOptions

/** ABI loading is a platform fact; it does not advertise qualified native playback. */
internal object NativeTorrentArtifacts {
    private val availability = NativeTorrentArtifactAvailability { defaultTorrentOptions() }

    fun isLoaded(): Boolean = availability.isLoaded()
}

internal class NativeTorrentArtifactAvailability(private val initialize: () -> Unit) {
    private val loaded: Boolean by lazy {
        try {
            initialize()
            true
        } catch (_: LinkageError) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    fun isLoaded(): Boolean = loaded
}
