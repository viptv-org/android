package org.viptv.video

import android.content.Context
import android.net.ConnectivityManager
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/** All app HTTP clients share short-lived system-DNS answers, fenced by active network identity. */
class AndroidMediaDns(context: Context) : Dns {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    override fun lookup(hostname: String): List<InetAddress> = shared.lookup(hostname,
        runCatching { connectivity?.activeNetwork?.networkHandle }.getOrNull())
    private companion object {
        val shared = NetworkMediaDns()
    }
}

/** Preserve Android DNS/VPN/private-DNS selection and every returned address family. */
internal class NetworkMediaDns(
    private val system: Dns = Dns.SYSTEM,
    private val nowNanos: () -> Long = System::nanoTime,
) {
    private data class Key(val network: Long, val hostname: String)
    private data class Entry(val addresses: List<InetAddress>, val deadline: Long)
    private val entries = LinkedHashMap<Key, Entry>()
    fun lookup(hostname: String, networkId: Long?): List<InetAddress> {
        // Without network identity, let Android retain cache/invalidation ownership.
        if (networkId == null) return system.lookup(hostname)
        val key = Key(networkId, hostname)
        val now = nowNanos()
        synchronized(entries) {
            entries.entries.removeAll { it.value.deadline <= now || it.key.network != networkId }
            entries[key]?.let { return it.addresses }
        }
        val addresses = system.lookup(hostname).toList()
        if (addresses.isEmpty()) throw UnknownHostException("No reachable DNS addresses")
        synchronized(entries) {
            if (entries.size >= 64) entries.remove(entries.keys.first())
            entries[key] = Entry(addresses, nowNanos() + 30_000_000_000L)
        }
        return addresses
    }
}
