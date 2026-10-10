package org.viptv.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Fetches declared catalogs before exposing Discover; no synthetic filters or catalog IDs. */
internal fun AppController.openDiscover() {
    if (_state.value.discoverUi.catalogs.isNotEmpty() && _state.value.discoverUi.items.isNotEmpty()) {
        _state.value = _state.value.copy(route = Route.Browse(Destination.Discover), discoverUi = _state.value.discoverUi.copy(loading = false), loading = false)
        return
    }
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
    val catalog = current.catalogs.firstOrNull { catalog ->
        if (type == "anime") catalog.key.id.startsWith("anime-")
        else catalog.key.type == type && !catalog.key.id.startsWith("anime-")
    } ?: return
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

/** Append the server's cursor page without discarding the user's scroll/focus. */
internal fun AppController.appendDiscoverPage() {
    val current = _state.value.discoverUi
    val skip = current.nextSkip ?: return
    if (current.loading || _state.value.route != Route.Browse(Destination.Discover)) return
    val catalog = current.catalogs.firstOrNull { it.key == current.selectedCatalogKey } ?: return
    val generation = ++discoverGeneration
    _state.value = _state.value.copy(discoverUi = current.copy(loading = true))
    discoverJob = scope.launch {
        try {
            val page = gateway.discover(DiscoverPolicy.request(catalog, current.selectedFilters, skip))
            if (!isCurrentDiscover(generation)) return@launch
            _state.value = _state.value.copy(discoverUi = current.copy(
                items = (current.items + page.items).distinctBy { HomeFocusPolicy.mediaKey(it) },
                nextSkip = page.nextSkip?.takeIf { page.hasMore && it > skip },
                loading = false, error = null,
            ))
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { if (isCurrentDiscover(generation)) _state.value = _state.value.copy(discoverUi = current.copy(loading = false, error = "Couldn't load more titles. Try another catalog or retry.")) }
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
    simklSearchFilters=simklSearchFilters.filterKeys { it!="skip" }
    runSimklSearch(query,false)
}
internal fun AppController.appendSimklSearch() { runSimklSearch(_state.value.searchQuery,true) }
private fun AppController.runSimklSearch(query: String, append: Boolean) {
    searchJob?.cancel()
    val normalized=query.trim()
    val previous=if(append) _state.value.searchSections else emptyList()
    fun combine(next:List<SearchSection>):List<SearchSection> = (previous+next).groupBy { it.id }.values.map { entries -> entries.last().copy(items=entries.flatMap { it.items }.distinctBy { HomeFocusPolicy.mediaKey(it) }) }
    _state.value=_state.value.copy(searchQuery=query.take(256),searchResults=previous.flatMap {it.items},searchSections=previous,simklSearchFilters=simklSearchFilters,
        searchHasMore=false,searchStatus="Searching…",loading=false,message=null)
    searchJob=scope.launch {
        delay(if(append) 0 else 650)
        try {
            val profileId=_state.value.selectedProfile?.id
            val results=gateway.advancedSearch(normalized,simklSearchFilters) { partial ->
                if(isActive && _state.value.route==Route.Search && _state.value.selectedProfile?.id==profileId && _state.value.searchQuery.trim()==normalized) {
                    val sections=combine(partial.sections)
                    _state.value=_state.value.copy(searchSections=sections,searchResults=sections.flatMap {it.items},searchStatus="Searching…")
                }
            }
            if(isActive && _state.value.route==Route.Search && _state.value.selectedProfile?.id==profileId) {
                val sections=combine(results.sections);val count=sections.flatMap {it.items}.distinctBy {HomeFocusPolicy.mediaKey(it)}.size
                _state.value=_state.value.copy(searchSections=sections,searchResults=sections.flatMap {it.items},searchHasMore=results.hasMore,
                    searchStatus="$count results · ${results.coverage}"+(if(results.partialFailure) " · Some sources couldn't load" else ""),loading=false)
            }
        } catch(cancelled:CancellationException){throw cancelled}
        catch(error:Exception){if(isActive) _state.value=_state.value.copy(loading=false,searchStatus=error.message ?: "Search unavailable")}
    }
}

internal fun AppController.setCalendarPeriod(date: String? = null, month: String? = null) {
    val current = _state.value.discoverUi
    val catalog = current.catalogs.firstOrNull { it.key == current.selectedCatalogKey } ?: return
    val filters = current.selectedFilters.toMutableMap().apply {
        remove("date"); remove("month")
        date?.let { put("date", it) }; month?.let { put("month", it) }
    }
    if (filters == current.selectedFilters) return
    startDiscoverRequest(catalog, filters, 0, emptyList(), current.selectedType)
}
