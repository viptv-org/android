package org.viptv.app

import org.json.JSONObject

/** Effect inputs only. Shared Rust validates and constructs the canonical request. */
data class LiveCatalogQuery(
    val catalogId: String? = null,
    val categoryId: String? = null,
    val collection: String? = null,
    val search: String? = null,
    val cursor: String? = null,
    val limit: Int = 50,
) {
    internal fun coreInput(operation: String) = JSONObject().put("operation",operation)
        .putOpt("catalogId",catalogId).putOpt("categoryId",categoryId)
        .putOpt("collection",collection).putOpt("search",search)
        .putOpt("cursor",cursor).put("limit",limit)
}
