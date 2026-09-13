/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.input.remote

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

class CrossScreenServerTest {
    private fun request(server: CrossScreenServer, method: String, path: String,
                        body: String = "", code: String = server.pairingCode,
                        target: String = "editor-1", extra: String = "", host: String = "127.0.0.1:${server.port}"): String {
        val bytes = body.toByteArray(Charsets.UTF_8)
        return Socket("127.0.0.1", server.port).use { socket ->
            socket.soTimeout = 5000
            socket.getOutputStream().apply {
                write(("$method $path HTTP/1.1\r\nHost: $host\r\n" +
                    "X-Pairing-Code: $code\r\nX-Input-Target: $target\r\n" +
                    "Content-Length: ${bytes.size}\r\n$extra\r\n").toByteArray())
                write(bytes)
                flush()
            }
            val input = socket.getInputStream().buffered()
            val header = StringBuilder()
            while (!header.endsWith("\r\n\r\n")) {
                val c = input.read()
                check(c >= 0)
                header.append(c.toChar())
            }
            val length = header.lines().first { it.startsWith("Content-Length:") }
                .substringAfter(':').trim().toInt()
            val responseBody = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(responseBody, read, length - read)
                check(n > 0)
                read += n
            }
            header.toString() + responseBody.toString(Charsets.UTF_8)
        }
    }


    @Test fun pairedClientSyncsTextSelectionAndClearWithoutReconnecting() {
        var current = EditorState(true, "", 0, 0, 1, "")
        CrossScreenServer("page".toByteArray(), { current }, { edit ->
            current = EditorState(true, edit.text, edit.selectionStart, edit.selectionEnd, current.revision + 1, "")
            current
        }).use { s ->
            assertTrue(request(s, "GET", "/", code = "").endsWith("page"))
            assertTrue(request(s, "GET", "/state").startsWith("HTTP/1.1 200"))
            val text = "中文输入 👋\n第二行"
            val edit = EditorEdit(text, 2, 4, current.revision)
            assertTrue(request(s, "POST", "/sync", Json.encodeToString(edit)).startsWith("HTTP/1.1 200"))
            assertEquals(text, current.text)
            assertEquals(2, current.selectionStart)
            assertEquals(4, current.selectionEnd)
            assertTrue(request(s, "POST", "/sync", Json.encodeToString(EditorEdit("", 0, 0, current.revision)))
                .startsWith("HTTP/1.1 200"))
            assertEquals("", current.text)
            // Device-side clear / field switch is read through the same authenticated connection.
            current = EditorState(true, "另一个输入框", 1, 1, 10, "")
            assertTrue(request(s, "GET", "/state").contains("另一个输入框"))
        }
    }

    @Test fun unpairedAndCrossOriginRequestsCannotEdit() {
        var edits = 0
        CrossScreenServer(byteArrayOf(), { EditorState() }, { edits++; EditorState() }).use { s ->
            assertTrue(request(s, "POST", "/sync", "x", code = "wrong").startsWith("HTTP/1.1 403"))
            assertTrue(request(s, "POST", "/sync", "x", extra = "Origin: https://evil.example\r\n").startsWith("HTTP/1.1 403"))
            assertTrue(request(s, "POST", "/sync", "x", host = "evil.example:${s.port}").startsWith("HTTP/1.1 403"))
            assertEquals(0, edits)
        }
    }

    @Test fun conflictsReturnLatestStateWithoutRePairing() {
        val current = EditorState(true, "Quest 修改", 3, 3, 7, "")
        CrossScreenServer(byteArrayOf(), { current }, { null }).use { s ->
            val response = request(s, "POST", "/sync", Json.encodeToString(EditorEdit("旧文字", 0, 0, 1)))
            assertTrue(response.startsWith("HTTP/1.1 409"))
            assertTrue(response.contains("Quest 修改"))
            assertTrue(request(s, "GET", "/state").startsWith("HTTP/1.1 200"))
        }
    }

    @Test fun invalidSelectionsAndOversizedBodiesAreRejected() {
        var edits = 0
        CrossScreenServer(byteArrayOf(), { EditorState() }, { edits++; EditorState() }).use { s ->
            assertTrue(request(s, "POST", "/sync", "x".repeat(262145)).startsWith("HTTP/1.1 413"))
            assertTrue(request(s, "POST", "/sync", Json.encodeToString(EditorEdit("a", 2, 2, 1)))
                .startsWith("HTTP/1.1 400"))
            assertEquals(0, edits)
        }
    }

    @Test fun repeatedWrongCodesAreRateLimited() {
        CrossScreenServer(byteArrayOf(), { EditorState() }, { null }).use { s ->
            repeat(5) { assertTrue(request(s, "GET", "/state", code = "invalid").startsWith("HTTP/1.1 403")) }
            assertTrue(request(s, "GET", "/state", code = "invalid").startsWith("HTTP/1.1 429"))
        }
    }

    @Test fun unreadableEditorAcceptsOneWayInputAndBackspace() {
        val state = EditorState(available = true, readable = false, revision = 9)
        val actions = mutableListOf<EditorEdit>()
        CrossScreenServer(byteArrayOf(), { state }, { if (it.canApplyTo(state)) { actions.add(it); state } else null }).use { s ->
            val input = EditorEdit("你好", 0, 0, 1, oneWay = true)
            assertTrue(request(s, "POST", "/sync", Json.encodeToString(input)).startsWith("HTTP/1.1 200"))
            val delete = EditorEdit("", 0, 0, 1, oneWay = true, backspace = true)
            assertTrue(request(s, "POST", "/sync", Json.encodeToString(delete)).startsWith("HTTP/1.1 200"))
            assertEquals(listOf(input, delete), actions)
            val response = request(s, "GET", "/state")
            assertTrue(response.contains("\"readable\":false"))
            assertFalse(response.contains("你好"))
        }
    }

    @Test fun requestedPortCanBeReusedAfterClosing() {
        val first = CrossScreenServer(byteArrayOf(), { EditorState() }, { null })
        val port = first.port
        request(first, "GET", "/state")
        first.close()
        CrossScreenServer(byteArrayOf(), { EditorState() }, { null }, listenPort = port).use { second ->
            assertEquals(port, second.port)
            assertTrue(request(second, "GET", "/state").startsWith("HTTP/1.1 200"))
            assertTrue(runCatching {
                CrossScreenServer(byteArrayOf(), { EditorState() }, { null }, listenPort = port).close()
            }.exceptionOrNull() is java.net.BindException)
        }
    }

    @Test fun closingRevokesListenerAndPairingCodeHasFourDigits() {
        val server = CrossScreenServer(byteArrayOf(), { EditorState() }, { null })
        val port = server.port
        assertTrue(server.pairingCode.matches(Regex("[0-9]{4}")))
        server.close()
        assertTrue(runCatching { Socket("127.0.0.1", port).close() }.isFailure)
    }
}
