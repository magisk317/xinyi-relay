package io.github.magisk317.relay.desktop.auth

import io.github.magisk317.relay.contract.remote.DesktopSessionResponse
import io.github.magisk317.relay.desktop.remote.ConsoleClient
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.concurrent.thread
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

data class AuthCallbackPayload(
    val code: String?,
    val state: String?,
    val error: String?,
)

/**
 * One-shot browser handoff, mirroring the Rust desktop_start_browser_login /
 * desktop_exchange_browser_login pair: bind a loopback port, hand the backend
 * a start URL carrying that callback plus an anti-CSRF state, accept exactly
 * one GET /callback?code&state, then exchange the code for a session.
 */
class DesktopAuthFlow(
    private val clientName: String = "Xinyi Relay Desktop",
) {
    val state: String = UUID.randomUUID().toString()
    private val server = ServerSocket(0)
    val callbackUrl: String = "http://127.0.0.1:${server.localPort}/callback"
    private val result = CompletableDeferred<AuthCallbackPayload>()
    private var listener: Thread? = null

    fun authUrl(baseUrl: String): String =
        ConsoleClient(baseUrl).desktopStartUrl(callbackUrl, state, clientName)

    fun start() {
        if (listener != null) return
        listener = thread(name = "desktop-auth-callback", isDaemon = true) {
            try {
                server.soTimeout = 0
                while (!result.isCompleted) {
                    server.accept().use { socket -> handle(socket) }
                }
            } catch (_: Exception) {
                if (!result.isCompleted) {
                    result.complete(AuthCallbackPayload(null, null, "callback listener closed"))
                }
            }
        }
    }

    private fun handle(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.inputStream))
        val requestLine = reader.readLine().orEmpty()
        val target = requestLine.split(" ").getOrNull(1).orEmpty()
        // Anything on this loopback port that is not the callback path is
        // refused, so a stray request cannot complete the handoff.
        val uri = runCatching { URI(target) }.getOrNull()
        val path = uri?.path ?: target.substringBefore('?')
        if (path != CALLBACK_PATH) {
            respond(socket, "404 Not Found", NOT_FOUND_HTML)
            return
        }
        val query = uri?.rawQuery ?: target.substringAfter('?', "")
        var code: String? = null
        var state: String? = null
        var error: String? = null
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val key = pair.substringBefore('=')
            val value = decode(pair.substringAfter('=', ""))
            when (key) {
                "code" -> code = value
                "state" -> state = value
                "error" -> error = value
            }
        }
        respond(socket, "200 OK", SUCCESS_HTML)
        result.complete(AuthCallbackPayload(code, state, error))
    }

    /** Percent-decodes a query value, leaving it untouched when malformed. */
    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8) }.getOrDefault(value)

    private fun respond(socket: Socket, status: String, html: String) {
        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        val out = socket.getOutputStream()
        out.write(
            ("HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray(),
        )
        out.write(bytes)
        out.flush()
    }

    suspend fun awaitCallback(timeoutMillis: Long = 300_000): AuthCallbackPayload =
        withTimeout(timeoutMillis) { result.await() }

    suspend fun completeLogin(client: ConsoleClient, timeoutMillis: Long = 300_000): DesktopSessionResponse {
        val payload = awaitCallback(timeoutMillis)
        payload.error?.let { throw IllegalStateException("desktop auth failed: $it") }
        val code = payload.code ?: throw IllegalStateException("desktop auth callback without code")
        if (payload.state != state) throw IllegalStateException("desktop auth state mismatch")
        return client.desktopExchange(code)
    }

    fun openBrowser(url: String): Boolean =
        runCatching { java.awt.Desktop.getDesktop().browse(URI(url)) }.isSuccess

    fun close() {
        runCatching { server.close() }
        listener?.interrupt()
        listener = null
    }

    private companion object {
        const val CALLBACK_PATH = "/callback"
        const val SUCCESS_HTML =
            "<html><body><h3>Xinyi Relay Desktop</h3><p>Auth finished. Close this tab.</p></body></html>"
        const val NOT_FOUND_HTML =
            "<html><body><h3>Xinyi Relay Desktop</h3><p>Not found.</p></body></html>"
    }
}
