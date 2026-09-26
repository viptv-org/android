package org.viptv.app

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionReadRecoveryTest {
    @Test fun expiredReadConnectionGetsOneFreshAttempt() = runBlocking {
        var calls = 0
        val response = executeSessionRead(JSONObject().put("method", "GET")) {
            if (++calls == 1) JSONObject("""{"Err":{"Io":"HTTP transport failed"}}""")
            else JSONObject("""{"Ok":{"status":200}}""")
        }
        assertEquals(2, calls)
        assertEquals(200, response.getJSONObject("Ok").getInt("status"))
    }

    @Test fun writesCancellationAndHttpFailuresAreNeverRetried() = runBlocking {
        listOf(
            "POST" to """{"Err":{"Io":"HTTP transport failed"}}""",
            "GET" to """{"Err":{"Io":"Request cancelled"}}""",
            "GET" to """{"Err":"Timeout"}""",
            "GET" to """{"Ok":{"status":403}}""",
        ).forEach { (method, body) ->
            var calls = 0
            executeSessionRead(JSONObject().put("method", method)) { calls++; JSONObject(body) }
            assertEquals(1, calls)
        }
    }
}
