package org.viptv.app

import org.json.JSONObject
import uniffi.playback_gateway_ffi.TorrentException
import uniffi.viptv_core.normalize

/** Closed measured facts cross the shared projection; dependency diagnostics stay private. */
internal class NativeTorrentFailure internal constructor(val reason: String) :
    RuntimeException("Native playback failed")

/** Remains an IO failure so heartbeat retries retain the last accepted authority deadline. */
internal class NativePlaybackNetworkFailure(val reason: String) : java.io.IOException("Playback network failed")

internal fun nativePlaybackNetworkFailure(error: java.io.IOException): NativePlaybackNetworkFailure =
    NativePlaybackNetworkFailure((nativeTorrentFailure(error) as NativeTorrentFailure).reason)

internal fun nativeTorrentFailure(error: Exception): Exception = when (error) {
    is NativePlaybackNetworkFailure -> NativeTorrentFailure(error.reason)
    is TorrentException.StartupTimeout -> NativeTorrentFailure("native_acquisition_timeout")
    is TorrentException.SessionTimeout -> NativeTorrentFailure("native_session_timeout")
    is TorrentException.MetadataTimeout -> NativeTorrentFailure("native_metadata_timeout")
    is TorrentException.CachePreparationTimeout -> NativeTorrentFailure("native_cache_preparation_timeout")
    is TorrentException.InitializationTimeout -> NativeTorrentFailure("native_initialization_timeout")
    is TorrentException.LoopbackTimeout -> NativeTorrentFailure("native_loopback_timeout")
    is TorrentException.SessionUnavailable -> NativeTorrentFailure("native_session_unavailable")
    is TorrentException.InitializationFailed -> NativeTorrentFailure("native_initialization_failed")
    is TorrentException.LoopbackUnavailable -> NativeTorrentFailure("native_loopback_unavailable")
    is TorrentException.RetirementPending -> NativeTorrentFailure("native_retirement_pending")
    is TorrentException.PayloadLimit -> NativeTorrentFailure("native_payload_limit")
    is TorrentException.StorageUnavailable -> NativeTorrentFailure("native_storage_unavailable")
    is TorrentException.CacheUnavailable -> NativeTorrentFailure("native_cache_unavailable")
    is TorrentException.MetadataInvalid -> NativeTorrentFailure("native_metadata_invalid")
    is TorrentException.FileUnavailable -> NativeTorrentFailure("native_file_unavailable")
    is TorrentException -> NativeTorrentFailure("native_playback_failed")
    is java.net.UnknownHostException -> NativeTorrentFailure("native_dns_unavailable")
    is javax.net.ssl.SSLException -> NativeTorrentFailure("native_tls_failed")
    is java.net.ConnectException -> NativeTorrentFailure("native_connection_failed")
    is java.net.SocketTimeoutException, is java.util.concurrent.TimeoutException -> NativeTorrentFailure("native_control_timeout")
    is java.io.IOException -> NativeTorrentFailure("native_network_unavailable")
    is GatewayError, is NativeTorrentFailure, is NativeTorrentCacheUnavailable,
    is NativeTorrentCoordinatorUnavailable, is kotlinx.coroutines.CancellationException -> error
    else -> NativeTorrentCoordinatorUnavailable()
}

internal fun nativeTorrentFailureMessage(reason: String): String {
    val projection = JSONObject(normalize(
        "nativeTorrent", JSONObject().put("operation", "failure").put("reason", reason).toString(), "",
    ))
    // Only Rust-validated closed facts are logged; never attach an exception or source identity.
    runCatching { android.util.Log.w("NativePlaybackDiagnostic", projection.getString("code")) }
    return projection.getString("message")
}
