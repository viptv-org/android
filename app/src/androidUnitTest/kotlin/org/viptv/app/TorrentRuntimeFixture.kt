package org.viptv.app

import org.json.JSONObject
/** Convert only the well-formed baseline; malformed response edits remain original raw bytes. */
internal fun String.runtimeV2Fixture(): String {
    val envelope = JSONObject(this)
    val grant = envelope.getJSONObject("delivery").getJSONObject("grant")
    grant.put("version", 2).put("network_policy", "public_discovery_verified_v2")
        .put("archive_index", JSONObject.NULL).put("trackers", org.json.JSONArray())
    return envelope.toString()
}
