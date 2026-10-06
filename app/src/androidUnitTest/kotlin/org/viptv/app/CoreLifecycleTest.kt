package org.viptv.app

import org.viptv.core.wire.Account
import org.viptv.core.wire.CountdownAction
import org.viptv.core.wire.ForegroundAuthorityDecision
import org.viptv.core.wire.HomeRevisionDecision
import org.viptv.core.wire.Identity
import org.viptv.core.wire.PreviewAction
import org.viptv.core.wire.PreviewDecision
import org.viptv.core.wire.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Calls the actual native normalizer through generated wire DTOs. */
class CoreLifecycleTest {
    private val media = Media("e1", "series", "Episode", episode = 1, positionMillis = 50_000, durationMillis = 60_000)
    private fun identity() = Identity(Account("a", "fixture", "Fixture", "member"),
        listOf(Profile(id = "p", name = "P", kid = false, setupComplete = false)), "p", false, false)

    @Test fun authorityPreservesAbsentFalseFlagsButRejectsChangedAuthority() {
        val prior = identity()
        val absent = prior.copy(profiles = prior.profiles.map { it.copy(kid = null, setupComplete = null) })
        assertEquals(ForegroundAuthorityDecision.VALID, CoreLifecycle.authority(prior, absent, "p"))
        assertEquals(ForegroundAuthorityDecision.PROFILEUNAVAILABLE,
            CoreLifecycle.authority(prior, absent.copy(profiles = absent.profiles.map { it.copy(kid = true) }), "p"))
        assertEquals(ForegroundAuthorityDecision.PROFILEUNAVAILABLE,
            CoreLifecycle.authority(prior, prior.copy(profileId = "other"), "p"))
        assertEquals(ForegroundAuthorityDecision.REVOKED,
            CoreLifecycle.authority(prior, prior.copy(account = prior.account.copy(id = "replacement")), "p"))
    }

    @Test fun postLoadRevisionAndFailedRefreshRemainUnacknowledged() {
        assertEquals(HomeRevisionDecision.REFRESH, CoreLifecycle.homeRevision(true, "r2", "r1"))
        assertEquals(HomeRevisionDecision.RETRYLATER, CoreLifecycle.homeRevision(true, "r2", "r1", false))
        assertEquals(HomeRevisionDecision.REFRESH, CoreLifecycle.homeRevision(true, "r2", "r1"))
        assertEquals(HomeRevisionDecision.REFRESHED, CoreLifecycle.homeRevision(true, "r2", "r2", true))
        assertEquals(HomeRevisionDecision.REFRESH, CoreLifecycle.homeRevision(true, "r3", "r2"))
        assertEquals(HomeRevisionDecision.SCOPELOST, CoreLifecycle.homeRevision(false, "r3", "r2", true))
    }

    @Test fun previewDecisionsRetainPartialResultsRetryEmptyAndRejectStaleOwner() {
        val key = requireNotNull(SourcePreviewPolicy.key("p", media))
        val pending = SourcePreviewSnapshot(key)
        assertEquals(PreviewDecision.BEGINSETTLED, CoreLifecycle.preview(PreviewAction.START, key, null))
        assertEquals(PreviewDecision.RETAIN, CoreLifecycle.preview(PreviewAction.START, key, pending))
        assertEquals(PreviewDecision.RETAIN, CoreLifecycle.preview(PreviewAction.ADOPT, key, pending, running = true))
        assertEquals(PreviewDecision.BEGINIMMEDIATE, CoreLifecycle.preview(PreviewAction.ADOPT, key, pending.copy(done = true)))
        assertEquals(PreviewDecision.CANCELLED, CoreLifecycle.preview(PreviewAction.RESULT, key, pending))
        val failed = pending.copy(done = true, error = IllegalStateException("Sources unavailable"))
        assertEquals(PreviewDecision.FAILED, CoreLifecycle.preview(PreviewAction.RESULT, key, failed))
        val partial = failed.copy(sources = listOf(Source("s", "Fixture")))
        assertEquals(PreviewDecision.RETAIN, CoreLifecycle.preview(PreviewAction.ADOPT, key, partial))
        assertEquals(PreviewDecision.READY, CoreLifecycle.preview(PreviewAction.RESULT, key, partial))
        assertEquals(PreviewDecision.REJECT, CoreLifecycle.preview(PreviewAction.UPDATE, key, partial, ownerMatches = false))
    }

    @Test fun outgoingDetailsFrameRemainsOwnedUntilTargetEffectOrDisposal() {
        val key = requireNotNull(SourcePreviewPolicy.key("p", media))
        val outgoing = Route.Details(media.copy(id = "replacement"))
        assertTrue(SourcePreviewPolicy.keep(key, "p", outgoing))
        assertFalse(SourcePreviewPolicy.keep(key, "p", outgoing, releasing = true))
        assertTrue(SourcePreviewPolicy.keep(key, "p", Route.Sources(media), releasing = true))
        assertFalse(SourcePreviewPolicy.keep(key, "other", Route.Details(media)))
        assertFalse(SourcePreviewPolicy.keep(key, "p", Route.Sources(media.copy(id = "other"))))
        assertNull(SourcePreviewPolicy.key("p", media.copy(episode = null)))
        assertNull(SourcePreviewPolicy.key("p", media.copy(type = "live")))
    }

    @Test fun countdownPauseReplacementAndCancelUseNativeState() {
        val clock = NextEpisodeCountdown()
        assertFalse(clock.advance(2_250, true))
        assertEquals(7_750L, clock.remainingMillis)
        assertEquals(8L, clock.snapshot.seconds)
        assertFalse(clock.advance(30_000, false))
        assertEquals(7_750L, clock.remainingMillis)
        assertFalse(clock.advance(30_000, true, scopeMatches = false))
        assertEquals(7_750L, clock.remainingMillis)
        clock.cancel()
        assertFalse(clock.advance(30_000, true))
        val replacement = NextEpisodeCountdown()
        assertEquals(10_000L, replacement.remainingMillis)
        assertTrue(replacement.advance(10_000, true))
        val prompt = UpNextPrompt(media, replacement.snapshot)
        assertEquals(0L, prompt.seconds)
        assertEquals(0L, prompt.remainingMillis)
        assertFalse(CoreLifecycle.countdown(CountdownAction.CANCEL, active = true).done)
    }

    @Test fun exactResumeSuppressionEndsAtCompletionAndCancelRetainsAttempt() {
        val armed = CoreLifecycle.upNextPlayback(media, null, "series.other", null, explicitResume = true)
        assertNull(armed.attemptedKey)
        assertEquals("series.e1", armed.resumeAwaitingKey)
        assertNull(CoreLifecycle.upNextPlayback(media.copy(positionMillis = 49_999), null, null, null, true).resumeAwaitingKey)
        val suppressed = CoreLifecycle.upNextGate("series.e1", null, armed.resumeAwaitingKey, ended = false, eligible = true, busy = false, blocked = false)
        assertFalse(suppressed.start)
        val completed = CoreLifecycle.upNextGate("series.e1", null, suppressed.resumeAwaitingKey, ended = true, eligible = true, busy = false, blocked = false)
        assertTrue(completed.start)
        assertNull(completed.resumeAwaitingKey)
        val cancelled = CoreLifecycle.upNextGate("series.e1", completed.attemptedKey, null, ended = true, eligible = true, busy = false, blocked = false)
        assertFalse(cancelled.start)
        val replacement = CoreLifecycle.upNextPlayback(media.copy(id = "e2"), media, completed.attemptedKey, null, false)
        assertNull(replacement.attemptedKey)
    }
}
