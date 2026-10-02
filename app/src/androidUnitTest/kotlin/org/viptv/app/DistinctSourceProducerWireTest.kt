package org.viptv.app

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DistinctSourceProducerWireTest {
    @Test fun configuredNamesStaySeparateEvenWhenOnlyOneAddonHasRows() {
        val observed = listOf("addon:3", "addon:4", "addon:8").map { SourceProducerOutcome(it) }
        val installed = listOf(
            Addon("3", "Torrentio", "", true), Addon("4", "TorrentsDB", "", true),
            Addon("8", "Torrentio TB", "", true), Addon("9", "Catalog only", "", true),
        )
        val playable = Source("playable-8", "Torrentio", addonId = "addon:8")

        val named = namedSourceProducers(observed, installed, listOf(playable))

        assertEquals(listOf("Torrentio", "TorrentsDB", "Torrentio TB"), named.map { it.label })
        assertEquals(3, named.map { it.providerKey }.toSet().size)
    }

    @Test fun zeroResultAddonsRemainDistinctWhileAnotherAddonSuppliesPlayableRows() = runBlocking {
        FixtureServer(3) { request -> when (request.target) {
            "/api/v2/streams" -> FixtureResponse("""{"id":"job-producers"}""")
            "/api/v2/streams/job-producers?after=0" -> FixtureResponse("""{"events":[
                {"seq":1,"source":"addon:3","streams":[],"error_code":"source_format_unsupported","error":"Only HTTP(S) streams are supported here."},
                {"seq":2,"source":"addon:4","streams":[],"error_code":"source_format_unsupported","error":"Only HTTP(S) streams are supported here."}
            ],"done":false}""")
            "/api/v2/streams/job-producers?after=2" -> FixtureResponse("""{"events":[
                {"seq":3,"source":"addon:8","streams":[{"id":"playable-8","source_name":"Torrentio TB","source_addon_id":"addon:8","source_fingerprint":"fingerprint-8"}]}
            ],"done":true}""")
            else -> error("Unexpected request ${request.target}")
        } }.use { server ->
            val producerUpdates = mutableListOf<List<SourceProducerOutcome>>()
            val sourceUpdates = mutableListOf<List<Source>>()
            val found = VipTvHttpGateway(server.origin).sources(Media("title-1", "movie", "Title"),
                onUpdate = { sourceUpdates += it }, onProducerUpdate = { producerUpdates += it })

            assertEquals(listOf("addon:3", "addon:4"), producerUpdates.first().map { it.sourceId })
            assertEquals(listOf("addon:3", "addon:4", "addon:8"), producerUpdates.last().map { it.sourceId })
            assertEquals(listOf("addon:addon:3", "addon:addon:4", "addon:addon:8"), producerUpdates.last().map { it.providerKey })
            assertEquals(listOf("source_format_unsupported", "source_format_unsupported", null), producerUpdates.last().map { it.errorCode })
            assertTrue(sourceUpdates.first().isEmpty())
            assertEquals(listOf("playable-8"), found.map { it.id })
            assertEquals(producerUpdates.last().last().providerKey, SourceDisplayPolicy.providerKey(found.single()))
            server.assertHealthy()
        }
    }
}
