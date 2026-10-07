package org.viptv.app

import android.content.Context
import android.os.SystemClock
import uniffi.playback_gateway_ffi.TorrentClient

/** Production construction has no source-selectable network or storage configuration. */
internal fun openNativeTorrentCache(context: Context): NativeTorrentCache {
    if (!NativeTorrentArtifacts.isLoaded()) throw NativeTorrentCacheUnavailable()
    return NativeTorrentCache.open(
        context.applicationContext.noBackupFilesDir,
        { directory, maxPayloadBytes ->
            NativeTorrentEngineCacheManager(TorrentClient.newNativePublic(directory.path, maxPayloadBytes.toULong()))
        },
        nowNanos = { SystemClock.elapsedRealtimeNanos() },
    )
}

/** One client proves same-owner payload sharing; grant acquisitions remain independently owned. */
internal class NativeTorrentEngineCacheManager(internal val client: TorrentClient) : NativeTorrentCacheManager {
    override fun hasFailedSettlement() = client.hasFailedSettlement()
    override fun closeAfterSettlement() = client.close()
    override fun toString() = "NativeTorrentEngineCacheManager(<redacted>)"
}
