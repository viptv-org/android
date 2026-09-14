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

    @Test fun cardEnrichmentKeepsQueueIdentityAndExactEpisodeArtwork() {
        val queued = CoreModels.media(JSONObject("""{"id":"series:1:2","type":"episode","series_id":"series","name":"Series","season":1,"episode":2,"position":42,"duration":120,"poster":"https://images.example/poster.jpg","source_addon_id":"addon:1","source_fingerprint":"exact"}"""))
        val metadata = CoreModels.media(JSONObject("""{"id":"series","type":"series","name":"Series","position":99,"duration":300,"background":"https://images.example/landscape.jpg","logo":"https://images.example/title.png","videos":[{"id":"series:1:1","season":1,"episode":1,"title":"Wrong episode","thumbnail":"https://images.example/one.jpg"},{"id":"series:1:2","season":1,"episode":2,"title":"The return","thumbnail":"https://images.example/two.jpg"}]}"""))
        val enriched = queued.withArtworkFrom(metadata)
        val card = CoreModels.card(enriched, queue = true)
        assertEquals("series:1:2", enriched.id)
        assertEquals(42_000L, enriched.positionMillis)
        assertEquals("addon:1\u0000exact", ResumeIdentity.sourceIdentity(enriched.sourceAddonId, enriched.sourceFingerprint))
        assertEquals("https://images.example/two.jpg", card.image)
        assertEquals("episode", card.imageRole)
        assertEquals("resume", card.primaryAction)
        assertEquals(.35, card.progress)
        assertTrue(card.subtitle.contains("The return"))
        assertEquals("https://images.example/title.png", CoreModels.presentation(enriched).titleLogo)
        assertEquals("details", CoreModels.card(enriched).primaryAction)
    }

    @Test fun imageFailureObservationsReachSharedPolicyWithoutChangingResume() {
        val media = CoreModels.media(JSONObject("""{"id":"series:1:2","type":"episode","name":"Series","season":1,"episode":2,"position":42,"duration":120,"thumbnail":"https://images.example/missing.jpg","background":"https://images.example/landscape.jpg","poster":"https://images.example/poster.jpg"}"""))
        val original = CoreModels.card(media, queue = true)
        val failed = setOf("https://images.example/missing.jpg")
        val fallback = CoreModels.card(media, queue = true, failedImages = failed)
        assertEquals("https://images.example/landscape.jpg", fallback.image)
        assertEquals("landscape", fallback.imageRole)
        assertEquals(original.progress, fallback.progress)
        assertEquals(original.primaryAction, fallback.primaryAction)
        val empty = CoreModels.card(media, queue = true, failedImages = failed + "https://images.example/landscape.jpg")
        assertNull(empty.image)
        assertEquals("none", empty.imageRole)
    }

    @Test fun liveCardUsesCoreLogoRoleAndDirectActivationWithoutProgress() {
        val live = CoreModels.media(JSONObject("""{"id":"station","type":"live","name":"World News","poster":"https://images.example/station.png","position":42,"duration":120}"""))
        val card = CoreModels.card(live)
        assertEquals("https://images.example/station.png", card.image)
        assertEquals("logo", card.imageRole)
        assertEquals("play", card.primaryAction)
        assertNull(card.progress)
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
