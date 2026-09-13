package org.viptv.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.json.JSONArray
import org.json.JSONObject
import org.viptv.core.wire.CoreJson
import org.viptv.core.wire.Phase
import org.viptv.core.wire.ViewModel
import uniffi.viptv_core.CoreBridge

/** Runs against the actual host Rust library, never a Kotlin replacement policy. */
class SharedCoreNativeTest {
    @Test fun generatedMediaKeepsLandscapeSeparateFromPortraitAndCurrentProgress() {
        val media = CoreModels.media(JSONObject("""{"id":"series:1","type":"series","name":"Series","poster":"https://images.example/poster.jpg","background":"https://images.example/landscape.jpg","position":30,"duration":100,"source_addon_id":"addon:1","source_fingerprint":"exact"}"""))
        assertEquals("https://images.example/landscape.jpg", CoreModels.presentation(media).heroImage)
        assertNull(CoreModels.presentation(media.copy(backdrop = null)).heroImage)
        assertEquals(.6, CoreModels.presentation(media.copy(positionMillis = 60_000)).progress)
        assertEquals("addon:1\u0000exact", ResumeIdentity.sourceIdentity(media.sourceAddonId, media.sourceFingerprint))
    }

    @Test fun nativeCruxStartupRequiresStorageBeforePairing() {
        CoreBridge().use { core ->
            val requests = JSONArray(core.update("""{"Begin":{"origin":"https://viptv.example","allowInsecurePreview":false}}"""))
            assertEquals(Phase.RESTORING, CoreJson.decode<ViewModel>(core.view()).phase)
            val load = (0 until requests.length()).map { requests.getJSONObject(it) }.first { it.getJSONObject("effect").has("Storage") }
            assertEquals("Load", load.getJSONObject("effect").getString("Storage"))
            val result = JSONArray(core.resolve(load.getLong("id").toUInt(), """{"Ok":null}"""))
            assertTrue(result.length() > 0)
            assertEquals(Phase.PAIRING, CoreJson.decode<ViewModel>(core.view()).phase)
        }
    }
}
