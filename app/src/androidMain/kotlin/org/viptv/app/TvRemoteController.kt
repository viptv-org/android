package org.viptv.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.*
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import tv.viptv.core.SmartCastClient
import tv.viptv.core.SmartCastTokenStore
import tv.viptv.core.SmartCastTransport
import uniffi.viptv_core.vizioDeviceinfoName
import uniffi.viptv_core.vizioDiscoveryCandidates

internal data class RemoteTv(val name: String, val origin: String)

internal object RemoteInput {
    fun origin(value: String): String? {
        val parts = value.trim().removePrefix("https://").split(':')
        if (parts.size !in 1..2) return null
        val octets = parts[0].split('.').map { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it == null || it !in 0..255 }) return null
        val a = octets[0]; val b = octets[1]!!
        if (!(a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254))) return null
        val port = if (parts.size == 1) 7345 else parts[1].toIntOrNull() ?: return null
        if (port !in listOf(7345, 9000)) return null
        return "https://${octets.joinToString(".")}:$port"
    }
    fun swipe(x: Float, y: Float, threshold: Float): String? = when {
        kotlin.math.max(kotlin.math.abs(x), kotlin.math.abs(y)) < threshold -> null
        kotlin.math.abs(x) > kotlin.math.abs(y) -> if (x > 0) "RIGHT" else "LEFT"
        else -> if (y > 0) "DOWN" else "UP"
    }
}

/** Phone-only presentation and lifecycle; SmartCast protocol remains in the shared core. */
internal class TvRemoteController(private val context: Context) : AutoCloseable {
    private val prefs = context.getSharedPreferences("viptv.remote", 0)
    private val tokens by lazy { SmartCastTokenStore(context) }
    private val lifetime = SupervisorJob()
    private val scope = CoroutineScope(lifetime + Dispatchers.Main.immediate)
    private val executor = Executors.newFixedThreadPool(8)
    private var generation = 0
    private val commands = Mutex()
    private var search: Job? = null
    private var client: SmartCastClient? = null
    private var target: RemoteTv? = null
    private var challenge: JSONObject? = null
    private var forgotten = mutableSetOf<String>()
    var selected by mutableStateOf(prefs.getString("origin", null)?.let { RemoteTv(prefs.getString("name", "Vizio TV")!!, it) }); private set
    var page by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set
    var online by mutableStateOf(false); private set
    var message by mutableStateOf(""); private set
    var results by mutableStateOf(emptyList<RemoteTv>()); private set
    var pin by mutableStateOf(""); private set
    var header by mutableStateOf(prefs.getBoolean("header", true)); private set
    var vibrate by mutableStateOf(prefs.getBoolean("vibrate", true)); private set
    var keepAwake by mutableStateOf(prefs.getBoolean("awake", true)); private set
    var swipe by mutableStateOf(prefs.getBoolean("swipe", false)); private set
    var tip by mutableStateOf(false); private set
    val tvName get() = (target ?: selected)?.name ?: "Vizio TV"
    val address get() = (target ?: selected)?.origin?.removePrefix("https://").orEmpty()
    val visible get() = selected != null && header

