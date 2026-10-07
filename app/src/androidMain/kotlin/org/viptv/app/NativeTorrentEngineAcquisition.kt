package org.viptv.app

import android.util.Base64
import uniffi.playback_gateway_ffi.TorrentHandle
import uniffi.playback_gateway_ffi.TorrentSettlement

/** All input validation/selection decisions are Rust-owned; Kotlin performs retained IO effects. */
internal fun beginNativeTorrentAcquisition(cache: NativeTorrentCache, control: NativePlaybackControl, remainingBudgetMillis: () -> Long): NativeTorrentAcquisitionEffect {
    val manager = cache.managerForAdmission() as? NativeTorrentEngineCacheManager
        ?: throw NativeTorrentCoordinatorUnavailable()
    return control.withAuthorizedGrant { bridge, facts ->
        val hash = bridge.privateInfoHash(facts)
        val index = bridge.privateFileIndex(facts)
        val size = bridge.privateExpectedFileSize(facts)
        val input = bridge.privateInputValue(facts)
        fun remaining(): UInt {
            val millis = remainingBudgetMillis()
            if (millis <= 0) throw NativeTorrentFailure("native_acquisition_timeout")
            return millis.takeIf { it <= 30_000 }?.toUInt() ?: throw NativeTorrentCoordinatorUnavailable()
        }
        val acquisition = when (bridge.privateInputKind(facts)) {
            "magnet" -> manager.client.beginSelected(input, hash, index, size, remaining())
            "metainfo" -> {
                val decoded = Base64.decode(input, Base64.NO_WRAP)
                manager.client.beginSelectedMetainfo(decoded, hash, index, size, remaining())
            }
            else -> throw NativeTorrentCoordinatorUnavailable()
        }
        object : NativeTorrentAcquisitionEffect {
            override fun waitReady(): NativeTorrentHandleEffect = StrictHandle(acquisition.waitReady(), control, hash, index)
            override fun cancel() = acquisition.cancel()
            override fun cancelAndJoin() = acquisition.cancelAndWait() == TorrentSettlement.SETTLED
            override fun close() = acquisition.close()
            override fun toString() = "NativeTorrentAcquisitionEffect(<redacted>)"
        }
    }
}

private class StrictHandle(
    private val handle: TorrentHandle,
    private val control: NativePlaybackControl,
    private val hash: String,
    private val index: UInt,
) : NativeTorrentHandleEffect {
    override fun validatedCapability(): NativeTorrentCapability {
        val files = handle.files()
        val selected = files.singleOrNull { it.index == index } ?: throw NativeTorrentCoordinatorUnavailable()
        val fileCount = handle.metadataFileCount()
        val matches = control.withAuthorizedGrant { bridge, facts ->
            bridge.metadataMatchesNative(hash, index, fileCount, selected.size, true, facts)
        }
        if (!matches) {
            throw NativeTorrentCoordinatorUnavailable()
        }
        return NativeTorrentCapability.validated(handle.streamUrl(index), index)
    }
    override fun stop() = handle.stop()
    override fun stopAndJoin() = handle.stopAndWait() == TorrentSettlement.SETTLED
    override fun close() = handle.close()
    override fun toString() = "NativeTorrentHandleEffect(<redacted>)"
}
