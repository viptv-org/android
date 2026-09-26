package org.viptv.app

import android.content.Context

/**
 * User-configurable backend origin. The private household build ships the
 * production default; any build can point at a compatible backend. Changing
 * the origin invalidates the stored device grant, so the app returns to
 * pairing instead of replaying credentials against a different server.
 */
internal object ServerOrigin {
    private const val PREFS = "viptv.settings"
    private const val KEY = "origin"
    val DEFAULT = "https://viptv.syek.tech"

    fun load(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.takeIf { validate(it) != null } ?: DEFAULT
    fun save(context: Context, origin: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, origin).commit()
    }

    /** Accepts a bare HTTPS origin with an optional port and no path, query or credentials. */
    fun validate(value: String): String? {
        val trimmed = value.trim().trimEnd('/')
        val match = Regex("^https://(\\[[0-9a-fA-F:.]+]|[a-zA-Z0-9](?:[a-zA-Z0-9.-]*[a-zA-Z0-9])?)(:[0-9]{1,5})?$").find(trimmed) ?: return null
        if (".." in trimmed) return null
        val port = match.groupValues[2].takeIf { it.isNotEmpty() }?.removePrefix(":")?.toIntOrNull() ?: return match.value
        if (port !in 1..65535) return null
        return match.value
    }
}
