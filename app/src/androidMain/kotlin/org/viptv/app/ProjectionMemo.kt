package org.viptv.app

/** Immutable media instances own projections; progress copies naturally miss the cache. */
internal class ProjectionMemo<T>(private val capacity: Int = 96) {
    private val values = java.util.IdentityHashMap<Media, T>()
    fun get(media: Media, compute: () -> T): T {
        synchronized(values) { values[media]?.let { return it } }
        val result = compute()
        synchronized(values) {
            if (values.size >= capacity) values.keys.iterator().let { if (it.hasNext()) values.remove(it.next()) }
            values[media] = result
        }
        return result
    }
    fun clear() = synchronized(values) { values.clear() }
}
