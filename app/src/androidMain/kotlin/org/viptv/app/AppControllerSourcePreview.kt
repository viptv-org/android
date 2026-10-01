package org.viptv.app

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * One background discovery for a title's best-source line (AND-043). The
 * chooser adopts a running or fresh discovery for the same item instead of
 * starting a second one; the poll loop, deduplication and three-minute budget
 * stay the shared Rust reducer's (`gateway.sources`).
 */
internal class SourcePreviewEntry(val key: String) {
    @Volatile var sources: List<Source> = emptyList()
    @Volatile var done = false
    @Volatile var error: Throwable? = null
    var finishedAt = 0L
    var job: Job? = null
    val version = MutableStateFlow(0L)
    fun fresh(now: Long = SystemClock.elapsedRealtime()) =
        done && error == null && sources.isNotEmpty() && now - finishedAt < SourcePreviewPolicy.FRESH_MILLIS
}

internal object SourcePreviewPolicy {
    /** A discovery answered within the last two minutes is still the item's list. */
    const val FRESH_MILLIS = 120_000L
    /** A quick pass through a title (Back, a season flick) settles before discovery starts. */
    const val SETTLE_MILLIS = 400L
    private const val CACHE_LIMIT = 16

    /** Live channels play directly and a series without an episode has no single source list. */
    fun key(profileId: String?, media: Media): String? =
        if (profileId == null || media.type == "live" || (media.type == "series" && media.episode == null)) null
        else "$profileId\u0000${media.type}\u0000${media.id}"

    /** The preview survives only while its title, or its own chooser/player, is on screen. */
    fun keep(entryKey: String, profileId: String?, route: Route): Boolean = when (route) {
        is Route.Details -> true
        is Route.Sources -> key(profileId, route.media) == entryKey
        is Route.Player -> key(profileId, route.media) == entryKey
        else -> false
    }

    fun <V> trim(cache: LinkedHashMap<String, V>) {
        while (cache.size > CACHE_LIMIT) cache.remove(cache.keys.first())
    }
}

internal fun AppController.sourcePreviewKey(media: Media): String? =
    SourcePreviewPolicy.key(_state.value.selectedProfile?.id, media)

/** Starts (or continues) the background discovery behind the title's best-source line. */
internal fun AppController.previewSources(media: Media) {
    val key = sourcePreviewKey(media) ?: return
    val active = sourcePreview
    if (active?.key == key && (active.job?.isActive == true || active.fresh())) { publishSourceSummary(active); return }
    stopSourcePreview()
    val cached = sourcePreviewCache[key]?.takeIf { it.fresh() }
    if (cached != null) { sourcePreview = cached; publishSourceSummary(cached); return }
    ensureRankCapabilities()
    val entry = SourcePreviewEntry(key)
    sourcePreview = entry
    publishSourceSummary(entry)
    entry.job = scope.launch {
        try {
            entry.sources = gateway.sources(media) { arriving ->
                entry.sources = arriving; entry.version.value++; publishSourceSummary(entry)
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Throwable) {
            // The chooser reports discovery failures; the title line only goes quiet.
            entry.error = error
        }
        entry.finishedAt = SystemClock.elapsedRealtime(); entry.done = true
        if (entry.fresh()) { sourcePreviewCache[key] = entry; SourcePreviewPolicy.trim(sourcePreviewCache) }
        entry.version.value++
        publishSourceSummary(entry)
    }
}

/** Called when a title's play target leaves the screen. */
internal fun AppController.releaseSourcePreview(media: Media) {
    val key = sourcePreviewKey(media) ?: return
    val entry = sourcePreview ?: return
    if (entry.key != key) return
    val route = _state.value.route
    val adopted = (route is Route.Sources || route is Route.Player) && SourcePreviewPolicy.keep(key, _state.value.selectedProfile?.id, route)
    if (!adopted) stopSourcePreview()
}

/** Route changes cancel a preview whose title, chooser and player have all gone. */
internal fun AppController.pruneSourcePreview() {
    val entry = sourcePreview ?: return
    if (!SourcePreviewPolicy.keep(entry.key, _state.value.selectedProfile?.id, _state.value.route)) stopSourcePreview()
}

internal fun AppController.stopSourcePreview() {
    val entry = sourcePreview ?: return
    sourcePreview = null
    if (!entry.done) entry.job?.cancel()
}

internal fun AppController.cancelSourcePreviews() {
    stopSourcePreview(); sourcePreviewCache.clear()
    _state.value = _state.value.copy(sourceSummary = null)
}

/**
 * The chooser's discovery: adopt the title's fresh or running discovery for the
 * same item, otherwise run one. Either way the finished list is remembered for
 * the title line.
 */
internal suspend fun AppController.discoverSourcesFor(media: Media, onUpdate: (List<Source>) -> Unit): List<Source> {
    val key = sourcePreviewKey(media)
    if (key != null) {
        val entry = sourcePreview?.takeIf { it.key == key } ?: sourcePreviewCache[key]?.takeIf { it.fresh() }
        if (entry != null && entry.fresh()) { onUpdate(entry.sources); return entry.sources }
        val running = entry?.job?.takeIf { it.isActive }
        if (entry != null && running != null) {
            coroutineScope {
                val mirror = launch { entry.version.collect { onUpdate(entry.sources) } }
                running.join(); mirror.cancel()
            }
            if (entry.done) {
                entry.error?.let { if (entry.sources.isEmpty()) throw it }
                return entry.sources
            }
        }
    }
    val discovered = gateway.sources(media, onUpdate)
    if (key != null && discovered.isNotEmpty()) {
        val entry = SourcePreviewEntry(key).apply { sources = discovered; done = true; finishedAt = SystemClock.elapsedRealtime() }
        sourcePreviewCache[key] = entry; SourcePreviewPolicy.trim(sourcePreviewCache)
    }
    return discovered
}

internal fun AppController.rankSources(sources: List<Source>): List<Source> =
    SourceRankPolicy.order(sources, rankCapabilities(), _state.value.preferences.audioLanguage)

private fun AppController.publishSourceSummary(entry: SourcePreviewEntry) {
    if (sourcePreview !== entry) return
    val sources = entry.sources
    _state.value = _state.value.copy(sourceSummary = SourceSummary(entry.key, rankSources(sources).firstOrNull(), sources.size, entry.done))
}

/** Device limits come from the measured probe without creating a player for a title page. */
private fun AppController.ensureRankCapabilities() {
    if (rankCapabilities() != null || capabilityProbe?.isActive == true) return
    capabilityProbe = scope.launch {
        runCatching { probedCapabilities = PlaybackClientCapabilities.from(backendFactory.probe()) }
        sourcePreview?.let { publishSourceSummary(it) }
    }
}
