package org.viptv.app

import org.json.JSONObject

internal fun v2Ready(id: String, delivery: String): String = JSONObject()
    .put("id", id).put("status", "ready").put("expires_at", System.currentTimeMillis() / 1000 + 60)
    .put("renew_after_seconds", 20).put("delivery", JSONObject(delivery)).toString()
