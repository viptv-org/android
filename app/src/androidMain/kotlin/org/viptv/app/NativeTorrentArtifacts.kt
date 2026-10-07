package org.viptv.app

import uniffi.playback_gateway_ffi.defaultTorrentOptions

/** ABI loading is a required platform fact for native playback capability. */
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
