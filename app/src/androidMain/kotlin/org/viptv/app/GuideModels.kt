package org.viptv.app

/**
 * Server schedules are cached per channel while the Guide owns window, page and
 * selection.  It stays UI-neutral so Compose can render its five-row grid
 * without making network decisions.
 */
data class GuideUiState(
    /** Exactly the server-selected 40-channel page, never a locally-filtered full catalogue. */
    val channels: List<LiveChannel> = emptyList(),
    val schedulesByChannelId: Map<String, List<GuideProgramme>> = emptyMap(),
    val selectedChannelId: String? = null,
    val page: Int = 0,
    val channelOffset: Int = 0,
    val channelTotal: Int = 0,
    val channelFilter: LiveChannelFilter = LiveChannelFilter.AllUs,
    val categories: List<LiveCategory> = emptyList(),
    val searchScope: String? = null,
    val windowStartMillis: Long = 0,
    val followsNow: Boolean = true,
    val loadingChannelIds: Set<String> = emptySet(),
)

object LiveEntryPolicy {
    fun initialChannel(channels: List<LiveChannel>, rememberedId: String?): LiveChannel? =
        channels.firstOrNull { it.id == rememberedId } ?: channels.firstOrNull()
}

object GuidePolicy {
    const val PAGE_SIZE = 40
    const val VISIBLE_ROWS = 5
    private const val HALF_HOUR_MILLIS = 30 * 60 * 1_000L
    private const val MAX_AHEAD_MILLIS = 24 * 60 * 60 * 1_000L

    fun nowWindow(nowMillis: Long): Long = nowMillis / HALF_HOUR_MILLIS * HALF_HOUR_MILLIS
    fun pageFor(channels: List<LiveChannel>, channelId: String): Int =
        (channels.indexOfFirst { it.id == channelId }.coerceAtLeast(0) / PAGE_SIZE)
    /** `channels` is already the active server page; page is display metadata only. */
    fun visibleRows(state: GuideUiState): List<LiveChannel> {
        val selected = state.channels.indexOfFirst { it.id == state.selectedChannelId }.coerceAtLeast(0)
        return state.channels.drop((selected - (VISIBLE_ROWS - 1)).coerceAtLeast(0)).take(VISIBLE_ROWS)
    }
    fun visibleAndLookAhead(state: GuideUiState): List<LiveChannel> {
        val selected = state.channels.indexOfFirst { it.id == state.selectedChannelId }.coerceAtLeast(0)
        val start = (selected - (VISIBLE_ROWS - 1)).coerceAtLeast(0)
        return state.channels.drop(start).take(VISIBLE_ROWS + 2)
    }
    fun shiftedWindow(windowStartMillis: Long, hours: Int, nowMillis: Long): Long =
        (windowStartMillis + hours * 60 * 60 * 1_000L).coerceIn(nowWindow(nowMillis), nowWindow(nowMillis) + MAX_AHEAD_MILLIS)
}
