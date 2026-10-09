package org.viptv.app.hero

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Main-dispatcher confined, screen-owned decoded artwork with one speculative request. */
internal class HeroArtPreloader<T : Any>(
    private val scope: CoroutineScope,
    private val loadArt: suspend (String) -> T?,
    private val sizeOf: (T) -> Int,
    private val maxBytes: Long = 12L * 1024 * 1024,
) {
    private val cache = linkedMapOf<String, T>()
    private val requests = mutableMapOf<String, Deferred<T?>>()
    private val foreground = mutableMapOf<String, Int>()
    private var bytes = 0L
    private var selected: String? = null
    private var neighbours = emptyList<String>()
    private var worker: Job? = null
    private var closed = false

    fun setWindow(current: String?, upcoming: List<String>) {
        if (closed) return
        val targets = upcoming.filter { it.isNotBlank() && it != current }.distinct().take(2)
        if (selected == current && neighbours == targets) return
        selected = current
        neighbours = targets
        stopSpeculation()
        startSpeculation()
    }

    suspend fun load(url: String): T? {
        if (closed) return null
        foreground[url] = (foreground[url] ?: 0) + 1
        stopSpeculation()
        try {
            cache.remove(url)?.let { value -> cache[url] = value; return value }
            return request(url).await()
        } finally {
            val remaining = (foreground[url] ?: 1) - 1
            if (remaining == 0) foreground.remove(url) else foreground[url] = remaining
            if (remaining == 0 && url != selected && url !in neighbours) requests[url]?.cancel()
            startSpeculation()
        }
    }

    fun close() {
        closed = true
        worker?.cancel()
        requests.values.toList().forEach { it.cancel() }
        requests.clear()
        cache.clear()
        foreground.clear()
        bytes = 0
    }

    private fun stopSpeculation() {
        worker?.cancel()
        worker = null
        requests.filterKeys { it != selected && it !in foreground }.values.toList().forEach { it.cancel() }
    }

    private fun startSpeculation() {
        if (closed || foreground.isNotEmpty() || worker?.isActive == true || requests[selected]?.isActive == true) return
        val targets = neighbours
        val task = scope.launch(start = CoroutineStart.LAZY) {
            for (url in targets) {
                if (url in cache) continue
                try { request(url).await() }
                catch (cancelled: CancellationException) { currentCoroutineContext().ensureActive() }
            }
        }
        worker = task
        task.start()
    }

    private fun request(url: String): Deferred<T?> {
        requests[url]?.takeIf { !it.isCancelled }?.let { return it }
        val deferred = scope.async(start = CoroutineStart.LAZY) {
            try {
                val value = try { loadArt(url) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
                currentCoroutineContext().ensureActive()
                value?.also { retain(url, it) }
            } finally {
                if (requests[url] === currentCoroutineContext()[Job]) requests.remove(url)
                startSpeculation()
            }
        }
        requests[url] = deferred
        deferred.start()
        return deferred
    }

    private fun retain(url: String, value: T) {
        val weight = sizeOf(value).toLong()
        if (closed || weight < 0 || weight > maxBytes) return
        cache.remove(url)?.let { bytes -= sizeOf(it) }
        while (cache.isNotEmpty() && (cache.size >= 3 || bytes + weight > maxBytes)) {
            val oldest = cache.keys.first()
            bytes -= sizeOf(cache.remove(oldest)!!)
        }
        cache[url] = value
        bytes += weight
    }
}

/** Nearest visible row positions only; no speculative metadata fetch or artwork policy. */
internal fun <T> neighbouringHeroItems(items: List<T>, index: Int): List<T> =
    if (index !in items.indices) items.take(2)
    else listOf(index + 1, index - 1, index + 2, index - 2)
        .filter { it in items.indices }.take(2).map(items::get)
