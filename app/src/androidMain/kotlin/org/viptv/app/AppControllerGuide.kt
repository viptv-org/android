package org.viptv.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun AppController.cancelGuideWork() {
    guideBrowseGeneration++; guideGeneration++
    guideBrowseJob?.cancel(); guideRowsJob?.cancel(); guidePageJob?.cancel()
    guideCategories.cancel()
    _state.value = _state.value.copy(guideUi = GuidePolicy.suspended(_state.value.guideUi))
}
internal fun AppController.setGuideFilter(filter: LiveChannelFilter) = loadGuidePage(filter, 0)
internal fun AppController.setGuideSearch(query: String) {
    val normalized = query.trim().take(128)
    setGuideFilter(if (normalized.isBlank()) LiveChannelFilter.AllUs else LiveChannelFilter.Search(normalized))
}
internal fun AppController.retryGuidePage() {
    val current = _state.value.guideUi
    loadGuidePage(current.channelFilter, 0, current.selectedChannelId)
}

/** Offset is presentation only; the backend receives the original opaque cursor. */
internal fun AppController.loadGuidePage(filter: LiveChannelFilter, offset: Int, preferredChannelId: String? = null, cursor: String? = null) {
    if (_state.value.preparingSourceId != null) invalidatePlaybackPreparation()
    cancelGuideWork()
    val ticket = guideBrowseGeneration
    val profile = _state.value.selectedProfile?.id
    val prior = _state.value.guideUi
    guideScheduleCache.clear()
    _state.value = _state.value.copy(route = Route.Guide(), liveChannels = emptyList(),
        guideUi = prior.copy(channels = emptyList(), schedulesByChannelId = emptyMap(),
            channelFilter = filter, paging = false, pagingFailed = false, loadingChannelIds = emptySet()),
        loading = true, message = null)
    guideBrowseJob = scope.launch {
        try {
            val page = gateway.livePage(LiveBrowseRequest(filter, cursor))
            if (ticket != guideBrowseGeneration || _state.value.selectedProfile?.id != profile || _state.value.route !is Route.Guide) return@launch
            guidePages.replace(page, offset)
            val initial = LiveEntryPolicy.initialChannel(page.channels, preferredChannelId ?: prior.selectedChannelId)
            val guide = _state.value.guideUi.copy(channels = page.channels, selectedChannelId = initial?.id,
                page = offset / GuidePolicy.PAGE_SIZE, channelOffset = offset, pageCursor = cursor,
                nextCursor = page.nextCursor, previousCursor = page.previousCursor,
                catalogId = page.catalogId, generation = page.generation, visibleFirst = 0, visibleEnd = 7, visibleScrollOffset = 0,
                windowStartMillis = prior.windowStartMillis.takeIf { it > 0 } ?: GuidePolicy.nowWindow(System.currentTimeMillis()), followsNow = true)
            _state.value = _state.value.copy(route = Route.Guide(initial), liveChannels = page.channels,
                guideUi = guide, guide = emptyList(), loading = false,
                message = if (initial != null) null else if (filter is LiveChannelFilter.Search) "No channels match your search." else "No channels are available for this filter.")
            requestGuideSchedules()
            // Categories never hold first content behind another network request.
            guideCategories.ensure()
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) { if (ticket == guideBrowseGeneration && _state.value.selectedProfile?.id == profile && _state.value.route is Route.Guide) fail(error) }
    }
}
internal fun AppController.openGuide(channel: LiveChannel) {
    if (_state.value.route is Route.Guide) selectGuideChannel(channel)
    else loadGuidePage(LiveChannelFilter.AllUs, 0, channel.id)
}
internal fun AppController.selectGuideChannel(channel: LiveChannel) {
    if (_state.value.route !is Route.Guide || _state.value.preparingSourceId != null) return
    val current = _state.value.guideUi
    if (current.selectedChannelId == channel.id || current.channels.none { it.id == channel.id }) return
    val guide = current.copy(selectedChannelId = channel.id)
    _state.value = _state.value.copy(route = Route.Guide(channel), guideUi = guide, guide = guide.schedulesByChannelId[channel.id].orEmpty())
}
internal fun AppController.changeGuidePage(delta: Int) {
    val current = _state.value.guideUi
    val cursor = if (delta > 0) current.nextCursor else if (delta < 0) current.previousCursor else null
    if (cursor == null || _state.value.loading || current.paging) return
    loadGuidePage(current.channelFilter, (current.channelOffset + delta.coerceIn(-1, 1) * GuidePolicy.PAGE_SIZE).coerceAtLeast(0), cursor = cursor)
}
/** UI reports its actual lazy-list viewport; controllers own paging and EPG I/O. */
internal fun AppController.onGuideViewport(first: Int, last: Int, scrollOffset: Int = 0) {
    val current = _state.value.guideUi
    if (_state.value.route !is Route.Guide || current.channels.isEmpty() || _state.value.preparingSourceId != null) return
    val start = first.coerceIn(0, current.channels.lastIndex)
    val end = (last + 1).coerceIn(start + 1, current.channels.size)
    if (current.visibleFirst != start || current.visibleEnd != end || current.visibleScrollOffset != scrollOffset) {
        _state.value = _state.value.copy(guideUi = current.copy(visibleFirst = start, visibleEnd = end,
            visibleScrollOffset = scrollOffset.coerceAtLeast(0),
            pagingFailed = current.pagingFailed && !GuidePolicy.movedSinceFailure(current, start, scrollOffset)))
        if (current.visibleFirst != start || current.visibleEnd != end) requestGuideSchedules(debounce = true)
    }
    val now = System.currentTimeMillis()
    val missing = GuidePolicy.scheduleRows(_state.value.guideUi).any { (guideScheduleCache[it.id]?.expiresAtMillis ?: 0) <= now }
    if (GuidePolicy.mayResumeSchedules(false, guideRowsJob?.isActive == true, missing)) requestGuideSchedules(debounce = true)
    if (start <= 2 && current.previousCursor != null) loadAdjacentGuidePage(true)
    else if (end >= current.channels.size - 5 && current.nextCursor != null) loadAdjacentGuidePage(false)
}
internal fun AppController.appendGuidePage() = loadAdjacentGuidePage(false)
internal fun AppController.changeGuideCategoryPage(delta: Int, renderedRevision: Long) = guideCategories.move(delta, renderedRevision)
internal fun AppController.retryGuideCategories() = guideCategories.retry()
internal fun AppController.onGuideCategoryViewport(renderedRevision: Long, firstId: String, lastId: String, offset: Int = 0, allowPaging: Boolean = true) =
    guideCategories.viewport(renderedRevision, firstId, lastId, offset, allowPaging)