    fun preference(name: String, value: Boolean) {
        prefs.edit().putBoolean(name, value).apply()
        when (name) { "header" -> header = value; "vibrate" -> vibrate = value; "awake" -> keepAwake = value; "swipe" -> swipe = value }
    }
    fun dismissTip() { tip = false; prefs.edit().putBoolean("tipSeen", true).apply() }
    fun settings() { dismiss(); page = if (selected == null) "intro" else "settings" }
    fun manual() { stopSearch(); message = ""; page = "manual" }
    fun changeTv() { dismiss(); page = "intro" }
    fun confirmForget() { page = "forget" }
    fun cancelForget() { page = "settings" }
    fun back() {
        when (page) {
            "manual", "pin", "search", "access" -> { dismiss(); page = "intro" }
            "forget" -> page = "settings"
            else -> dismiss()
        }
    }
    private fun stopSearch() { search?.cancel(); search = null }
    fun dismiss() {
        generation++; stopSearch(); page = ""; message = ""; pin = ""; challenge = null; target = null
        // A running bridge owns its callback until completion; never destroy it underneath JNI.
        if (!busy) client?.close()
        client = null; busy = false
    }
    fun forget() {
        selected?.let { forgotten.add(it.origin); tokens.clear(it.origin) }
        prefs.edit().remove("origin").remove("name").remove("tipSeen").apply()
        selected = null; tip = false; online = false; dismiss()
    }
    fun open() {
        val tv = selected ?: return
        dismiss(); target = tv; page = "remote"; online = false
        command("pingAuth") { online = true }
    }
    fun retry() = open()
    fun repair() { selected?.let { connect(it.origin, it.name, forcePair = true) } }
    fun connect(value: String, name: String = "Vizio TV", forcePair: Boolean = false) {
        val origin = RemoteInput.origin(value)
        if (origin == null) { message = "Enter a local IPv4 address, optionally followed by :7345 or :9000."; return }
        dismiss(); target = RemoteTv(name, origin); page = "pin"; forgotten.remove(origin)
        if (forcePair || tokens.load(origin) == null) newPin()
        else command("pingAuth", failure = { newPin() }) { paired() }
    }
    fun newPin() {
        pin = ""; challenge = null
        command("beginPair") { result ->
            if (result.has("challengeType") && result.has("token")) challenge = result
            else message = "The TV did not return a PIN. Try again."
        }
    }
    fun enterPin(value: String) {
        if (busy || challenge == null) return
        pin = value.filter(Char::isDigit).take(4)
        if (pin.length == 4) {
            val fields = JSONObject(challenge!!.toString()).put("pin", pin)
            pin = ""
            command("finishPair", fields) { if (it.optBoolean("paired")) paired() else message = "Incorrect PIN. Try again." }
        }
    }
    private fun paired() {
        val tv = target ?: return
        val old = selected
        if (old != null && old.origin != tv.origin) tokens.clear(old.origin)
        selected = tv; online = true; page = "done"; challenge = null
        prefs.edit().putString("origin", tv.origin).putString("name", tv.name).apply()
        tip = !prefs.getBoolean("tipSeen", false)
    }
    fun key(key: String) { if (online) command("key", JSONObject().put("key", key)) {} }
    fun launchTv() {
        if (online) command("launchConjure", JSONObject().put("url", "https://watch.syek.tech/?platform=vizio")) {
            message = "The TV accepted the launch request. Check its screen to confirm VIPTV opened."
        }
    }
    private fun command(operation: String, input: JSONObject = JSONObject(), failure: (() -> Unit)? = null, done: (JSONObject) -> Unit) {
        if (busy) return
        val tv = target ?: selected ?: return
        val ticket = generation
        val active = client ?: SmartCastClient(tv.origin,
            prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("deviceId", it).apply() },
            "VIPTV phone", tokens, executor).also { client = it }
        busy = true; message = ""
        scope.launch { commands.withLock {
            if (ticket != generation) { active.close(); return@withLock }
            // Deliberately await the final callback even after navigation invalidates this ticket.
            val output = runCatching { suspendCoroutine<JSONObject> { continuation ->
                active.run(operation, input) { continuation.resume(it) }
            } }.getOrElse { JSONObject().put("kind", "error") }
            if (ticket != generation) {
                if (forgotten.contains(tv.origin) || (operation == "finishPair" && selected?.origin != tv.origin)) tokens.clear(tv.origin)
                active.close(); return@withLock
            }
            busy = false
            if (output.optString("kind") == "complete") done(output.optJSONObject("result") ?: JSONObject())
            else {
                val error = output.optJSONObject("error")
                if (error?.optString("kind") == "authentication" && failure != null) failure()
                else {
                    online = false
                    message = when {
                        operation == "finishPair" && error?.optString("kind") in listOf("authentication", "invalidParameter") -> "Incorrect PIN. Try again."
                        error?.optString("kind") == "authentication" -> "Pair this TV again to reconnect."
                        else -> error?.optString("message")?.takeIf { it.isNotBlank() } ?: "Can't reach your TV. Check the Wi-Fi connection and try again."
                    }
                }
            }
        } }
    }

    fun discover() {
        dismiss(); page = "search"; results = emptyList(); busy = true
        val ticket = generation
        search = scope.launch {
            try {
                withTimeout(12_000) {
                    val network = context.getSystemService(ConnectivityManager::class.java)
                    val lan = network.allNetworks.firstOrNull { network.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
                    val ip = lan?.let { network.getLinkProperties(it)?.linkAddresses?.firstOrNull { a -> a.address is Inet4Address }?.address?.hostAddress }
                    if (ip == null) { message = "Connect this phone to the same Wi-Fi as your TV."; return@withTimeout }
                    val prefix = ip.substringBeforeLast('.')
                    val candidates = JSONArray(vizioDiscoveryCandidates(prefix))
                    val permits = Semaphore(24)
                    withContext(Dispatchers.IO) {
                        coroutineScope {
                            launch {
                                val hosts = try { discoverSsdp(ip) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptySet() }
                                for (host in hosts) {
                                    ensureActive()
                                    val origin = RemoteInput.origin(host) ?: continue
                                    val tv = probe(origin) ?: continue
                                    withContext(Dispatchers.Main) {
                                        if (ticket == generation && results.none { URI(it.origin).host == URI(tv.origin).host }) results = (results + tv).take(16)
                                    }
                                }
                            }
                            for (i in 0 until candidates.length()) launch {
                                permits.withPermit {
                                    ensureActive()
                                    val host = candidates.getJSONObject(i).getString("host")
                                    val origin = RemoteInput.origin(host) ?: return@withPermit
                                    val tv = probe(origin) ?: return@withPermit
                                    withContext(Dispatchers.Main) {
                                        if (ticket == generation && results.none { URI(it.origin).host == URI(tv.origin).host }) results = (results + tv).take(16)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                if (ticket == generation) message = "Search finished. Enter the IP address if your TV isn't listed."
            } catch (_: CancellationException) { /* Navigation owns cancellation. */
            } catch (_: SecurityException) { if (ticket == generation) page = "access"
            } catch (_: Exception) { if (ticket == generation) message = "Could not search for TVs. Enter the IP address instead."
            } finally { if (ticket == generation) busy = false }
        }
    }
    private suspend fun discoverSsdp(ip: String): Set<String> {
        val found = mutableSetOf<String>()
        DatagramSocket(InetSocketAddress(ip, 0)).use { socket ->
            socket.soTimeout = 250
            val data = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: ssdp:all\r\n\r\n".toByteArray()
            socket.send(DatagramPacket(data, data.size, InetAddress.getByName("239.255.255.250"), 1900))
            val deadline = System.nanoTime() + 3_000_000_000L
            while (System.nanoTime() < deadline && found.size < 16) {
                currentCoroutineContext().ensureActive()
                val packet = DatagramPacket(ByteArray(4096), 4096)
                try { socket.receive(packet) } catch (_: SocketTimeoutException) { continue }
                val host = packet.address.hostAddress ?: continue
                if (host.startsWith(ip.substringBeforeLast('.') + ".") && String(packet.data, 0, packet.length).contains("vizio", ignoreCase = true)) found.add(host)
            }
        }
        return found
    }
    private suspend fun probe(origin: String): RemoteTv? = runCatching {
        val uri = URI(origin)
        Socket().use { it.connect(InetSocketAddress(uri.host, uri.port), 300) }
        val transport = SmartCastTransport(origin, executor)
        val request = JSONObject().put("url", "$origin/state/device/deviceinfo").put("method", "GET")
            .put("headers", JSONObject()).put("timeoutMillis", 1200).put("maxResponseBytes", 65536)
        val response = suspendCancellableCoroutine<SmartCastTransport.Response> { continuation ->
            val call = transport.execute(request) { result -> continuation.resumeWith(result) }
            continuation.invokeOnCancellation { call.cancel() }
        }
        if (response.status != 200) return@runCatching null
        vizioDeviceinfoName(response.body)?.let { RemoteTv(it, origin) }
    }.getOrNull()
    override fun close() { dismiss(); lifetime.invokeOnCompletion { executor.shutdown() }; lifetime.complete() }
}
