package org.viptv.app

import org.json.JSONArray
import org.json.JSONObject
import org.viptv.core.wire.CoreJson
import org.viptv.core.wire.DiscoverPolicyProjection
import org.viptv.core.wire.EpisodeWatching
import org.viptv.core.wire.HeroEdgePool
import org.viptv.core.wire.HomeActions
import org.viptv.core.wire.PhonePresentation
import org.viptv.core.wire.SourceRanks
import uniffi.viptv_core.normalize

/** Generated semantic projections; Android supplies facts and executes their intents. */
internal object SharedPresentation {
    private inline fun <reified T> project(operation: String, input: JSONObject): T =
        CoreJson.decode(normalize(operation, input.toString(), ""))

    fun home(media: Media, queueShelf: Boolean = false): HomeActions = project("homeActions",
        JSONObject().put("item", JSONObject(media.normalizedJson(includeEpisodes = false))).put("queueShelf", queueShelf))

    fun episode(media: Media): EpisodeWatching = project("episodeWatching", JSONObject(media.normalizedJson(includeEpisodes = false)))

    /**
     * TV hero backdrop category and edge pool for a title, restricted to the edge ids the renderer ships.
     * Every field is written explicitly rather than relying on core's defaults for lists the generated codec omits when empty.
     */
    fun heroEdgePool(mediaType: String, genres: List<String>, availableEdges: List<String>): HeroEdgePool = project("heroEdgePool",
        JSONObject().put("mediaType", mediaType).put("genres", JSONArray(genres)).put("availableEdges", JSONArray(availableEdges)))

    fun phone(media: Media? = null, shelf: HomeShelf? = null, contentType: String? = null): PhonePresentation = project("phonePresentation",
        JSONObject().putOpt("contentType", contentType)
            .put("item", JSONObject().putOpt("season", media?.season).putOpt("episode", media?.episode).putOpt("year", media?.year))
            .put("shelf", JSONObject().putOpt("title", shelf?.title).putOpt("contentType", shelf?.contentType)
                .putOpt("catalogName", shelf?.catalogName).put("isQueueShelf", shelf?.isQueueShelf == true)))

    /** Only safe display facts enter ranking; unknown measured limits remain absent. */
    fun ranks(sources: List<Source>, capabilities: PlaybackClientCapabilities?, audioLanguage: String): SourceRanks = project("sourceRanks",
        JSONObject().put("sources", JSONArray().also { rows -> sources.forEach { rows.put(sourceLabel(it)) } })
            .put("capabilities", JSONObject().putOpt("maxHeight", capabilities?.maxHeight?.takeIf { it > 0 }).put("hevcSdr", capabilities?.hevcSdr == true))
            .put("preferences", JSONObject().put("audioLanguage", audioLanguage)))

    private fun sourceLabel(source: Source): JSONObject = JSONObject().put("name", source.name)
        .putOpt("title", source.quality).putOpt("audio", source.audio)
        .put("raw", JSONObject().put("description", source.description))

    fun namedProducers(observed: List<SourceProducerOutcome>, addons: List<Addon>, sources: List<Source>): List<SourceProducerOutcome> {
        val result = project<List<org.viptv.core.wire.SourceProducerOutcome>>("sourceProducerLabels", JSONObject()
            .put("observed", JSONArray().also { rows -> observed.forEach { rows.put(JSONObject(CoreJson.encode(it.wire()))) } })
            .put("addons", JSONArray().also { rows -> addons.forEach { rows.put(JSONObject().put("id", it.id).put("name", it.name)) } })
            .put("sources", JSONArray().also { rows -> sources.forEach { rows.put(sourceLabel(it).put("provider", it.provider).putOpt("sourceAddonId", it.addonId)) } }))
        return result.map { it.view() }
    }

    fun producers(output: JSONObject): List<SourceProducerOutcome> =
        CoreJson.decode<List<org.viptv.core.wire.SourceProducerOutcome>>(output.getJSONArray("producers").toString()).map { it.view() }

    fun discover(type: String, catalogs: List<DiscoverCatalog> = emptyList(), catalog: DiscoverCatalog? = null): DiscoverPolicyProjection = project("discoverPolicy",
        JSONObject().put("type", type)
            .put("catalogs", JSONArray().also { rows -> catalogs.forEach { rows.put(JSONObject().put("type", it.key.type)) } })
            .put("catalog", JSONObject().put("supportsSearch", catalog?.supportsSearch == true).put("extras", JSONArray().also { rows ->
                catalog?.filters?.forEach { rows.put(JSONObject().put("name", it.name).put("required", it.required)
                    .put("options", JSONArray(it.options)).putOpt("defaultValue", it.defaultValue)) }
            })))

    private fun SourceProducerOutcome.wire() = org.viptv.core.wire.SourceProducerOutcome(sourceId, label, errorCode, errorMessage)
    private fun org.viptv.core.wire.SourceProducerOutcome.view() = SourceProducerOutcome(sourceId, label, errorCode, errorMessage)
}
