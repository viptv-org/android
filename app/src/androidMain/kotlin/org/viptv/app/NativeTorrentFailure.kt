package org.viptv.app

import org.json.JSONObject
import uniffi.playback_gateway_ffi.TorrentException
import uniffi.viptv_core.normalize

/** Closed measured facts cross the shared projection; dependency diagnostics stay private. */
internal class NativeTorrentFailure internal constructor(val reason: String) :
    RuntimeException("Native playback failed")

internal fun nativeTorrentFailure(error: Exception): Exception = when (error) {
    is TorrentException.StartupTimeout -> NativeTorrentFailure("native_acquisition_timeout")
    is TorrentException.PayloadLimit -> NativeTorrentFailure("native_payload_limit")
    is TorrentException.StorageUnavailable -> NativeTorrentFailure("native_storage_unavailable")
    is TorrentException.CacheUnavailable -> NativeTorrentFailure("native_cache_unavailable")
    is TorrentException.MetadataInvalid -> NativeTorrentFailure("native_metadata_invalid")
    is TorrentException.FileUnavailable -> NativeTorrentFailure("native_file_unavailable")
    is TorrentException -> NativeTorrentFailure("native_playback_failed")
    is java.io.IOException -> NativeTorrentFailure("native_network_unavailable")
    is java.util.concurrent.TimeoutException -> NativeTorrentFailure("native_acquisition_timeout")
    is GatewayError, is NativeTorrentFailure, is NativeTorrentCacheUnavailable,
    is NativeTorrentCoordinatorUnavailable, is kotlinx.coroutines.CancellationException -> error
    else -> NativeTorrentCoordinatorUnavailable()
}

internal fun nativeTorrentFailureMessage(reason: String): String = JSONObject(normalize(
    "nativeTorrent", JSONObject().put("operation", "failure").put("reason", reason).toString(), "",
)).getString("message")
