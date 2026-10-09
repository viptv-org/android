package org.viptv.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame

class NativeTorrentFailureTest {
    @Test fun runtimeObservationsPreserveClosedMeasuredReasons() {
        for ((exception, reason) in listOf(
            NativeTorrentFailure("native_acquisition_timeout") to "native_acquisition_timeout",
            NativeTorrentFailure("native_session_timeout") to "native_session_timeout",
            NativeTorrentFailure("native_metadata_timeout") to "native_metadata_timeout",
            NativeTorrentFailure("native_cache_preparation_timeout") to "native_cache_preparation_timeout",
            NativeTorrentFailure("native_initialization_timeout") to "native_initialization_timeout",
            NativeTorrentFailure("native_loopback_timeout") to "native_loopback_timeout",
            NativeTorrentFailure("native_session_unavailable") to "native_session_unavailable",
            NativeTorrentFailure("native_initialization_failed") to "native_initialization_failed",
            NativeTorrentFailure("native_loopback_unavailable") to "native_loopback_unavailable",
            NativeTorrentFailure("native_retirement_pending") to "native_retirement_pending",
            NativeTorrentFailure("native_payload_limit") to "native_payload_limit",
            NativeTorrentFailure("native_storage_unavailable") to "native_storage_unavailable",
            NativeTorrentFailure("native_cache_unavailable") to "native_cache_unavailable",
            NativeTorrentFailure("native_metadata_invalid") to "native_metadata_invalid",
            NativeTorrentFailure("native_file_unavailable") to "native_file_unavailable",
            NativeTorrentFailure("native_playback_failed") to "native_playback_failed",
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
