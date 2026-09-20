package org.viptv.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Fetches declared catalogs before exposing Discover; no synthetic filters or catalog IDs. */
internal fun AppController.openDiscover() {
    discoverJob?.cancel()
    val generation = ++discoverGeneration
    discoverJob = scope.launch {
        val previous = _state.value.discoverUi
        _state.value = _state.value.copy(
            route = Route.Browse(Destination.Discover),
            discoverUi = previous.copy(loading = true, error = null),
            loading = false,
            message = null,
        )
        try {
            val catalogs = gateway.catalogs()
            if (!isCurrentDiscover(generation)) return@launch
            val catalog = catalogs.firstOrNull { it.key == previous.selectedCatalogKey }
                ?: DiscoverPolicy.firstCatalog(catalogs, previous.selectedType)
            if (catalog == null) {
                _state.value = _state.value.copy(discoverUi = DiscoverUiState(catalogs = catalogs, loading = false, error = "No catalogs are available."))
                return@launch
            }
            val filters = if (catalog.key == previous.selectedCatalogKey) previous.selectedFilters else DiscoverPolicy.defaults(catalog)
            _state.value = _state.value.copy(
                discoverUi = previous.copy(
                    catalogs = catalogs,
                    selectedType = DiscoverPolicy.typeGroup(catalog.key.type),
                    selectedCatalogKey = catalog.key,
                    selectedFilters = filters,
                    loading = true,
                    error = null,
                ),
            )
            requestDiscoverPage(generation, catalogs, catalog, filters, skip = 0, previousSkips = emptyList())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            if (isCurrentDiscover(generation)) {
                _state.value = _state.value.copy(discoverUi = previous.copy(loading = false, error = "Couldn't load catalogs. Press OK to retry."))
            }
        }
    }
}

internal fun AppController.setDiscoverType(type: String) {
    val current = _state.value.discoverUi
    val catalog = DiscoverPolicy.firstCatalog(current.catalogs, type) ?: return
    startDiscoverRequest(catalog, DiscoverPolicy.defaults(catalog), skip = 0, previousSkips = emptyList(), selectedType = type)
}

internal fun AppController.setDiscoverCatalog(key: CatalogKey) {
    val catalog = _state.value.discoverUi.catalogs.firstOrNull { it.key == key } ?: return
    startDiscoverRequest(catalog, DiscoverPolicy.defaults(catalog), skip = 0, previousSkips = emptyList(), selectedType = DiscoverPolicy.typeGroup(catalog.key.type))
}

/** Search, genre, and extras all reset the forward-only server cursor. */
internal fun AppController.setDiscoverFilter(key: String, value: String?) {
    val current = _state.value.discoverUi
    val catalog = current.catalogs.firstOrNull { it.key == current.selectedCatalogKey } ?: return
    val declared = catalog.filters.firstOrNull { it.name == key }
    if (key != "search" && declared == null) return
    if (key == "search" && !catalog.supportsSearch && declared?.kind != CatalogFilterKind.Search) return
    val normalized = value?.trim().orEmpty()
    val replacement = when {
        normalized.isNotBlank() -> normalized
        declared?.required == true -> declared.defaultValue ?: declared.options.firstOrNull().orEmpty()
        else -> ""
    }
    val filters = current.selectedFilters.toMutableMap().apply {
        if (replacement.isBlank()) remove(key) else put(key, replacement)
    }
    if (filters == current.selectedFilters) return
    startDiscoverRequest(catalog, filters, skip = 0, previousSkips = emptyList(), selectedType = current.selectedType)
}

internal fun AppController.changeDiscoverPage(delta: Int) {
    val current = _state.value.discoverUi
    val catalog = current.catalogs.firstOrNull { it.key == current.selectedCatalogKey } ?: return
    when {
        delta > 0 && current.nextSkip != null -> startDiscoverRequest(
            catalog, current.selectedFilters, current.nextSkip,
            current.previousSkips + current.requestedSkip, current.selectedType,
        )
        delta < 0 && current.previousSkips.isNotEmpty() -> startDiscoverRequest(
            catalog, current.selectedFilters, current.previousSkips.last(),
            current.previousSkips.dropLast(1), current.selectedType,
        )
    }
}

private fun AppController.startDiscoverRequest(
    catalog: DiscoverCatalog,
    filters: Map<String, String>,
    skip: Int,
    previousSkips: List<Int>,
    selectedType: String,
) {
    discoverJob?.cancel()
    val generation = ++discoverGeneration
    discoverJob = scope.launch {
        val catalogs = _state.value.discoverUi.catalogs
        _state.value = _state.value.copy(
            route = Route.Browse(Destination.Discover),
            discoverUi = _state.value.discoverUi.copy(
                catalogs = catalogs,
                selectedType = selectedType,
                selectedCatalogKey = catalog.key,
                selectedFilters = filters,
                requestedSkip = skip,
                previousSkips = previousSkips,
                loading = true,
                error = null,
            ),
            loading = false,
            message = null,
        )
        requestDiscoverPage(generation, catalogs, catalog, filters, skip, previousSkips)
    }
}

private suspend fun AppController.requestDiscoverPage(
    generation: Long,
    catalogs: List<DiscoverCatalog>,
    catalog: DiscoverCatalog,
    filters: Map<String, String>,
    skip: Int,
    previousSkips: List<Int>,
) {
    try {
        val page = gateway.discover(DiscoverPolicy.request(catalog, filters, skip))
        if (!isCurrentDiscover(generation) || page.catalog != catalog.key) return
        _state.value = _state.value.copy(
            discoverUi = _state.value.discoverUi.copy(
                catalogs = catalogs,
                selectedCatalogKey = catalog.key,
                selectedFilters = filters,
                items = page.items,
                requestedSkip = page.requestedSkip,
                nextSkip = page.nextSkip?.takeIf { page.hasMore },
                previousSkips = previousSkips,
                loading = false,
                error = null,
            ),
        )
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        if (isCurrentDiscover(generation)) {
            _state.value = _state.value.copy(discoverUi = _state.value.discoverUi.copy(loading = false, error = "Couldn't load this catalog. Press OK to retry."))
        }
    }
}

private fun AppController.isCurrentDiscover(generation: Long): Boolean =
    generation == discoverGeneration && _state.value.route == Route.Browse(Destination.Discover)

/** Each edit replaces prior work; a late response cannot repopulate a cleared query. */
internal fun AppController.search(query: String) {
    searchJob?.cancel()
    val normalized = query.trim()
    _state.value = _state.value.copy(
        searchQuery = query.take(256),
        searchResults = emptyList(),
        searchSections = emptyList(),
        searchStatus = if (normalized.isEmpty()) "Find your next favorite." else "Searching…",
        loading = false,
        message = null,
    )
    if (normalized.isEmpty()) return
    searchJob = scope.launch {
        delay(650)
        try {
            val results = gateway.search(normalized)
            if (isActive) {
                val count = results.sections.sumOf { it.items.size }
                val baseStatus = if (count == 0) "No results. Try another title." else "$count results"
                _state.value = _state.value.copy(
                    searchSections = results.sections,
                    searchResults = results.sections.flatMap(SearchSection::items),
                    searchStatus = if (results.partialFailure) "$baseStatus  Some sources couldn't load." else baseStatus,
                    loading = false,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            if (isActive) _state.value = _state.value.copy(loading = false, searchStatus = "Searching…  Some sources couldn't load.")
        }
    }
}
