package org.viptv.app

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** A Guide filter replaces the server page from zero. Search is validated before it reaches transport. */
internal fun AppController.setGuideFilter(filter: LiveChannelFilter) = loadGuidePage(filter, offset = 0)
internal fun AppController.setGuideSearch(query: String) {
    val normalized = query.trim().take(128)
    setGuideFilter(if (normalized.isBlank()) LiveChannelFilter.AllUs else LiveChannelFilter.Search(normalized))
}
internal fun AppController.retryGuidePage() {
    val current = _state.value.guideUi
    loadGuidePage(current.channelFilter, current.channelOffset, current.selectedChannelId)
}

internal fun AppController.loadGuidePage(filter: LiveChannelFilter, offset: Int, preferredChannelId: String? = null) {
    val browseGeneration = ++guideBrowseGeneration
    scope.launch {
        val prior = _state.value.guideUi
        // Enter the Guide shell before I/O. A first-load fault and an
        // empty collection retain filters and the normal Guide Back path.
        val priorChannel = (_state.value.route as? Route.Guide)?.channel
            ?: prior.channels.firstOrNull { it.id == prior.selectedChannelId }
        _state.value = _state.value.copy(route = Route.Guide(priorChannel), loading = true, message = null)
        try {
            val (page, categories) = coroutineScope {
                val pageRequest = async { gateway.livePage(LiveBrowseRequest(filter, offset, GuidePolicy.PAGE_SIZE)) }
                val categoryRequest = async { runCatching { gateway.liveCategories() } }
                pageRequest.await() to categoryRequest.await().getOrElse { prior.categories }
            }
            if (browseGeneration != guideBrowseGeneration) return@launch
            val initial = LiveEntryPolicy.initialChannel(page.channels, preferredChannelId ?: prior.selectedChannelId)
            val guide = prior.copy(
                channels = page.channels,
                selectedChannelId = initial?.id,
                page = page.request.offset / GuidePolicy.PAGE_SIZE,
                channelOffset = page.request.offset,
                channelTotal = page.total,
                channelFilter = page.request.filter,
                categories = categories,
                searchScope = page.searchScope,
                windowStartMillis = prior.windowStartMillis.takeIf { it > 0 } ?: GuidePolicy.nowWindow(System.currentTimeMillis()),
                followsNow = true,
                loadingChannelIds = emptySet(),
            )
            if (initial == null) {
                _state.value = _state.value.copy(
                    // Empty filter pages retain the canonical Guide shell.
                    // Falling back to Browse(Live) discards the active
                    // filter/search controls and turns Back into a
                    // different navigation path.
                    route = Route.Guide(),
                    liveChannels = emptyList(),
                    guideUi = guide,
                    loading = false,
                    message = when (filter) {
                        is LiveChannelFilter.Search -> "No matching US channels or current programmes. Try a channel name, section, or another title."
                        else -> "No channels are available for this filter."
                    },
                )
                return@launch
            }
            val scheduleGeneration = ++guideGeneration
            _state.value = _state.value.copy(
                route = Route.Guide(initial),
                liveChannels = page.channels,
                guideUi = guide,
                guide = guide.schedulesByChannelId[initial.id].orEmpty(),
                loading = false,
                message = null,
            )
            refreshGuideRows(scheduleGeneration)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (browseGeneration == guideBrowseGeneration) fail(error)
        }
    }
}

/** Select within the loaded server page; filter and page transitions always go through `loadGuidePage`. */
internal fun AppController.openGuide(channel: LiveChannel) = selectGuideChannel(channel)
internal fun AppController.selectGuideChannel(channel: LiveChannel) {
    val current = _state.value.guideUi
    if (current.selectedChannelId == channel.id) return
    val generation = ++guideGeneration
    val channels = if (current.channels.any { it.id == channel.id }) current.channels else listOf(channel) + current.channels
    val guide = current.copy(channels = channels, selectedChannelId = channel.id)
    _state.value = _state.value.copy(route = Route.Guide(channel), guideUi = guide, guide = guide.schedulesByChannelId[channel.id].orEmpty(), message = null)
    scope.launch { refreshGuideRows(generation) }
}

