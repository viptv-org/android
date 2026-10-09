package org.viptv.video

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NetworkMediaDnsTest {
    @Test fun repeatedHostLookupsReuseSystemAnswersUntilExpiryOrNetworkChange() {
        var now = 0L
        var queries = 0
        val v4 = InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1))
        val v6 = InetAddress.getByAddress(ByteArray(16).also { it[0] = 0x20; it[1] = 0x01; it[15] = 1 })
        val system = object : Dns { override fun lookup(hostname: String): List<InetAddress> { queries++; return listOf(v4, v6) } }
        val dns = NetworkMediaDns(system, { now })
        repeat(5) { assertEquals(listOf(v4, v6), dns.lookup("public.invalid", 1)) }
        assertEquals(1, queries)
        now = 31_000_000_000
        dns.lookup("public.invalid", 1)
        assertEquals(2, queries)
        dns.lookup("public.invalid", 2)
        assertEquals(3, queries)
        dns.lookup("public.invalid", null)
        dns.lookup("public.invalid", null)
        assertEquals(5, queries)
    }

    @Test fun failedLookupIsNeverNegativelyCached() {
        var queries = 0
        val system = object : Dns { override fun lookup(hostname: String): List<InetAddress> { queries++; throw UnknownHostException("fixture failure") } }
        val dns = NetworkMediaDns(system, { 0 })
        repeat(2) { assertFailsWith<UnknownHostException> { dns.lookup("public.invalid", 1) } }
        assertEquals(2, queries)
    }
}
