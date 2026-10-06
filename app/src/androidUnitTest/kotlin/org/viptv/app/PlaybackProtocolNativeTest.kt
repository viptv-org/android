package org.viptv.app

import org.json.JSONObject
import org.viptv.core.wire.CoreJson
import org.viptv.core.wire.PlaybackProtocol
import uniffi.viptv_core.CoreException
import uniffi.viptv_core.normalize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercises the real host Rust bridge without advertising a native capability. */
class PlaybackProtocolNativeTest {
    private val origin = "https://backend.example"

    @Test fun `protocol response reaches Rust as original text`() {
        for (versions in listOf(emptyList<Long>(), listOf(1L))) {
            val result = CoreJson.decode<PlaybackProtocol>(normalize("playbackProtocolV2",
                """{"version":1,"native_torrent_versions":$versions}""", origin))
            assertEquals(1L, result.version)
            assertEquals(versions, result.nativeTorrentVersions)
        }
        for (invalid in listOf(
            """{"version":1,"version":1,"native_torrent_versions":[1]}""",
            """{"version":1,"\u0076ersion":1,"native_torrent_versions":[1]}""",
            """{"version":1.0,"native_torrent_versions":[1]}""",
            """{"version":1,"native_torrent_versions":[1e0]}""",
            """{"version":1,"native_torrent_versions":[1],"private":"sentinel"}""",
            " ".repeat(4097),
        )) {
            val failure = assertFailsWith<CoreException> {
                normalize("playbackProtocolV2", invalid, origin)
            }
            assertFalse(failure.toString().contains("sentinel"))
        }
    }

    @Test fun `negotiation and request cancellation are bodyless backend requests`() {
        val negotiation = JSONObject(normalize("request", """{"operation":"playbackProtocolV2"}""", origin))
        assertEquals("GET", negotiation.getString("method"))
        assertEquals("/api/v2/playback-protocol", negotiation.getString("path"))
        assertTrue(negotiation.isNull("body"))
        val cancellation = JSONObject(normalize("request",
            """{"operation":"playbackV2CancelRequest","requestId":"request_1-A"}""", origin))
        assertEquals("DELETE", cancellation.getString("method"))
        assertEquals("/api/v2/playback-requests/request_1-A", cancellation.getString("path"))
        assertTrue(cancellation.isNull("body"))
        assertFailsWith<CoreException> {
            normalize("request", """{"operation":"playbackV2CancelRequest","requestId":"../another"}""", origin)
        }
    }

    @Test fun `ordinary Android starts keep the legacy client shape`() {
        val intent = """{"requestId":"request_1","platform":"android_tv","preferences":{},"playback":{"streamId":"source","capabilities":{"maxWidth":3840,"maxHeight":2160,"h264":true,"aac":true,"directUrls":true}}}"""
        val playback = JSONObject(normalize("playbackV2Intent", intent, origin))
        val request = JSONObject(normalize("request", JSONObject()
            .put("operation", "playbackV2").put("playback", playback).toString(), origin))
        val client = request.getJSONObject("body").getJSONObject("client")
        assertFalse(client.has("native_torrent"))
        assertFalse(client.has("nativeTorrent"))
        assertEquals("android_tv", client.getString("platform"))
    }
}
