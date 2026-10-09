package org.viptv.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TitleSourcePreviewTest {
    private val movie = Media("m", "movie", "Movie")
    private val row = Source("s", "Provider", "1080p h264 English audio", quality = "1080p")

    @Test fun expiredHandlesAreReplacedBeforePickerReuse() = runTest {
        var calls = 0
        val preview = TitleSourcePreview(backgroundScope,
            { _, _, _ -> calls++; listOf(row.copy(id = "generation-$calls")) }, {},
            settleMillis = 0, reuseBudgetMillis = 100, elapsedRealtime = { testScheduler.currentTime })
        preview.start("p", movie); runCurrent()
        advanceTimeBy(99); runCurrent()
        assertEquals("generation-1", preview.adopt("p", movie, {}, {}).single().id)
        advanceTimeBy(1); runCurrent()
        assertEquals("generation-2", preview.adopt("p", movie, {}, {}).single().id)
        preview.cancel()
        assertEquals("generation-3", preview.adopt("p", movie, {}, {}).single().id)
    }

    @Test fun repeatedTitleRendersStartOneDiscoveryAfterSettling() = runTest {
        var calls = 0
        var published: SourcePreviewSnapshot? = null
        val preview = TitleSourcePreview(backgroundScope, { _, _, _ -> calls++; listOf(row) }, { published = it })
        repeat(10) { preview.start("p\u0000movie\u0000m", movie) }
        advanceTimeBy(399); runCurrent()
        assertEquals(0, calls)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, calls)
        assertEquals(listOf(row), published?.sources)
        assertTrue(published?.done == true)
    }

    @Test fun leavingBeforeSettleAvoidsAnyRequest() = runTest {
        var calls = 0
        val preview = TitleSourcePreview(backgroundScope, { _, _, _ -> calls++; emptyList() }, {})
        preview.start("p", movie)
        preview.cancel()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(0, calls)
        assertNull(preview.key)
    }

    @Test fun pickerAdoptsRunningRowsAndProducerFailuresWithoutDuplicatingJob() = runTest {
        var calls = 0
        val completed = CompletableDeferred<Unit>()
        val producer = SourceProducerOutcome("addon:4", errorCode = "source_format_unsupported")
        val preview = TitleSourcePreview(backgroundScope, { _, producers, sources ->
            calls++; producers(listOf(producer)); sources(listOf(row)); completed.await(); listOf(row)
        }, {}, settleMillis = 0)
        preview.start("p", movie); runCurrent()
        var seenRows = emptyList<Source>()
        var seenProducers = emptyList<SourceProducerOutcome>()
        val selected = async { preview.adopt("p", movie, { seenProducers = it }, { seenRows = it }) }
        runCurrent()
        assertEquals(listOf(row), seenRows)
        assertEquals(listOf(producer), seenProducers)
        completed.complete(Unit); runCurrent()
        assertEquals(listOf(row), selected.await())
        assertEquals(1, calls)
    }

    @Test fun completedRowsAreReusedButEmptyDiscoveryCanRetry() = runTest {
        var calls = 0
        val preview = TitleSourcePreview(backgroundScope, { _, _, _ -> calls++; if (calls == 1) emptyList() else listOf(row) }, {}, settleMillis = 0)
        preview.start("p", movie); runCurrent()
        assertEquals(listOf(row), preview.adopt("p", movie, {}, {}))
        assertEquals(listOf(row), preview.adopt("p", movie, {}, {}))
        assertEquals(2, calls)
    }

    @Test fun targetReplacementRejectsOutgoingLateCallbacks() = runTest {
        var publishRows: ((List<Source>) -> Unit)? = null
        var latest: SourcePreviewSnapshot? = null
        val preview = TitleSourcePreview(backgroundScope, { media, _, rows ->
            if (media.id == "m") { publishRows = rows; awaitCancellation() }
            else emptyList()
        }, { latest = it }, settleMillis = 0)
        preview.start("old", movie); runCurrent()
        val stale = requireNotNull(publishRows)
        preview.start("new", movie.copy(id = "other")); runCurrent()
        stale(listOf(row))
        assertEquals("new", latest?.key)
        assertTrue(latest?.sources?.isEmpty() == true)
    }

    @Test fun stalledDiscoveryIsBoundedAndPartialRowsRemainAvailable() = runTest {
        var latest: SourcePreviewSnapshot? = null
        val preview = TitleSourcePreview(backgroundScope, { _, _, rows -> rows(listOf(row)); awaitCancellation() },
            { latest = it }, settleMillis = 0, timeoutMillis = 100)
        preview.start("p", movie); runCurrent()
        advanceTimeBy(100); runCurrent()
        assertTrue(latest?.done == true)
        assertTrue(latest?.error != null)
        assertEquals(listOf(row), preview.adopt("p", movie, {}, {}))
    }

    @Test fun emptyTimedOutPickerFinishesAsRecoverableFailure() = runTest {
        val preview = TitleSourcePreview(backgroundScope, { _, _, _ -> awaitCancellation() }, {}, settleMillis = 0, timeoutMillis = 100)
        val picked = async { runCatching { preview.adopt("p", movie, {}, {}) } }
        runCurrent(); advanceTimeBy(100); runCurrent()
        assertTrue(picked.await().exceptionOrNull() is IllegalStateException)
    }

    @Test fun profileAndRouteIdentityDefineTheTitleFamily() {
        val key = requireNotNull(SourcePreviewPolicy.key("p", movie))
        assertTrue(SourcePreviewPolicy.keep(key, "p", Route.Details(movie)))
        assertTrue(SourcePreviewPolicy.keep(key, "p", Route.Sources(movie)))
        assertFalse(SourcePreviewPolicy.keep(key, "other", Route.Details(movie)))
        assertFalse(SourcePreviewPolicy.keep(key, "p", Route.Sources(movie.copy(id = "different"))))
        assertFalse(SourcePreviewPolicy.keep(key, "p", Route.Search))
        assertNull(SourcePreviewPolicy.key("p", movie.copy(type = "live")))
        assertNull(SourcePreviewPolicy.key("p", movie.copy(type = "series")))
        assertNull(SourcePreviewPolicy.key(null, movie))
    }

    @Test fun summaryRankingUsesCoreDeviceLimitsAndPreservesEqualRanks() {
        val aboveLimit = row.copy(id = "4k", name = "2160p h264 English audio", quality = "2160p")
        val equal = row.copy(id = "equal")
        val caps = PlaybackClientCapabilities(1920, 1080, true, false, false, true, true)
        assertEquals(listOf(row, equal, aboveLimit), SourceRankPolicy.order(listOf(aboveLimit, row, equal), caps, "en"))
    }
}
