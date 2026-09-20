package org.viptv.app

import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.Collections
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Minimal in-process HTTP fixture speaking the backend's JSON wire shape. */
internal data class FixtureRequest(val method: String, val target: String, val body: String)
internal data class FixtureResponse(val body: String, val status: Int = 200)
internal class FixtureServer(
    private val expectedRequests: Int,
    private val respond: (FixtureRequest) -> FixtureResponse,
) : AutoCloseable {
    private val socket = ServerSocket(0)
    private val workerFailure = Collections.synchronizedList(mutableListOf<Throwable>())
    val requests = Collections.synchronizedList(mutableListOf<FixtureRequest>())
    val origin = "http://127.0.0.1:${socket.localPort}"
    private val worker = thread(name = "viptv-gateway-wire", isDaemon = true) {
        try {
            repeat(expectedRequests) {
                socket.accept().use(::handle)
            }
        } catch (error: Throwable) {
            if (!socket.isClosed) workerFailure += error
        }
    }

    private fun handle(connection: java.net.Socket) {
        val input = connection.getInputStream().bufferedReader(StandardCharsets.UTF_8)
        val requestLine = input.readLine() ?: error("Missing request line")
        val parts = requestLine.split(' ', limit = 3)
        require(parts.size >= 2) { "Malformed request line: $requestLine" }
        var contentLength = 0
        while (true) {
            val line = input.readLine() ?: error("Unexpected end of headers")
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0 && line.substring(0, separator).equals("Content-Length", ignoreCase = true)) {
                contentLength = line.substring(separator + 1).trim().toInt()
            }
        }
        val body = CharArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val count = input.read(body, read, contentLength - read)
            if (count < 0) error("Unexpected end of request body")
            read += count
        }
        val request = FixtureRequest(parts[0], parts[1], body.concatToString())
        requests += request
        val response = respond(request)
        val bytes = response.body.toByteArray(StandardCharsets.UTF_8)
        connection.getOutputStream().buffered().use { output ->
            output.write("HTTP/1.1 ${response.status} OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
            output.write(bytes)
        }
    }

    fun assertHealthy() {
        assertEquals(expectedRequests, requests.size)
        assertTrue(workerFailure.isEmpty(), workerFailure.joinToString("\n") { it.stackTraceToString() })
    }

    override fun close() {
        socket.close()
        worker.join(1_000)
        assertHealthy()
    }
}
