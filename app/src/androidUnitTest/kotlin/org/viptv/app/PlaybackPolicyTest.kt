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
        assertEquals(BackDisposition.ExitPlayer, BackPolicy.decide(dialogOpen = false, pinOpen = false, seekPreviewOpen = false, playerChromeOpen = true, inPlayer = true, explicitExit = true))
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
        val channels = (41..80).map { LiveChannel(it.toString(), "Channel $it") }
        assertEquals(1, GuidePolicy.pageFor((1..82).map { LiveChannel(it.toString(), "Channel $it") }, "41"))
        val state = GuideUiState(channels = channels, page = 1, channelOffset = 40, selectedChannelId = "45")
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

class ManagedPausePolicyTest {
    @Test fun `managed paused title time stays anchored while the HLS window moves`() {
        assertTrue(ManagedPausePolicy.usesAnchor("remux"))
        assertEquals(177_168, ManagedPausePolicy.displayPosition(177_168, 195_033))
        assertTrue(ManagedPausePolicy.requiresReplacementOnResume("remux", 177_168))
        assertEquals(177_168, ManagedPausePolicy.anchorAfterOpen("remux", live = false, launchPositionMillis = 177_168, playWhenReady = false))
        assertEquals(null, ManagedPausePolicy.anchorAfterOpen("remux", live = false, launchPositionMillis = 177_168, playWhenReady = true))
    }
}

class PlayerChromePolicyTest {
    @Test fun `player chrome stays visible while a track menu or seek preview owns focus`() {
        assertFalse(PlayerChromePolicy.shouldAutoHide(inPlayer = true, playing = true, menuOpen = true, seekPreviewOpen = false))
        assertFalse(PlayerChromePolicy.shouldAutoHide(inPlayer = true, playing = true, menuOpen = false, seekPreviewOpen = true))
        assertTrue(PlayerChromePolicy.shouldAutoHide(inPlayer = true, playing = true, menuOpen = false, seekPreviewOpen = false))
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

    @Test fun `a request queued behind preparation cannot mint new authority after Back`() {
        val queuedSourceRequest = 12L
        val generationAfterBack = 13L
        assertFalse(PlaybackRequestPolicy.mayPrepareAfterMutexWait(queuedSourceRequest, generationAfterBack))
        assertTrue(PlaybackRequestPolicy.mayPrepareAfterMutexWait(generationAfterBack, generationAfterBack))
    }
}

class AuthSessionPolicyTest {
    @Test fun `only a conclusive unauthorized refresh drops the saved device grant`() {
        assertTrue(AuthSessionPolicy.discardStoredGrant(401))
        assertFalse(AuthSessionPolicy.discardStoredGrant(429))
        assertFalse(AuthSessionPolicy.discardStoredGrant(500))
        assertFalse(AuthSessionPolicy.discardStoredGrant(null))
    }

    @Test fun `device poll returns to the issued interval and backs off only on rate limit`() {
        assertEquals(5, DevicePollPolicy.nextIntervalSeconds(5, 20, rateLimited = false))
        assertEquals(10, DevicePollPolicy.nextIntervalSeconds(5, 5, rateLimited = true))
        assertEquals(30, DevicePollPolicy.nextIntervalSeconds(5, 30, rateLimited = true))
    }
}

class DiscoverPolicyTest {
    private val alphaMovies = DiscoverCatalog(
        key = CatalogKey("alpha", "movie", "popular"),
        name = "Popular",
        supportsSearch = true,
        supportsSkip = true,
        filters = listOf(
            CatalogFilter("genre", CatalogFilterKind.Genre, required = true, options = listOf("Drama", "Comedy")),
            CatalogFilter("quality", CatalogFilterKind.Choice, required = false, options = listOf("HD")),
        ),
    )

    @Test fun `catalog identity stays source qualified when ids collide`() {
        val betaMovies = alphaMovies.copy(key = CatalogKey("beta", "movie", "popular"))
        assertEquals(alphaMovies, DiscoverPolicy.firstCatalog(listOf(alphaMovies, betaMovies), "movie"))
        assertFalse(alphaMovies.key == betaMovies.key)
    }

    @Test fun `required defaults and declared filters become the exact discover request`() {
        assertEquals(mapOf("genre" to "Drama"), DiscoverPolicy.defaults(alphaMovies))
        val request = DiscoverPolicy.request(alphaMovies, mapOf("genre" to "Comedy", "search" to "planet", "quality" to "HD"), 40)
        assertEquals("planet", request.search)
        assertEquals("Comedy", request.genre)
        assertEquals(mapOf("quality" to "HD"), request.extras)
        assertEquals(40, request.skip)
    }
}

class DetailReturnPolicyTest {
    @Test fun `discover details return to the retained discover surface`() {
        assertEquals(Destination.Discover, DetailReturnPolicy.destination(Destination.Discover))
        assertEquals(Destination.Home, DetailReturnPolicy.destination(null))
    }
}

class BackAvailabilityPolicyTest {
    @Test fun `sources and player own Back while Home leaves the Android root alone`() {
        assertTrue(BackAvailabilityPolicy.consumes(AppState(route = Route.Sources(Media("movie", "movie")))))
        assertTrue(BackAvailabilityPolicy.consumes(AppState(route = Route.Player(Media("movie", "movie"), Source("stream", "Provider")))))
        assertFalse(BackAvailabilityPolicy.consumes(AppState(route = Route.Browse(Destination.Home))))
    }
}

class PlaybackRecoveryPolicyTest {
    @Test fun `recovery preserves the current title coordinate for retry source choice and Back`() {
        val playing = Media("movie", "movie", positionMillis = 10_000, durationMillis = 120_000)
        val snapshot = PlaybackRecoveryPolicy.snapshot(playing, positionMillis = 78_500, durationMillis = 120_000)
        assertEquals(78_500, snapshot.positionMillis)
        assertEquals(120_000, snapshot.durationMillis)
        assertEquals(Route.Sources(snapshot), PlaybackRecoveryPolicy.returnRoute(PlaybackReturn.Sources, snapshot))
        assertEquals(Route.Details(snapshot), PlaybackRecoveryPolicy.returnRoute(PlaybackReturn.Details, snapshot))
    }
}

class LiveEntryPolicyTest {
    @Test fun `Live rail restores the selected guide channel or falls back to first`() {
        val channels = listOf(LiveChannel("one", "One"), LiveChannel("two", "Two"))
        assertEquals(channels[1], LiveEntryPolicy.initialChannel(channels, "two"))
        assertEquals(channels[0], LiveEntryPolicy.initialChannel(channels, "missing"))
        assertEquals(null, LiveEntryPolicy.initialChannel(emptyList(), "two"))
    }
}
