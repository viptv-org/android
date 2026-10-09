package org.viptv.app

import java.io.DataInputStream
import java.io.DataOutputStream

/** Pipes carry private control bodies outside Binder's small transaction buffer. */
internal object TorrentRuntimeFrames {
    const val MAX_BYTES = 6 * 1024 * 1024
    fun read(input: DataInputStream): ByteArray {
        val size = input.readInt()
        if (size !in 1..MAX_BYTES) throw NativeTorrentCoordinatorUnavailable()
        return ByteArray(size).also(input::readFully)
    }
    fun write(output: DataOutputStream, bytes: ByteArray) {
        if (bytes.size !in 1..MAX_BYTES) throw NativeTorrentCoordinatorUnavailable()
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }
}
