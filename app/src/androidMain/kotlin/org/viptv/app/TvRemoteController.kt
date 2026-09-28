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
    fun discoveryCandidates(prefix: String): JSONArray = JSONObject(vizioDiscoveryCandidates(prefix)).getJSONArray("Ok")
    fun parent(page: String, pairingReturn: String, paired: Boolean): String = when (page) {
        "manual" -> "search"
        "pin" -> pairingReturn
        "search", "access" -> "intro"
        "intro" -> if (paired) "settings" else ""
        "forget" -> "settings"
        else -> ""
    }
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
    @Volatile private var generation = 0
    @Volatile private var keyEpoch = 0
    private var pendingKeys = 0
    private val commands = Mutex()
    private var search: Job? = null
    private var client: SmartCastClient? = null
    private var clientOrigin: String? = null
    private var pairingPending = false
    private var resumeSearch = false
    private var reconnectPending = false
    private var nameJob: Job? = null
    private var target: RemoteTv? = null
    private var challenge: JSONObject? = null
    private var pairingReturn = "search"
    var manualAddress by mutableStateOf("")
    var foreground by mutableStateOf(true); private set
    var selected by mutableStateOf(prefs.getString("origin", null)?.let { RemoteTv(prefs.getString("name", "Vizio TV")!!, it) }); private set
    var page by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set
    var online by mutableStateOf(false); private set
    var checking by mutableStateOf(false); private set
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
    fun manual() { dismiss(); page = "manual" }
    fun changeTv() { dismiss(); page = "intro" }
    fun confirmForget() { page = "forget" }
    fun cancelForget() { page = "settings" }
    fun back() {
        val previous = RemoteInput.parent(page, pairingReturn, selected != null)
        dismiss()
        if (previous == "remote") open() else page = previous
    }
    private fun stopSearch() { search?.cancel(); search = null }
    fun onBackground() {
        foreground = false
        keyEpoch++; pendingKeys = 0
        resumeSearch = page == "search"
        reconnectPending = page == "remote"
        stopSearch()
        nameJob?.cancel()
        // The ViewModel retains the page and challenge; backgrounding isn't Cancel.
    }
    fun onForeground() {
        foreground = true
        if (resumeSearch && page == "search") { resumeSearch = false; discover() }
        else if (page == "remote") { reconnectPending = true; reconnect() }
    }
    fun dismiss() {
        keyEpoch++; pendingKeys = 0; resumeSearch = false
        generation++; stopSearch(); page = ""; message = ""; pin = ""; challenge = null; target = null
        busy = false; checking = false; reconnectPending = false; nameJob?.cancel()
        // Retire only after the active callback, including a late pairing challenge.
        scope.launch { commands.withLock { withContext(Dispatchers.IO) { releaseSession() } } }
    }
    fun forget() {
        val origin = selected?.origin
        prefs.edit().remove("origin").remove("name").remove("tipSeen").apply()
        selected = null; tip = false; online = false; dismiss()
        if (origin != null) scope.launch { commands.withLock { withContext(Dispatchers.IO) { tokens.clear(origin) } } }
    }
    fun open() {
        val tv = selected ?: return
        dismiss(); target = tv; page = "remote"
        reconnect()
    }
    fun retry() = reconnect()
    private fun reconnect() {
        if (checking || busy || selected == null || !foreground || page != "remote") return
        reconnectPending = false
        checking = true
        command("pingAuth", silent = true) { online = true; message = ""; refreshName() }
    }
    private fun refreshName() {
        val tv = target ?: selected ?: return
        val ticket = generation
        nameJob?.cancel()
        nameJob = scope.launch {
            val found = withContext(Dispatchers.IO) { probe(tv.origin) } ?: return@launch
            if (ticket != generation || !foreground || selected?.origin != tv.origin) return@launch
            selected = found; target = found
            prefs.edit().putString("name", found.name).apply()
        }
    }
    fun repair() { selected?.let { connect(it.origin, it.name, forcePair = true) } }
    fun connect(value: String, name: String = "Vizio TV", forcePair: Boolean = false) {
        val origin = RemoteInput.origin(value)
        if (origin == null) { message = "Enter a local IPv4 address, optionally followed by :7345 or :9000."; return }
        pairingReturn = when (page) { "manual" -> "manual"; "remote" -> "remote"; else -> "search" }
        dismiss(); target = RemoteTv(name, origin); page = "pin"
        if (forcePair) newPin()
        else command("pingAuth", failure = { beginPair(false) }) { paired() }
    }
    fun newPin() = beginPair(true)
    private fun beginPair(restart: Boolean) {
        if (busy) return
        pin = ""; challenge = null
        command("beginPair", restartPair = restart) { result ->
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
        if (old != null && old.origin != tv.origin) scope.launch(Dispatchers.IO) { tokens.clear(old.origin) }
        selected = tv; online = true; page = "done"; challenge = null
        prefs.edit().putString("origin", tv.origin).putString("name", tv.name).apply()
        tip = !prefs.getBoolean("tipSeen", false)
        refreshName()
    }
    fun key(key: String): Boolean {
        if (!online || !foreground || page != "remote" || pendingKeys >= 8) return false
        pendingKeys++
        command("key", JSONObject().put("key", key), control = true) {}
        return true
    }
    fun power() {
        if (!foreground || page != "remote" || busy || checking) return
        command("powerToggle") { message = "Power request sent." }
    }
    fun mute() {
        if (!online || !foreground || page != "remote" || busy) return
        command("muteToggle") { message = "Mute request sent." }
    }
    fun launchTv() {
        if (online) command("launchConjure", JSONObject().put("url", "https://watch.syek.tech/?platform=vizio")) {
            message = "The TV accepted the launch request. Check its screen to confirm VIPTV opened."
        }
    }
    private fun command(operation: String, input: JSONObject = JSONObject(), failure: (() -> Unit)? = null,
        control: Boolean = false, restartPair: Boolean = false, silent: Boolean = false, done: (JSONObject) -> Unit) {
        if (!control && busy) return
        val tv = target ?: selected ?: return
        val ticket = generation
        val epoch = keyEpoch
        val alreadyPaired = selected?.origin == tv.origin
        if (!control && !silent) { busy = true; message = "" }
        scope.launch {
            try {
                val output = commands.withLock {
                    withContext(Dispatchers.IO) {
                        if (ticket != generation || (control && epoch != keyEpoch)) return@withContext null
                        try {
                            if (clientOrigin != tv.origin) releaseSession()
                            val active = client ?: SmartCastClient(tv.origin,
                                prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("deviceId", it).commit() },
                                "VIPTV phone", tokens, executor).also { client = it; clientOrigin = tv.origin }
                            if (operation == "beginPair") {
                                if (restartPair || pairingPending || prefs.getString("pendingPair", null) == tv.origin) {
                                    val cancelled = request(active, "cancelPair")
                                    if (cancelled.optString("kind") != "complete") return@withContext cancelled
                                }
                                pairingPending = true
                                prefs.edit().putString("pendingPair", tv.origin).commit()
                            }
                            var result = request(active, operation, input)
                            if (silent && result.optJSONObject("error")?.optString("kind") in listOf("transport", "httpStatus") && ticket == generation && epoch == keyEpoch) {
                                delay(350)
                                if (ticket == generation && epoch == keyEpoch) result = request(active, operation, input)
                            }
                            if ((operation == "finishPair" && result.optString("kind") == "complete") ||
                                (operation == "beginPair" && result.optString("kind") == "error" && result.optJSONObject("error")?.optString("kind") != "transport")) {
                                pairingPending = false
                                prefs.edit().remove("pendingPair").commit()
                            }
                            if (ticket != generation && operation == "finishPair" && !alreadyPaired) tokens.clear(tv.origin)
                            result
                        } catch (_: Exception) { JSONObject().put("kind", "error").put("error", JSONObject().put("kind", "transport")) }
                    }
                } ?: return@launch
                if (ticket != generation || ((control || silent) && epoch != keyEpoch)) return@launch
                if (!control && !silent) busy = false
                if (output.optString("kind") == "complete") done(output.optJSONObject("result") ?: JSONObject())
                else {
                    val error = output.optJSONObject("error")
                    if (error?.optString("kind") == "authentication" && failure != null) failure()
                    else {
                        if (error?.optString("kind") in listOf("authentication", "transport", "httpStatus")) {
                            online = false; keyEpoch++; pendingKeys = 0
                        }
                        message = when {
                            operation == "finishPair" && error?.optString("kind") in listOf("authentication", "invalidParameter") -> "Incorrect PIN. Try again."
                            error?.optString("kind") == "authentication" -> "Pair this TV again to reconnect."
                            operation == "beginPair" && error?.optString("kind") == "busy" -> "A pairing request is still active. Choose New PIN to restart this phone's pairing."
                            else -> error?.optString("message")?.takeIf { it.isNotBlank() } ?: "Can't reach your TV. Check the Wi-Fi connection and try again."
                        }
                    }
                }
            } finally {
                if (ticket == generation && control && epoch == keyEpoch) pendingKeys--
                if (ticket == generation && silent) {
                    checking = false
                }
                // Only a lifecycle request schedules another check. A failed ping
                // invalidates queued keys too, but must never create a retry loop.
                if (ticket == generation && reconnectPending && foreground && page == "remote") reconnect()
            }
        }
    }
    private suspend fun request(active: SmartCastClient, operation: String, input: JSONObject = JSONObject()): JSONObject =
        suspendCoroutine { continuation -> active.run(operation, input) { continuation.resume(it) } }

    /** Called only under commands on IO, after the preceding request has finished. */
    private suspend fun releaseSession() {
        val active = client ?: return
        try {
            if (pairingPending) {
                val result = request(active, "cancelPair")
                if (result.optString("kind") == "complete") prefs.edit().remove("pendingPair").commit()
            }
        } catch (_: Exception) { /* Keep the pending-origin marker for explicit retry. */
        } finally {
            active.close(); client = null; clientOrigin = null; pairingPending = false
        }
    }

    fun discover() {
        dismiss(); page = "search"; results = emptyList(); busy = true
        val ticket = generation
        search = scope.launch {
            try {
                withTimeout(30_000) {
                    val ip = withContext(Dispatchers.IO) {
                        val network = context.getSystemService(ConnectivityManager::class.java)
                        val lan = network.allNetworks.firstOrNull { network.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
                        lan?.let { network.getLinkProperties(it)?.linkAddresses?.firstOrNull { a -> a.address is Inet4Address }?.address?.hostAddress }
                    }
                    if (ip == null) { message = "Connect this phone to the same Wi-Fi as your TV."; return@withTimeout }
                    val prefix = ip.substringBeforeLast('.')
                    val candidates = withContext(Dispatchers.IO) { RemoteInput.discoveryCandidates(prefix) }
                    val permits = Semaphore(32)
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
        Socket().use { it.connect(InetSocketAddress(uri.host, uri.port), 1500) }
        val transport = SmartCastTransport(origin, executor)
        val request = JSONObject().put("url", "$origin/state/device/deviceinfo").put("method", "GET")
            .put("headers", JSONObject()).put("timeoutMillis", 2500).put("maxResponseBytes", 65536)
        val response = suspendCancellableCoroutine<SmartCastTransport.Response> { continuation ->
            val call = transport.execute(request) { result -> continuation.resumeWith(result) }
            continuation.invokeOnCancellation { call.cancel() }
        }
        if (response.status != 200) return@runCatching null
        vizioDeviceinfoName(response.body)?.let { RemoteTv(it, origin) }
    }.getOrNull()
    override fun close() { dismiss(); lifetime.invokeOnCompletion { executor.shutdown() }; lifetime.complete() }
}
