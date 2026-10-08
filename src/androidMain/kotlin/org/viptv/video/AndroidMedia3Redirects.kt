@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package org.viptv.video

import android.net.Uri
import android.os.SystemClock
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import java.util.concurrent.ConcurrentHashMap

/** Bounded in-memory redirects for exact URLs; controller authority changes clear the cache. */
internal class Media3RedirectCache {
    private class Entry(val uri: Uri, val expiresMillis: Long) {
        override fun toString() = "Media3RedirectEntry(<redacted>)"
    }
    private val resolved = ConcurrentHashMap<Uri, Entry>()
    fun clear() = resolved.clear()
    fun factory(upstream: DataSource.Factory): DataSource.Factory = DataSource.Factory {
        val source = upstream.createDataSource()
        object : DataSource by source {
            override fun open(dataSpec: DataSpec): Long {
                val cached = resolved[dataSpec.uri]?.takeIf { it.expiresMillis > SystemClock.elapsedRealtime() }
                val target = cached?.uri
                val length = try {
                    source.open(if (target == null) dataSpec else dataSpec.withUri(target))
                } catch (error: HttpDataSource.InvalidResponseCodeException) {
                    if (target == null || error.responseCode !in setOf(401, 403, 404, 410)) throw error
                    source.close()
                    resolved.remove(dataSpec.uri, cached)
                    source.open(dataSpec)
                }
                source.uri?.takeIf { it != dataSpec.uri }?.let {
                    if (resolved.size >= 16 && !resolved.containsKey(dataSpec.uri)) resolved.clear()
                    resolved[dataSpec.uri] = Entry(it, SystemClock.elapsedRealtime() + 300_000)
                }
                return length
            }
        }
    }
}
