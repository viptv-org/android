package org.viptv.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackPolicyTest {
    @Test fun `resume only auto starts exact remembered source`() {
        val exact = Source("a", "Addon A", addonId = "addon", fingerprint = "release-a")
        val identity = ResumeIdentity.sourceIdentity(exact)
        assertEquals(PlaybackIntent.Open(exact, 42_000), PlaybackPolicy.forResume(identity, listOf(exact), 42_000))
        assertEquals(PlaybackIntent.ChooseSource, PlaybackPolicy.forResume(identity, listOf(Source("b", "Addon B", addonId = "addon", fingerprint = "release-b"))))
    }

    @Test fun `remembered source identity is profile scoped`() {
        val media = Media("episode-4", "series")
        assertEquals("source.alex.series.episode-4", ResumeIdentity.storageKey("alex", media))
        assertFalse(ResumeIdentity.storageKey("alex", media) == ResumeIdentity.storageKey("sam", media))
    }

    @Test fun `resume identity survives a new stream job id`() {
        val stored = ResumeIdentity.sourceIdentity(Source("expired-job", "Addon", name = "1080p", addonId = "org.example.addon", fingerprint = "release"))
        val rediscovered = ResumeIdentity.sourceIdentity(Source("new-job", "Addon", name = "1080p", addonId = "org.example.addon", fingerprint = "release"))
        assertEquals(stored, rediscovered)
    }

    @Test fun `resume rejects same-name lookalikes with a different fingerprint`() {
        val remembered = Source("expired", "Provider", name = "Premium 1080", addonId = "addon", fingerprint = "known-release")
        val lookalike = Source("new", "Provider", name = "Premium 1080", addonId = "addon", fingerprint = "different-release")
        assertEquals(PlaybackIntent.ChooseSource, PlaybackPolicy.forResume(ResumeIdentity.sourceIdentity(remembered), listOf(lookalike), 12_000))
        assertEquals(PlaybackIntent.ChooseSource, PlaybackPolicy.forResume(null, listOf(remembered), 12_000))
    }

    @Test fun `manual sources return to sources while exact resume returns detail`() {
        assertEquals(PlaybackReturn.Sources, PlaybackReturnPolicy.afterSourceStart(resume = false))
        assertEquals(PlaybackReturn.Details, PlaybackReturnPolicy.afterSourceStart(resume = true))
    }

    @Test fun `back closes transient UI before player or route navigation`() {
        assertEquals(BackDisposition.DismissDialog, BackPolicy.decide(dialogOpen = true, pinOpen = true, seekPreviewOpen = true, playerChromeOpen = true, inPlayer = true))
        assertEquals(BackDisposition.CancelPin, BackPolicy.decide(dialogOpen = false, pinOpen = true, seekPreviewOpen = true, playerChromeOpen = true, inPlayer = true))
        assertEquals(BackDisposition.CancelSeek, BackPolicy.decide(dialogOpen = false, pinOpen = false, seekPreviewOpen = true, playerChromeOpen = true, inPlayer = true))
        assertEquals(BackDisposition.HidePlayerChrome, BackPolicy.decide(dialogOpen = false, pinOpen = false, seekPreviewOpen = false, playerChromeOpen = true, inPlayer = true))
        assertEquals(BackDisposition.ExitPlayer, BackPolicy.decide(dialogOpen = false, pinOpen = false, seekPreviewOpen = false, playerChromeOpen = false, inPlayer = true))
    }

    @Test fun `seek preview clamps and ignores tiny no-op movement`() {
        assertEquals(100_000, SeekPolicy.target(95_000, 30_000, durationMillis = 100_000))
        assertEquals(0, SeekPolicy.target(15_000, -30_000, durationMillis = 100_000))
        assertEquals(null, SeekPolicy.target(10_000, 200, durationMillis = 100_000))
    }

    @Test fun `direct delivery commits natively while server-managed delivery replaces`() {
        assertFalse(SeekCommitPolicy.usesManagedReplacement("direct"))
        assertTrue(SeekCommitPolicy.usesManagedReplacement("remux"))
        assertTrue(SeekCommitPolicy.usesManagedReplacement("transcode"))
    }

    @Test fun `managed segment position maps to the title timeline`() {
        val offset = PlaybackTimelinePolicy.titleOffsetMillis("remux", 42_000)
        assertEquals(42_000, PlaybackTimelinePolicy.absolutePositionMillis(0, offset))
        assertEquals(51_500, PlaybackTimelinePolicy.absolutePositionMillis(9_500, offset))
        assertEquals(8_000, PlaybackTimelinePolicy.segmentPositionMillis(50_000, offset))
        assertEquals(0, PlaybackTimelinePolicy.titleOffsetMillis("direct", 42_000))
    }

    @Test fun `next needs active unpaused series in final ten seconds`() {
        assertTrue(PlaybackPolicy.canAutoNext(Media("e", "series"), 91_000, 100_000, true, false, true))
        assertFalse(PlaybackPolicy.canAutoNext(Media("e", "series"), 91_000, 100_000, false, false, true))
        assertFalse(PlaybackPolicy.canAutoNext(Media("e", "movie"), 91_000, 100_000, true, false, true))
        assertFalse(PlaybackPolicy.canAutoNext(Media("e", "series"), 1_000, 10_000, true, false, true))
        assertFalse(PlaybackPolicy.canAutoNext(Media("e", "series"), 91_000, 100_000, true, false, true, autoplay = false))
    }

    @Test fun `short release activates and long press suppresses activation`() {
        assertEquals(RemoteAction.Activate, HoldPolicy.release(699))
        assertEquals(RemoteAction.Hold, HoldPolicy.release(700))
    }

    @Test fun `continuation does not replace a paused or seeking outgoing episode`() {
        val episode = Media("episode-4", "series")
        assertEquals(ContinuationDecision.KeepOutgoing, ContinuationPolicy.decide(episode, 95_000, 100_000, false, false, "next"))
        assertEquals(ContinuationDecision.KeepOutgoing, ContinuationPolicy.decide(episode, 95_000, 100_000, true, true, "next"))
    }

    @Test fun `continuation only prepares a known next episode in the final ten seconds`() {
        val episode = Media("episode-4", "series")
        assertEquals(ContinuationDecision.PrepareNext, ContinuationPolicy.decide(episode, 90_000, 100_000, true, false, "next"))
        assertEquals(ContinuationDecision.KeepOutgoing, ContinuationPolicy.decide(episode, 89_999, 100_000, true, false, "next"))
        assertEquals(ContinuationDecision.ShowCaughtUp, ContinuationPolicy.decide(episode, 95_000, 100_000, true, false, "caught_up"))
    }

    @Test fun `continuation keeps the active IPTV add-on and never falls back arbitrarily`() {
        val outgoing = Source("stream-a", "IPTV", addonId = "iptv-a", fingerprint = "old")
        val next = Media("episode-5", "series", sourceAddonId = "ranked-b")
        val sameAddon = Source("stream-next", "IPTV", addonId = "iptv-a", fingerprint = "new")
        val ranked = Source("stream-ranked", "Provider", addonId = "ranked-b", fingerprint = "new")
        val other = Source("stream-other", "Provider", addonId = "other", fingerprint = "new")
        assertEquals(sameAddon, ContinuationSourcePolicy.select(next, outgoing, listOf(other, ranked, sameAddon)))
        assertEquals(ranked, ContinuationSourcePolicy.select(next, null, listOf(other, ranked)))
        assertEquals(null, ContinuationSourcePolicy.select(next, outgoing, listOf(other)))
    }
}

