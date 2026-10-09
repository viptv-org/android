@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package org.viptv.video

import android.os.Debug
import android.os.SystemClock
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/** Development transfer timings contain only offsets, byte counts and durations. */
internal class AndroidMedia3TransferDiagnostic : TransferListener {
    private var initialized = 0L
    private var started = 0L
    private var cpuStarted = 0L
    private var bytes = 0L
    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        initialized = SystemClock.elapsedRealtime()
        bytes = 0
    }
    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        started = SystemClock.elapsedRealtime()
        cpuStarted = Debug.threadCpuTimeNanos()
        log("range_start=${dataSpec.position} headers_ms=${started - initialized}")
    }
    override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) { bytes += bytesTransferred }
    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        log("range_end=${dataSpec.position} body_ms=${SystemClock.elapsedRealtime() - started} body_cpu_ms=${((Debug.threadCpuTimeNanos() - cpuStarted) / 1_000_000).coerceAtLeast(0)} bytes=$bytes")
    }
    private fun log(message: String) { android.util.Log.i("PlaybackTransferDiagnostic", message) }
}
