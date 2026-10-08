package io.github.magisk317.relay.desktop.auth

import io.github.magisk317.relay.contract.remote.DesktopSessionResponse
import io.github.magisk317.relay.desktop.remote.ConsoleClient
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
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
                server.accept().use { socket -> handle(socket) }
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
        val query = target.substringAfter('?', "")
        var code: String? = null
        var state: String? = null
        var error: String? = null
        for (pair in query.split('&')) {
            val (k, v) = pair.substringBefore('=') to pair.substringAfter('=', "")
            when (k) {
                "code" -> code = v
                "state" -> state = v
                "error" -> error = v
            }
        }
        val html = "<html><body><h3>Xinyi Relay Desktop</h3><p>Auth finished. Close this tab.</p></body></html>"
        val bytes = html.toByteArray()
        val out = socket.getOutputStream()
        out.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray(),
        )
        out.write(bytes)
        out.flush()
        result.complete(AuthCallbackPayload(code, state, error))
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
}