class GuidePolicyTest {
    @Test fun `guide pages in forties and keeps five selected-neighbor rows`() {
        val channels = (1..82).map { LiveChannel(it.toString(), "Channel $it") }
        assertEquals(1, GuidePolicy.pageFor(channels, "41"))
        val state = GuideUiState(channels = channels, page = 1, selectedChannelId = "45")
        assertEquals(listOf("41", "42", "43", "44", "45"), GuidePolicy.visibleRows(state).map(LiveChannel::id))
    }

    @Test fun `guide window never moves before now or beyond one day`() {
        val now = 1_800_123_000_000L
        val initial = GuidePolicy.nowWindow(now)
        assertEquals(initial, GuidePolicy.shiftedWindow(initial, -1, now))
        assertEquals(initial + 24 * 60 * 60 * 1_000L, GuidePolicy.shiftedWindow(initial, 25, now))
    }
}

class MediaCardPolicyTest {
    @Test fun `only continue watching resumes from a card primary action`() {
        val progressed = Media("movie", "movie", positionMillis = 10_000)
        assertEquals(MediaCardAction.ResumeExactSource, MediaCardPolicy.primary(true, progressed))
        assertEquals(MediaCardAction.OpenDetails, MediaCardPolicy.primary(false, progressed))
        assertFalse(MediaCardPolicy.supportsChooseSourceHold(false, progressed))
    }
}

class SourceDisplayPolicyTest {
    @Test fun `opaque provider worker id yields to filename metadata`() {
        val source = Source("stream", "iptv:4", name = "iptv:4", description = "Mayday.2021.1080p.mkv")
        assertEquals("Mayday.2021.1080p.mkv", SourceDisplayPolicy.title(source))
        assertEquals("Mayday.2021.1080p.mkv", SourceDisplayPolicy.body(source))
    }
}

class ManagedRecoveryPolicyTest {
    @Test fun `only one managed network recovery is permitted`() {
        assertTrue(ManagedRecoveryPolicy.shouldAttempt(serverManaged = true, networkFailure = true, alreadyAttempted = false))
        assertFalse(ManagedRecoveryPolicy.shouldAttempt(serverManaged = false, networkFailure = true, alreadyAttempted = false))
        assertFalse(ManagedRecoveryPolicy.shouldAttempt(serverManaged = true, networkFailure = false, alreadyAttempted = false))
        assertFalse(ManagedRecoveryPolicy.shouldAttempt(serverManaged = true, networkFailure = true, alreadyAttempted = true))
    }
}

class HoldPressPolicyTest {
    @Test fun `focus-lost release cannot activate a newly focused control`() {
        assertTrue(HoldPressPolicy.begins(0L, repeatCount = 0))
        assertFalse(HoldPressPolicy.activatesOnRelease(0L, held = false))
        assertFalse(HoldPressPolicy.begins(0L, repeatCount = 1))
        assertFalse(HoldPressPolicy.activatesOnRelease(100L, held = true))
        assertTrue(HoldPressPolicy.activatesOnRelease(100L, held = false))
    }
}

class PlaybackRequestPolicyTest {
    @Test fun `back invalidation makes a late preparation obsolete`() {
        assertTrue(PlaybackRequestPolicy.isCurrent(7, 7))
        assertFalse(PlaybackRequestPolicy.isCurrent(7, 8))
    }
}
