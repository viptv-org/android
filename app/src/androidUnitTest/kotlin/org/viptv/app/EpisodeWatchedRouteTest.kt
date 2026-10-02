package org.viptv.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class EpisodeWatchedRouteTest {
    @Test fun correctionUpdatesOnlyTheMatchingEpisodeInTheOpenSeries() {
        val selected = Media("show:1:1059", "episode", seriesId = "show", season = 1, episode = 1059,
            positionMillis = 30_000)
        val another = Media("show:1:1060", "episode", seriesId = "show", season = 1, episode = 1060)
        val route = Route.Details(Media("show", "series", episodes = listOf(selected, another)))

        val completed = correctedDetailsRoute(route, selected, true) as Route.Details
        assertTrue(completed.media.episodes[0].watched)
        assertFalse(completed.media.episodes[1].watched)
        assertFalse((correctedDetailsRoute(completed, selected, false) as Route.Details).media.episodes[0].watched)
        assertTrue(correctedDetailsRoute(route, selected.copy(seriesId = "other"), true) === route)
        assertTrue(correctedDetailsRoute(Route.Browse(Destination.Home), selected, true) is Route.Browse)
    }

    @Test fun refreshedPlaybackReturnWaitsForSaveAndKeepsTheCurrentEpisodeCursor() = runBlocking {
        val episode = Media("show:1:1059", "episode", seriesId = "show", season = 1, episode = 1059)
        val details = Route.Details(Media("show", "series", season = 1, episode = 1059,
            episodes = listOf(episode)))
        val returned = Route.Sources(episode, backRoute = details)
        var saved = false
        val updated = refreshEpisodeReturn(returned, episode,
            save = { saved = true }, readProgress = {
                assertTrue("Progress read must follow the final save", saved)
                listOf(episode.copy(watched = true))
            }) as Route.Sources
        val refreshed = updated.backRoute as Route.Details
        assertTrue(refreshed.media.episodes.single().watched)
        assertTrue(refreshed.media.episode == 1059)
    }

    @Test fun refreshIgnoresUnrelatedSeriesAndLeavesUnknownCompletionUnchanged() = runBlocking {
        val episode = Media("show:1:1059", "episode", seriesId = "show", season = 1, episode = 1059)
        val details = Route.Details(Media("other", "series", episodes = listOf(episode)))
        val untouched = refreshEpisodeReturn(details, episode, save = {}, readProgress = { listOf(episode.copy(watched = true)) })
        assertTrue(untouched === details)
        val matching = Route.Details(Media("show", "series", episodes = listOf(episode)))
        val unknown = refreshEpisodeReturn(matching, episode, save = {}, readProgress = { emptyList() }) as Route.Details
        assertFalse(unknown.media.episodes.single().watched)
    }

    @Test fun seriesShapedEpisodeCursorAlsoRefreshesParentDetails() = runBlocking {
        val cursor = Media("show", "series", season = 1, episode = 1059)
        val episode = Media("show:1:1059", "episode", seriesId = "show", season = 1, episode = 1059)
        val details = Route.Details(cursor.copy(episodes = listOf(episode)))
        val updated = refreshEpisodeReturn(details, cursor, save = {},
            readProgress = { listOf(episode.copy(watched = true)) }) as Route.Details
        assertTrue(updated.media.episodes.single().watched)
    }

    @Test fun pendingSourcesRefreshUpdatesDetailsAfterQuickBackButRejectsProfileOrRouteChange() = runBlocking {
        val episode = Media("show:1:1059", "episode", seriesId = "show", season = 1, episode = 1059)
        val details = Route.Details(Media("show", "series", episodes = listOf(episode)))
        val original = Route.Sources(episode, backRoute = details)
        val refreshed = refreshEpisodeReturn(original, episode, save = {},
            readProgress = { listOf(episode.copy(watched = true)) })
        val quickBack = applyRefreshedEpisodeReturn(original, refreshed, details, true, true) as Route.Details
        assertTrue(quickBack.media.episodes.single().watched)
        assertTrue(applyRefreshedEpisodeReturn(original, refreshed, details, false, true) === details)
        assertTrue((applyRefreshedEpisodeReturn(original, refreshed, details, true, false) as Route.Details).media.episodes.single().watched)
        assertTrue(applyRefreshedEpisodeReturn(original, refreshed, original, true, false) === original)
        val otherRoute = Route.Details(details.media.copy(name = "Another view"))
        assertTrue(applyRefreshedEpisodeReturn(original, refreshed, otherRoute, true, true) === otherRoute)
    }
}
