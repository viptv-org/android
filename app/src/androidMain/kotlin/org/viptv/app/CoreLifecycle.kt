package org.viptv.app

import org.json.JSONObject
import org.viptv.core.wire.CoreJson
import org.viptv.core.wire.CountdownAction
import org.viptv.core.wire.CountdownDecision
import org.viptv.core.wire.CountdownInput
import org.viptv.core.wire.ForegroundAuthorityDecision
import org.viptv.core.wire.ForegroundAuthorityInput
import org.viptv.core.wire.HomeRevisionDecision
import org.viptv.core.wire.HomeRevisionInput
import org.viptv.core.wire.Identity
import org.viptv.core.wire.PreviewAction
import org.viptv.core.wire.PreviewDecision
import org.viptv.core.wire.PreviewInput
import org.viptv.core.wire.PreviewRoute
import org.viptv.core.wire.PreviewScopeDecision
import org.viptv.core.wire.PreviewScopeInput
import org.viptv.core.wire.UpNextGateDecision
import org.viptv.core.wire.UpNextGateInput
import org.viptv.core.wire.UpNextPlaybackDecision
import org.viptv.core.wire.UpNextPlaybackInput
import uniffi.viptv_core.normalize

/** Native ownership/clock facts in; generated portable decisions out. No effects run here. */
internal object CoreLifecycle {
    private inline fun <reified I, reified O> decide(operation: String, input: I): O =
        CoreJson.decode(normalize("shellLifecycle", JSONObject(CoreJson.encode(input)).put("operation", operation).toString(), ""))

    fun authority(expected: Identity, current: Identity, profileId: String?): ForegroundAuthorityDecision =
        decide("foregroundAuthority", ForegroundAuthorityInput(expected, current, profileId))

    fun homeRevision(scopeValid: Boolean, observed: String?, rendered: String?, refreshed: Boolean? = null): HomeRevisionDecision =
        decide("homeRevision", HomeRevisionInput(scopeValid, observed, rendered, refreshed))

    fun previewScope(profileId: String?, media: Media?, activeKey: String? = null, route: PreviewRoute = PreviewRoute.OTHER, releasing: Boolean = false): PreviewScopeDecision =
        decide("previewScope", PreviewScopeInput(profileId, media?.type.orEmpty(), media?.id.orEmpty(), media?.episode != null, activeKey, route, releasing))

    fun preview(action: PreviewAction, requestedKey: String, snapshot: SourcePreviewSnapshot?, running: Boolean = false, ownerMatches: Boolean = true, elapsedMillis: Long? = null, reuseBudgetMillis: Long? = null): PreviewDecision =
        decide("preview", PreviewInput(action, requestedKey, snapshot?.key, running, snapshot?.sources?.isNotEmpty() == true,
            snapshot?.done == true, snapshot?.error != null, ownerMatches, elapsedMillis, reuseBudgetMillis))

    fun upNextPlayback(media: Media, previous: Media?, attemptedKey: String?, resumeAwaitingKey: String?, explicitResume: Boolean): UpNextPlaybackDecision =
        decide("upNextPlayback", UpNextPlaybackInput("${media.type}.${media.id}", previous?.let { "${it.type}.${it.id}" },
            attemptedKey, resumeAwaitingKey, explicitResume, media.positionMillis, media.durationMillis))

    fun upNextGate(mediaKey: String, attemptedKey: String?, resumeAwaitingKey: String?, ended: Boolean, eligible: Boolean, busy: Boolean, blocked: Boolean): UpNextGateDecision =
        decide("upNextGate", UpNextGateInput(mediaKey, attemptedKey, resumeAwaitingKey, ended, eligible, busy, blocked))

    fun countdown(action: CountdownAction, remainingMillis: Long = 0, active: Boolean = false, elapsedMillis: Long = 0, progressing: Boolean = false, scopeMatches: Boolean = true): CountdownDecision =
        decide("countdown", CountdownInput(action, remainingMillis, active, elapsedMillis, progressing, scopeMatches))
}
