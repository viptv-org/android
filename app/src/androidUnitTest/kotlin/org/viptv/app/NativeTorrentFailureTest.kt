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
        }
        val authorization = GatewayError(410, "Expired", "playback_expired")
        assertSame(authorization, nativeTorrentFailure(authorization))
        val unknown = nativeTorrentFailure(IllegalStateException("private-input-diagnostic"))
        assertIs<NativeTorrentCoordinatorUnavailable>(unknown)
        assertFalse(unknown.toString().contains("private-input-diagnostic"))
    }
}
