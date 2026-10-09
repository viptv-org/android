package org.viptv.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class TorrentRuntimeFramesTest {
    @Test fun metainfoLargerThanBinderRoundTripsAsAFrame() {
        val bytes = ByteArray(4 * 1024 * 1024) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        TorrentRuntimeFrames.write(DataOutputStream(output), bytes)
        assertContentEquals(bytes, TorrentRuntimeFrames.read(DataInputStream(ByteArrayInputStream(output.toByteArray()))))
    }
    @Test fun oversizedAndTruncatedFramesAreRejectedBeforePayloadAllocation() {
        for (length in listOf(-1, 0, TorrentRuntimeFrames.MAX_BYTES + 1)) {
            val bytes = ByteArrayOutputStream().apply { DataOutputStream(this).writeInt(length) }.toByteArray()
            assertFailsWith<NativeTorrentCoordinatorUnavailable> { TorrentRuntimeFrames.read(DataInputStream(ByteArrayInputStream(bytes))) }
        }
        assertFailsWith<java.io.EOFException> { TorrentRuntimeFrames.read(DataInputStream(ByteArrayInputStream(byteArrayOf(0,0,0,2,1)))) }
    }
}
