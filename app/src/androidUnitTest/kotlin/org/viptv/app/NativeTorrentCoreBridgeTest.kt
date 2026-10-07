package org.viptv.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONObject
import org.viptv.core.wire.NativeTorrentNegotiationFacts
import org.viptv.core.wire.PlaybackPlatform
import uniffi.viptv_core.NativeTorrentBridge

/** Actual generated UniFFI calls using the same synthetic corpus as native Rust/WASM. */
class NativeTorrentCoreBridgeTest {
    private fun corpus() = JSONObject(File(requireNotNull(System.getProperty("viptv.core.nativeVectors"))).readText())

    @Test fun generatedPrivateHolderIsRedactedAndRetiredBytesCannotReopenIt() {
        val vectors = corpus()
        NativeTorrentBridge(vectors.getJSONObject("context").toString()).use { bridge ->
            val ready = vectors.getJSONArray("cases").getJSONObject(0)
                .getJSONArray("steps").getJSONObject(0).getString("body")
            val observation = vectors.getJSONObject("observation").toString()
            val state = bridge.acceptBytes(200u, ready.toByteArray(Charsets.UTF_8), observation)
            val clock = vectors.getJSONObject("clock").toString()
            assertEquals("ready", JSONObject(state).getString("status"))
            assertEquals(vectors.getString("infoHash"), bridge.privateInfoHash(clock))
            assertEquals("NativeTorrentBridge(<redacted>)", bridge.toString())
            assertFalse(state.contains(vectors.getString("infoHash")))
            assertFalse(state.contains(vectors.getString("inputValue")))
            assertFails { bridge.acceptBytes(200u, byteArrayOf(0xff.toByte()), observation) }
            assertFails { bridge.privateInfoHash(clock) }
            assertFails { bridge.acceptBytes(200u, ready.toByteArray(Charsets.UTF_8), observation) }
        }
    }

    @Test fun generatedScalarMetadataChecksExactFileWithoutPrivateSerializableDto() {
        val vectors = corpus()
        NativeTorrentBridge(vectors.getJSONObject("context").toString()).use { bridge ->
            val ready = vectors.getJSONArray("cases").getJSONObject(0)
                .getJSONArray("steps").getJSONObject(0).getString("body")
            bridge.acceptBytes(200u, ready.toByteArray(Charsets.UTF_8), vectors.getJSONObject("observation").toString())
            val clock = vectors.getJSONObject("clock").toString()
            val hash = vectors.getString("infoHash")
            val index = vectors.getLong("fileIndex").toUInt()
            val size = vectors.getLong("expectedFileSize").toULong()
            assertTrue(bridge.metadataMatchesNative(hash, index, 4u, size, true, clock))
            assertFalse(bridge.metadataMatchesNative(hash, index - 1u, 4u, size, true, clock))
            assertFails { bridge.privateInputValue(clock) }
        }
        val facts = NativeTorrentNegotiationFacts(
            platform = PlaybackPlatform.ANDROID_TV,
            qualified = false,
            scopeMatches = true,
            status = 200,
            authorizationRefused = false,
            body = vectors.getString("inputValue"),
        )
        assertEquals("NativeTorrentNegotiationFacts(<redacted>)", facts.toString())
    }
}
