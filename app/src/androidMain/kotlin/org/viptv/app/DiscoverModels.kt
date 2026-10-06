package org.viptv.app

import org.viptv.core.wire.DiscoverPolicyProjection

/** Server-declared catalog choices and cursor history for the Discover surface. */
data class DiscoverUiState(
    val catalogs: List<DiscoverCatalog> = emptyList(),
    val selectedType: String = "movie",
    val selectedCatalogKey: CatalogKey? = null,
    val selectedFilters: Map<String, String> = emptyMap(),
    val items: List<Media> = emptyList(),
    val requestedSkip: Int = 0,
    val nextSkip: Int? = null,
    val previousSkips: List<Int> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

/** Bounded adapter memoization of immutable Rust type projections, shared by UI and effects. */
internal class DiscoverTypeCache(private val project: (String) -> DiscoverPolicyProjection) {
    private val entries = LinkedHashMap<String, DiscoverPolicyProjection>(64, .75f, true)

    @Synchronized fun get(type: String): DiscoverPolicyProjection {
        entries[type]?.let { return it }
        val result = project(type)
        entries[type] = result
        if (entries.size > 64) entries.entries.iterator().run { next(); remove() }
        return result
    }
}

object DiscoverPolicy {
    private val types = DiscoverTypeCache { SharedPresentation.discover(it) }

    /** Canonical Stremio-style discover groups; addon namespaces fold into them. */
    internal fun grouping(type: String): DiscoverPolicyProjection = types.get(type)
    fun typeGroup(type: String): String = grouping(type).group
    fun groupLabel(group: String): String = grouping(group).groupLabel
    fun firstCatalog(catalogs: List<DiscoverCatalog>, type: String): DiscoverCatalog? =
        SharedPresentation.discover(type, catalogs).firstCatalogIndex?.toInt()?.let(catalogs::get)

    /** Required declared filters use their server default or first allowed choice. */
    fun defaults(catalog: DiscoverCatalog): Map<String, String> = SharedPresentation.discover(catalog.key.type, catalog = catalog).defaults

    fun request(catalog: DiscoverCatalog, filters: Map<String, String>, skip: Int): CatalogDiscoverRequest {
        val normalized = filters.mapValues { it.value.trim() }.filterValues(String::isNotBlank)
        return CatalogDiscoverRequest(
            catalog = catalog,
            skip = skip,
            search = normalized["search"].takeIf {
                catalog.supportsSearch || catalog.filters.any { filter -> filter.kind == CatalogFilterKind.Search }
            },
            genre = normalized["genre"],
            extras = normalized.filterKeys { it != "search" && it != "genre" },
        )
    }
}
