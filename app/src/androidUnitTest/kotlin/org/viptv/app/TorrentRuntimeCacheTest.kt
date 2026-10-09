package org.viptv.app

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TorrentRuntimeCacheTest {
    @Test fun verifiedContentSurvivesScopeAndRestartButSignOutRemovesIt() {
        val parent = Files.createTempDirectory("runtime-cache").toFile()
        var directory: File? = null
        fun open() = NativeTorrentCache.open(parent, { path, _ ->
            directory = path
            object : NativeTorrentCacheManager {
                override fun hasFailedSettlement() = false
                override fun closeAfterSettlement() {}
            }
        }, retainContent = true)
        val first = open()
        val payload = File(requireNotNull(directory), "verified.fixture").apply { writeBytes(byteArrayOf(1,2,3)) }
        assertTrue(first.closeScope())
        assertTrue(payload.exists())
        val next = open()
        assertEquals(payload.parentFile, directory)
        assertTrue(next.closeScope(clearContent = true))
        assertFalse(payload.exists())
        parent.deleteRecursively()
    }
}
