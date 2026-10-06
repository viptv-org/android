package org.viptv.app

import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals

class FixtureServerProbeTest {
    @Test
    fun `root HEAD probes do not consume expected API requests`() {
        FixtureServer(1) { request ->
            assertEquals("/api/probe", request.target)
            FixtureResponse("""{"ok":true}""")
        }.use { server ->
            val probe = URL("${server.origin}/").openConnection() as HttpURLConnection
            probe.requestMethod = "HEAD"
            probe.connectTimeout = 1_000
            probe.readTimeout = 1_000
            try {
                assertEquals(404, probe.responseCode)
            } finally {
                probe.disconnect()
            }

            val api = URL("${server.origin}/api/probe").openConnection() as HttpURLConnection
            api.connectTimeout = 1_000
            api.readTimeout = 1_000
            try {
                assertEquals(200, api.responseCode)
                assertEquals("""{"ok":true}""", api.inputStream.bufferedReader().use { it.readText() })
            } finally {
                api.disconnect()
            }
            assertEquals(listOf("/api/probe"), server.requests.map { it.target })
            server.assertHealthy()
        }
    }
}
