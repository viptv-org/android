package org.viptv.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.viptv.core.wire.PreviewAction
import org.viptv.core.wire.PreviewDecision
import org.viptv.core.wire.PreviewRoute

internal data class SourcePreviewSnapshot(
    val key: String,
    val sources: List<Source> = emptyList(),
    val producers: List<SourceProducerOutcome> = emptyList(),
    val done: Boolean = false,
    val error: Throwable? = null,
)

/** A single scoped discovery shared by a Title and its own manual picker. */
internal class TitleSourcePreview(
    private val scope: CoroutineScope,
    private val discover: suspend (Media, (List<SourceProducerOutcome>) -> Unit, (List<Source>) -> Unit) -> List<Source>,
    private val publish: (SourcePreviewSnapshot?) -> Unit,
    private val settleMillis: Long = 400,
    private val timeoutMillis: Long = 180_000,
) {
    private class Entry(val state: MutableStateFlow<SourcePreviewSnapshot>) { var job: Job? = null }
    private var active: Entry? = null
    val key: String? get() = active?.state?.value?.key
    fun refresh() { active?.state?.value?.let(publish) }

    fun start(key: String, media: Media) {
        if (CoreLifecycle.preview(PreviewAction.START, key, active?.state?.value) == PreviewDecision.RETAIN) return
        begin(key, media, settleMillis)
    }

    fun cancel() {
        val outgoing = active
        active = null
        outgoing?.job?.cancel()
        publish(null)
    }

    private fun begin(key: String, media: Media, settle: Long): Entry {
        cancel()
        val entry = Entry(MutableStateFlow(SourcePreviewSnapshot(key)))
        active = entry
        fun update(next: SourcePreviewSnapshot) {
            if (active !== entry) return
            if (entry.state.value == next) return
            if (CoreLifecycle.preview(PreviewAction.UPDATE, key, next, ownerMatches = active === entry) != PreviewDecision.ACCEPT) return
            entry.state.value = next
            publish(next)
        }
        publish(entry.state.value)
        entry.job = scope.launch {
            try {
                delay(settle)
                val sources = withTimeout(timeoutMillis) {
                    discover(media,
                        { producers -> update(entry.state.value.copy(producers = producers)) },
                        { sources -> update(entry.state.value.copy(sources = sources)) })
                }
                update(entry.state.value.copy(sources = sources, done = true))
            } catch (_: TimeoutCancellationException) {
                // A completed budget failure is recoverable by the picker, unlike
                // cancellation caused by leaving the route.
                update(entry.state.value.copy(done = true, error = IllegalStateException("Sources unavailable")))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                update(entry.state.value.copy(done = true, error = error))
            }
        }
        return entry
    }

    suspend fun adopt(key: String, media: Media, onProducers: (List<SourceProducerOutcome>) -> Unit, onSources: (List<Source>) -> Unit): List<Source> {
        val retained = active?.takeIf {
            CoreLifecycle.preview(PreviewAction.ADOPT, key, it.state.value, running = it.job?.isActive == true) == PreviewDecision.RETAIN
        }
        val entry = retained ?: begin(key, media, 0)
        coroutineScope {
            val updates = launch { entry.state.collect { onProducers(it.producers); onSources(it.sources) } }
            try { entry.job?.join() } finally { updates.cancel() }
        }
        val result = entry.state.value
        val decision = CoreLifecycle.preview(PreviewAction.RESULT, key, result)
        if (decision == PreviewDecision.CANCELLED) throw CancellationException("Title discovery cancelled")
        onProducers(result.producers)
        onSources(result.sources)
        if (decision == PreviewDecision.FAILED) throw requireNotNull(result.error)
        return result.sources
    }
}

internal object SourcePreviewPolicy {
    fun key(profileId: String?, media: Media): String? = CoreLifecycle.previewScope(profileId, media).key

    fun keep(key: String, profileId: String?, route: Route, releasing: Boolean = false): Boolean {
        val (target, owner) = when (route) {
            is Route.Details -> route.media to PreviewRoute.DETAILS
            is Route.Sources -> route.media to PreviewRoute.SOURCES
            is Route.Player -> route.media to PreviewRoute.PLAYER
            else -> null to PreviewRoute.OTHER
        }
        return CoreLifecycle.previewScope(profileId, target, key, owner, releasing).keep
    }
}

internal fun AppController.previewSources(media: Media) {
    val key = SourcePreviewPolicy.key(_state.value.selectedProfile?.id, media) ?: return
    if (rankCapabilities() == null && capabilityProbe?.isActive != true) capabilityProbe = scope.launch {
        try { probedCapabilities = PlaybackClientCapabilities.from(backendFactory.probe()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
        sourcePreview.refresh()
    }
    sourcePreview.start(key, media)
}

internal fun AppController.releaseSourcePreview(media: Media) {
    val key = SourcePreviewPolicy.key(_state.value.selectedProfile?.id, media) ?: return
    if (sourcePreview.key != key) return
    val route = _state.value.route
    if (!SourcePreviewPolicy.keep(key, _state.value.selectedProfile?.id, route, releasing = true)) sourcePreview.cancel()
}

internal suspend fun AppController.discoverSourcesFor(media: Media, onProducers: (List<SourceProducerOutcome>) -> Unit, onSources: (List<Source>) -> Unit): List<Source> {
    val key = SourcePreviewPolicy.key(_state.value.selectedProfile?.id, media)
    return if (key == null) gateway.sources(media, onProducers, onSources)
    else sourcePreview.adopt(key, media, onProducers, onSources)
}
