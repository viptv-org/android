package com.getair.video

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.OutputStream
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal interface MpvCommandClient : AutoCloseable {
    val events: Flow<JsonObject>
    suspend fun command(arguments: List<JsonElement>): JsonElement?
}

internal data class MpvProcessOptions(
    val executable: String = "mpv",
    val extraArguments: List<String> = emptyList(),
    val headless: Boolean = true,
    val startupTimeoutMillis: Long = 8_000,
    val commandTimeoutMillis: Long = 8_000,
) {
    init {
        require(executable.isNotBlank())
        require(startupTimeoutMillis > 0)
        require(commandTimeoutMillis > 0)
    }

    override fun toString(): String =
        "MpvProcessOptions(executable=<redacted>, extraArguments=${extraArguments.size}, headless=$headless)"
}

internal class MpvIpcConnection private constructor(
    private val process: Process,
    private val socket: SocketChannel,
    private val tempDirectory: Path,
    private val socketPath: Path,
    private val commandTimeoutMillis: Long,
) : MpvCommandClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writerMutex = Mutex()
    private val writer: BufferedWriter = Channels.newWriter(socket, StandardCharsets.UTF_8).buffered()
    private val reader: BufferedReader = Channels.newReader(socket, StandardCharsets.UTF_8).buffered()
    private val requestIds = AtomicLong(0)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonElement?>>()
    private val eventFlow = MutableSharedFlow<JsonObject>(extraBufferCapacity = 256)
    @Volatile private var closed = false

    override val events: Flow<JsonObject> = eventFlow.asSharedFlow()

    init {
        scope.launch { process.inputStream.copyTo(OutputStream.nullOutputStream()) }
        scope.launch { readLoop() }
    }

    override suspend fun command(arguments: List<JsonElement>): JsonElement? {
        check(!closed) { "MPV transport is closed" }
        val requestId = requestIds.incrementAndGet()
        val response = CompletableDeferred<JsonElement?>()
        pending[requestId] = response
        val request = buildJsonObject {
            put("command", JsonArray(arguments))
            put("request_id", requestId)
        }
        try {
            writerMutex.withLock {
                withContext(Dispatchers.IO) {
                    writer.write(request.toString())
                    writer.newLine()
                    writer.flush()
                }
            }
            return withTimeout(commandTimeoutMillis) { response.await() }
        } finally {
            pending.remove(requestId)
        }
    }

    private fun readLoop() {
        try {
            while (!closed) {
                val line = reader.readLine() ?: break
                val message = runCatching { json.parseToJsonElement(line) as? JsonObject }.getOrNull() ?: continue
                val requestId = message["request_id"]?.jsonPrimitive?.longOrNull
                if (requestId != null) {
                    val deferred = pending.remove(requestId) ?: continue
                    val error = message["error"]?.jsonPrimitive?.content
                    if (error == null || error == "success") {
                        deferred.complete(message["data"])
                    } else {
                        deferred.completeExceptionally(MpvCommandException(error))
                    }
                } else if (message["event"] != null) {
                    eventFlow.tryEmit(message)
                }
            }
        } finally {
            if (!closed) close()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        pending.values.forEach { it.completeExceptionally(MpvCommandException("transport closed")) }
        pending.clear()
        runCatching {
            writer.write("{\"command\":[\"quit\"]}")
            writer.newLine()
            writer.flush()
        }
        runCatching { socket.close() }
        process.destroy()
        if (process.isAlive) runCatching { process.destroyForcibly() }
        scope.cancel()
        runCatching { Files.deleteIfExists(socketPath) }
        runCatching { Files.deleteIfExists(tempDirectory) }
    }

    companion object {
        suspend fun start(options: MpvProcessOptions = MpvProcessOptions()): MpvIpcConnection =
            withContext(Dispatchers.IO) {
                val tempDirectory = Files.createTempDirectory("air-mpv-")
                val socketPath = tempDirectory.resolve("ipc.sock")
                val command = buildList {
                    add(options.executable)
                    add("--no-config")
                    add("--idle=yes")
                    add("--terminal=no")
                    add("--msg-level=all=error")
                    add("--force-window=no")
                    add("--input-ipc-server=$socketPath")
                    if (options.headless) {
                        add("--vo=null")
                        add("--ao=null")
                    }
                    addAll(options.extraArguments)
                }
                val process = try {
                    ProcessBuilder(command).redirectErrorStream(true).start()
                } catch (error: Exception) {
                    Files.deleteIfExists(tempDirectory)
                    throw MpvCommandException("MPV could not be started", error)
                }
                try {
                    withTimeout(options.startupTimeoutMillis) {
                        while (!Files.exists(socketPath)) {
                            check(process.isAlive) { "MPV exited during startup" }
                            delay(20)
                        }
                    }
                    val socket = SocketChannel.open(StandardProtocolFamily.UNIX)
                    socket.connect(UnixDomainSocketAddress.of(socketPath))
                    MpvIpcConnection(
                        process = process,
                        socket = socket,
                        tempDirectory = tempDirectory,
                        socketPath = socketPath,
                        commandTimeoutMillis = options.commandTimeoutMillis,
                    )
                } catch (error: Throwable) {
                    process.destroyForcibly()
                    runCatching { Files.deleteIfExists(socketPath) }
                    runCatching { Files.deleteIfExists(tempDirectory) }
                    throw MpvCommandException("MPV IPC startup failed", error)
                }
            }
    }
}

internal class MpvCommandException(message: String, cause: Throwable? = null) :
    IllegalStateException("MPV command failed: $message", cause)

internal fun mpvString(value: String): JsonPrimitive = JsonPrimitive(value)
internal fun mpvLong(value: Long): JsonPrimitive = JsonPrimitive(value)
