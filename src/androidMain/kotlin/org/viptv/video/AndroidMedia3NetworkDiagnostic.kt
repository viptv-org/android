package org.viptv.video

import android.os.SystemClock
import java.net.InetAddress
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.Proxy
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response

/** Closed network-stage timings; hostnames, addresses, requests and headers stay private. */
internal class AndroidMedia3NetworkDiagnostic : EventListener() {
    private var dns = 0L
    private var tcp = 0L
    private var tls = 0L
    private var sent = 0L
    private fun now() = SystemClock.elapsedRealtime()
    private fun log(stage: String, millis: Long, code: Int? = null) {
        android.util.Log.i("PlaybackNetworkDiagnostic", "stage=$stage elapsed_ms=$millis" + (code?.let { " http_code=$it" } ?: ""))
    }
    override fun dnsStart(call: Call, domainName: String) { dns = now() }
    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) { log("dns", now() - dns) }
    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) { tcp = now() }
    override fun secureConnectStart(call: Call) { tls = now(); log("tcp", tls - tcp) }
    override fun secureConnectEnd(call: Call, handshake: Handshake?) { log("tls", now() - tls) }
    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
        log(if (inetSocketAddress.address is Inet6Address) "connection_ipv6" else "connection_ipv4", now() - tcp)
    }
    override fun requestHeadersEnd(call: Call, request: Request) { sent = now() }
    override fun responseHeadersEnd(call: Call, response: Response) { log("response", now() - sent, response.code) }
}