internal fun AppController.onGuideCategoryRowViewport(renderedRevision: Long, key: String, index: Int, offset: Int, focusKey: String?) =
    guideCategories.rowViewport(renderedRevision, key, index, offset, focusKey)
private fun AppController.loadAdjacentGuidePage(previous: Boolean) {
    val current = _state.value.guideUi
    val cursor = if (previous) current.previousCursor else current.nextCursor
    if (cursor == null || current.paging || current.pagingFailed || _state.value.loading || _state.value.route !is Route.Guide) return
    val ticket = guideBrowseGeneration
    val profile = _state.value.selectedProfile?.id
    _state.value = _state.value.copy(guideUi = current.copy(paging = true))
    guidePageJob = scope.launch {
        try {
            val page = gateway.livePage(LiveBrowseRequest(current.channelFilter, cursor))
            if (ticket != guideBrowseGeneration || _state.value.selectedProfile?.id != profile || _state.value.route !is Route.Guide) return@launch
            guidePages.add(page, previous)
            val channels = guidePages.channels
            val latest = _state.value.guideUi
            val selected = latest.selectedChannelId.takeIf { id -> channels.any { it.id == id } } ?: channels.firstOrNull()?.id
            val firstId = latest.channels.getOrNull(latest.visibleFirst)?.id
            val first = channels.indexOfFirst { it.id == firstId }.coerceAtLeast(0)
            _state.value = _state.value.copy(liveChannels = channels, guideUi = latest.copy(channels = channels,
                channelOffset = guidePages.start, nextCursor = guidePages.next, previousCursor = guidePages.previous,
                selectedChannelId = selected, paging = false, visibleFirst = first,
                visibleEnd = (first + latest.visibleEnd - latest.visibleFirst).coerceAtMost(channels.size)))
            requestGuideSchedules()
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) {
            if (ticket == guideBrowseGeneration && _state.value.selectedProfile?.id == profile && _state.value.route is Route.Guide) {
                _state.value = _state.value.copy(guideUi = _state.value.guideUi.copy(paging = false, pagingFailed = true))
                fail(error)
            }
        }
    }
}
internal fun AppController.shiftGuideWindow(hours: Int) {
    val current = _state.value.guideUi
    if (current.windowStartMillis == 0L) return
    _state.value = _state.value.copy(guideUi = current.copy(windowStartMillis = GuidePolicy.shiftedWindow(current.windowStartMillis, hours, System.currentTimeMillis()), followsNow = false))
}
internal fun AppController.followGuideNow() {
    _state.value = _state.value.copy(guideUi = _state.value.guideUi.copy(windowStartMillis = GuidePolicy.nowWindow(System.currentTimeMillis()), followsNow = true))
}
internal fun AppController.watchGuideChannel(channel: LiveChannel) = activateCard(Media(channel.id, "live", channel.name))
private fun AppController.requestGuideSchedules(debounce: Boolean = false) {
    guideRowsJob?.cancel()
    val ticket = ++guideGeneration
    guideRowsJob = scope.launch {
        if (debounce) delay(120)
        refreshGuideRows(ticket)
    }
}
private suspend fun AppController.refreshGuideRows(ticket: Long) {
    val before = _state.value.guideUi
    val ids = GuidePolicy.scheduleRows(before).map { it.id }
    val now = System.currentTimeMillis()
    val missing = ids.filter { (guideScheduleCache[it]?.expiresAtMillis ?: 0) <= now }
    _state.value = _state.value.copy(guideUi = before.copy(loadingChannelIds = missing.toSet()))
    publishGuideCache(ticket)
    coroutineScope {
        missing.chunked(3).forEach { batch ->
            batch.map { id -> async {
                val cache = try { AppController.GuideScheduleCache(gateway.guideV2(id), System.currentTimeMillis() + 300_000) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { AppController.GuideScheduleCache(emptyList(), System.currentTimeMillis() + 60_000) }
                id to cache
            } }.awaitAll().forEach { (id, cache) ->
                if (ticket != guideGeneration || _state.value.route !is Route.Guide) return@coroutineScope
                guideScheduleCache.remove(id); guideScheduleCache[id] = cache
                while (guideScheduleCache.size > 200) guideScheduleCache.remove(guideScheduleCache.keys.first())
            }
            publishGuideCache(ticket)
        }
    }
}
private fun AppController.publishGuideCache(ticket: Long) {
    if (ticket != guideGeneration || _state.value.route !is Route.Guide) return
    val current = _state.value.guideUi
    val schedules = current.channels.mapNotNull { channel -> guideScheduleCache[channel.id]?.let { channel.id to it.entries } }.toMap()
    _state.value = _state.value.copy(guideUi = current.copy(schedulesByChannelId = schedules,
        loadingChannelIds = current.loadingChannelIds - schedules.keys), guide = schedules[current.selectedChannelId].orEmpty())
}
