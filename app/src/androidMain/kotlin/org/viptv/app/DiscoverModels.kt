package org.viptv.app

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

object DiscoverPolicy {
    /** Canonical Stremio-style discover groups; addon namespaces fold into them. */
    fun typeGroup(type: String): String = when {
        type == "movie" -> "movie"
        type == "series" -> "series"
        type == "anime" || type.startsWith("anime.") -> "anime"
        else -> "other"
    }
    fun groupLabel(group: String): String = when (group) {
        "movie" -> "Movies"
        "series" -> "Series"
        "anime" -> "Anime"
        else -> "Other"
    }
    fun firstCatalog(catalogs: List<DiscoverCatalog>, type: String): DiscoverCatalog? =
        catalogs.firstOrNull { it.key.type != "live" && typeGroup(it.key.type) == typeGroup(type) }
            ?: catalogs.firstOrNull { it.key.type != "live" }
            ?: catalogs.firstOrNull()

    /** Required declared filters use their server default or first allowed choice. */
    fun defaults(catalog: DiscoverCatalog): Map<String, String> = buildMap {
        catalog.filters.forEach { filter ->
            val value = filter.defaultValue ?: filter.options.firstOrNull()
            if (filter.required && !value.isNullOrBlank()) put(filter.name, value)
        }
    }

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
