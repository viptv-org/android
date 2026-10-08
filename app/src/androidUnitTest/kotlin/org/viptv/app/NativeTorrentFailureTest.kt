package org.viptv.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import uniffi.playback_gateway_ffi.TorrentException

class NativeTorrentFailureTest {
    @Test fun nativeExceptionsPreserveClosedMeasuredReasons() {
        for ((exception, reason) in listOf(
            TorrentException.StartupTimeout() to "native_acquisition_timeout",
            TorrentException.SessionTimeout() to "native_session_timeout",
            TorrentException.MetadataTimeout() to "native_metadata_timeout",
            TorrentException.CachePreparationTimeout() to "native_cache_preparation_timeout",
            TorrentException.InitializationTimeout() to "native_initialization_timeout",
            TorrentException.LoopbackTimeout() to "native_loopback_timeout",
            TorrentException.SessionUnavailable() to "native_session_unavailable",
            TorrentException.InitializationFailed() to "native_initialization_failed",
            TorrentException.LoopbackUnavailable() to "native_loopback_unavailable",
            TorrentException.RetirementPending() to "native_retirement_pending",
            TorrentException.PayloadLimit() to "native_payload_limit",
            TorrentException.StorageUnavailable() to "native_storage_unavailable",
            TorrentException.CacheUnavailable() to "native_cache_unavailable",
            TorrentException.MetadataInvalid() to "native_metadata_invalid",
            TorrentException.FileUnavailable() to "native_file_unavailable",
            TorrentException.OperationFailed() to "native_playback_failed",
        )) {
            val failure = assertIs<NativeTorrentFailure>(nativeTorrentFailure(exception))
            assertEquals(reason, failure.reason)
            assertEquals(nativeTorrentFailureMessage(reason), playbackFailureMessage(failure))
            assertFalse(playbackFailureMessage(failure).contains("TorrentException"))
            assertEquals("Diagnostic: $reason", playbackFailureMessage(failure).lineSequence().last())
        }
        val authorization = GatewayError(410, "Expired", "playback_expired")
        assertSame(authorization, nativeTorrentFailure(authorization))
        val unknown = nativeTorrentFailure(IllegalStateException("private-input-diagnostic"))
        assertIs<NativeTorrentCoordinatorUnavailable>(unknown)
        assertFalse(unknown.toString().contains("private-input-diagnostic"))
    }

    @Test fun typedControlFailuresSurviveWithoutPrivateExceptionText() {
        val secret = "https://private.invalid/?token=do-not-display"
        for ((exception, reason) in listOf(
            java.net.UnknownHostException(secret) to "native_dns_unavailable",
            javax.net.ssl.SSLHandshakeException(secret) to "native_tls_failed",
            java.net.ConnectException(secret) to "native_connection_failed",
            java.net.SocketTimeoutException(secret) to "native_control_timeout",
            java.io.IOException(secret) to "native_network_unavailable",
        )) {
            val failure = assertIs<NativeTorrentFailure>(nativeTorrentFailure(exception))
            assertEquals(reason, failure.reason)
            assertFalse(playbackFailureMessage(failure).contains(secret))
            assertEquals("Diagnostic: $reason", playbackFailureMessage(failure).lineSequence().last())
        }
    }
}
