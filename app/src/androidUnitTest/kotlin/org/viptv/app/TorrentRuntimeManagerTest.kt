package org.viptv.app

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONObject

class TorrentRuntimeManagerTest {
    private class Port : TorrentRuntimePort {
        var alive = true
        var terminated = 0
        var next = 0
        val commands = mutableListOf<JSONObject>()
        override fun call(command: JSONObject, timeoutMillis: Long): JSONObject {
            commands.add(JSONObject(command.toString()))
            return JSONObject().put("version",2).put("ok",true).apply {
                if (command.getString("op") == "prepare") put("handle", (++next).toString())
            }
        }
        override fun isAlive() = alive
        override fun terminate(): Boolean { alive = false; terminated++; return true }
        override fun close() { terminate() }
    }
    @Test fun oneWarmWorkerPreservesHintsAndStaleCloseCannotKillReplacement() {
        val directory = Files.createTempDirectory("runtime-manager").toFile()
        val ports = mutableListOf<Port>()
        val manager = NativeTorrentEngineCacheManager(directory, NativeTorrentCacheLimits.PAYLOAD_BYTES) { Port().also(ports::add) }
        val source = JSONObject().put("magnet","magnet:?xt=urn:btih:${"0".repeat(40)}")
            .put("trackers",org.json.JSONArray(listOf("udp://tracker.example:1337/announce")))
        val first = manager.prepare(source, 20_000)
        val second = manager.prepare(source, 20_000)
        assertEquals(first.first, second.first)
        assertEquals(1,ports.size)
        assertEquals(1,ports[0].commands.count { it.getString("op") == "open" })
        assertEquals(java.io.File(directory,"pieces").path,ports[0].commands.first { it.getString("op")=="open" }.getJSONObject("config").getString("cache_dir"))
        assertEquals(source.toString(),ports[0].commands.first { it.getString("op") == "prepare" }.getJSONObject("source").toString())
        assertFalse(ports[0].commands.last().getJSONObject("source").has("file_index"))
        ports[0].alive = false
        val replacement = manager.prepare(source,20_000)
        assertTrue(replacement.first > first.first)
        assertTrue(manager.stop(first.first,first.second))
        assertTrue(ports[1].alive)
        manager.closeAfterSettlement()
        assertFalse(ports[1].alive)
        assertFalse(manager.hasFailedSettlement())
        directory.deleteRecursively()
    }
}
