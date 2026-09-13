/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.input.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.BufferedInputStream
import java.io.Closeable
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.Collections
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Explicitly started, session-scoped LAN endpoint. Never logs input text or credentials. */
class CrossScreenServer(
    private val page: ByteArray,
    private val state: suspend () -> EditorState,
    private val sync: suspend (EditorEdit) -> EditorState?,
    listenPort: Int = 0
) : Closeable {
    private val json = Json { encodeDefaults = true }
    val pairingCode = SecureRandom().nextInt(10000).toString().padStart(4, '0')
    private var failedPairings = 0
    private var pairingBlockedUntil = 0L

    @Synchronized
    private fun authorize(code: String?): Int {
        val now = System.nanoTime()
        if (now < pairingBlockedUntil) return 429
        if (code == pairingCode) {
            failedPairings = 0
            return 200
        }
        if (++failedPairings >= 5) {
            pairingBlockedUntil = now + TimeUnit.SECONDS.toNanos(30)
            failedPairings = 0
        }
        return 403
    }
    private val server = ServerSocket().apply {
        reuseAddress = true
        try { bind(java.net.InetSocketAddress(listenPort)) }
        catch (e: Exception) { close(); throw e }
    }
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val workers = ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
        ArrayBlockingQueue<Runnable>(8))
    @Volatile private var closed = false
    @Volatile var lastClientActivity = 0L
        private set
    val port get() = server.localPort

    fun addresses(): List<String> = Collections.list(NetworkInterface.getNetworkInterfaces())
        .filter { it.isUp && !it.isLoopback }
        .flatMap { Collections.list(it.inetAddresses) }
        .filterIsInstance<Inet4Address>()
        .filter { it.isSiteLocalAddress }
        .map { "http://${it.hostAddress}:$port" }

    init {
        Thread({
            while (!closed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                clients.add(socket)
                try {
                    workers.execute {
                        socket.use {
                            try { handle(it) } catch (_: Exception) { /* malformed or disconnected client */ }
                            finally { clients.remove(it) }
                        }
                    }
                } catch (_: java.util.concurrent.RejectedExecutionException) {
                    clients.remove(socket)
                    socket.close()
                }
            }
        }, "cross-screen-listener").apply { isDaemon = true; start() }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 5000
        val input = BufferedInputStream(socket.getInputStream())
        var headerBytes = 0
        fun line(): String {
            val bytes = ArrayList<Byte>()
            while (true) {
                val c = input.read()
                require(c >= 0 && ++headerBytes <= 8192)
                if (c == 10) break
                if (c != 13) bytes.add(c.toByte())
            }
            return bytes.toByteArray().toString(Charsets.US_ASCII)
        }
        val request = line().split(' ')
        require(request.size == 3)
        val headers = mutableMapOf<String, String>()
        while (true) {
            val l = line()
            if (l.isEmpty()) break
            val i = l.indexOf(':')
            require(i > 0)
            val key = l.substring(0, i).lowercase()
            require(key !in headers)
            headers[key] = l.substring(i + 1).trim()
        }
        fun reply(code: Int, body: ByteArray, html: Boolean = false) {
            val head = "HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\n" +
                "Content-Type: ${if (html) "text/html" else "text/plain"}; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\nConnection: close\r\nCache-Control: no-store\r\n" +
                "X-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\n" +
                "Content-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; form-action 'none'\r\n\r\n"
            socket.getOutputStream().apply { write(head.toByteArray()); write(body); flush() }
        }
        fun reply(code: Int, text: String) = reply(code, text.toByteArray(Charsets.UTF_8))
        // Validate the local destination, preventing DNS rebinding and cross-origin browser access.
        val host = "${socket.localAddress.hostAddress}:$port"
        if (headers["host"] != host || headers["origin"]?.let { it != "http://$host" } == true) {
            reply(403, "Invalid origin"); return
        }
        if (request[0] == "GET" && request[1] == "/") {
            reply(200, page, true); return
        }
        val authorization = authorize(headers["x-pairing-code"])
        if (authorization != 200) {
            reply(authorization, if (authorization == 429) "尝试过多，请等待 30 秒后重试"
                else "配对码不正确或服务已重新开启")
            return
        }
        lastClientActivity = System.nanoTime()
        if (request[0] == "GET" && request[1] == "/state") {
            val value = runBlocking { withTimeout(3000) { state() } }
            reply(200, json.encodeToString(value)); return
        }
        if (request[0] != "POST" || request[1] != "/sync") {
            reply(404, "Not found"); return
        }
        val length = headers["content-length"]?.toIntOrNull()
        if ("transfer-encoding" in headers || length == null || length !in 1..262144) {
            reply(413, "文字过长，最多同步 32768 个字符"); return
        }
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val n = input.read(bytes, offset, length - offset)
            require(n > 0)
            offset += n
        }
        val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        val edit = runCatching { json.decodeFromString<EditorEdit>(text) }.getOrNull()
        if (edit == null || edit.text.length > 32768 || edit.selectionStart !in 0..edit.text.length ||
            edit.selectionEnd !in 0..edit.text.length) {
            reply(400, "无效的文字或选区"); return
        }
        val result = runBlocking { withTimeout(3000) { sync(edit) } }
        val latest = result ?: runBlocking { withTimeout(3000) { state() } }
        reply(if (result != null) 200 else 409, json.encodeToString(latest))
    }

    override fun close() {
        closed = true
        server.close()
        clients.forEach { runCatching { it.close() } }
        workers.shutdownNow()
    }
}