/** Server cursor pages remain 40 channels; never fabricate a page by slicing a partial result. */
internal fun AppController.changeGuidePage(delta: Int) {
    val current = _state.value.guideUi
    if (delta == 0 || current.channelTotal <= 0) return
    val maxOffset = ((current.channelTotal - 1) / GuidePolicy.PAGE_SIZE) * GuidePolicy.PAGE_SIZE
    val target = (current.channelOffset + delta * GuidePolicy.PAGE_SIZE).coerceIn(0, maxOffset)
    if (target == current.channelOffset) return
    loadGuidePage(current.channelFilter, target)
}

internal fun AppController.appendGuidePage() {
    val current = _state.value.guideUi
    val offset = current.channelOffset + current.channels.size
    if (_state.value.loading || offset >= current.channelTotal || _state.value.route !is Route.Guide) return
    val generation = guideBrowseGeneration
    _state.value = _state.value.copy(loading = true)
    scope.launch {
        try {
            val page = gateway.livePage(LiveBrowseRequest(current.channelFilter, offset))
            if (generation != guideBrowseGeneration || _state.value.route !is Route.Guide) return@launch
            val latest = _state.value.guideUi
            _state.value = _state.value.copy(loading = false, guideUi = latest.copy(
                channels = (latest.channels + page.channels).distinctBy { it.id },
                channelTotal = if (page.channels.isEmpty()) offset else page.total,
            ))
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { if (generation == guideBrowseGeneration) _state.value = _state.value.copy(loading = false, message = "Couldn't load more channels.") }
    }
}

internal fun AppController.shiftGuideWindow(hours: Int) {
    val current = _state.value.guideUi
    if (current.windowStartMillis == 0L) return
    val now = System.currentTimeMillis()
    _state.value = _state.value.copy(guideUi = current.copy(windowStartMillis = GuidePolicy.shiftedWindow(current.windowStartMillis, hours, now), followsNow = false))
}

internal fun AppController.followGuideNow() {
    val current = _state.value.guideUi
    _state.value = _state.value.copy(guideUi = current.copy(windowStartMillis = GuidePolicy.nowWindow(System.currentTimeMillis()), followsNow = true))
}

internal fun AppController.watchGuideChannel(channel: LiveChannel) = activateCard(Media(channel.id, "live", channel.name))

private suspend fun AppController.refreshGuideRows(generation: Long) {
    val before = _state.value.guideUi
    val ids = GuidePolicy.visibleAndLookAhead(before).map(LiveChannel::id).distinct()
    val now = System.currentTimeMillis()
    val valid = ids.filter { id -> guideScheduleCache[id]?.let { now < it.expiresAtMillis } == true }
    val missing = ids - valid.toSet()
    if (missing.isEmpty()) {
        publishGuideCache(generation)
        return
    }
    _state.value = _state.value.copy(guideUi = before.copy(loadingChannelIds = before.loadingChannelIds + missing))
    coroutineScope {
        missing.chunked(3).forEach { batch ->
            batch.map { id -> async { id to runCatching { gateway.guide(id) } } }.awaitAll().forEach { (id, result) ->
                val fetchedAt = System.currentTimeMillis()
                guideScheduleCache[id] = result.fold(
                    onSuccess = { AppController.GuideScheduleCache(it, fetchedAt + 300_000L) },
                    onFailure = { AppController.GuideScheduleCache(emptyList(), fetchedAt + 60_000L) },
                )
            }
        }
    }
    publishGuideCache(generation)
}

private fun AppController.publishGuideCache(generation: Long) {
    if (generation != guideGeneration || _state.value.route !is Route.Guide) return
    val current = _state.value.guideUi
    val schedules = current.schedulesByChannelId + guideScheduleCache.mapValues { it.value.entries }
    val selected = current.selectedChannelId
    _state.value = _state.value.copy(guideUi = current.copy(schedulesByChannelId = schedules, loadingChannelIds = emptySet()), guide = selected?.let { schedules[it] }.orEmpty(), loading = false)
}
