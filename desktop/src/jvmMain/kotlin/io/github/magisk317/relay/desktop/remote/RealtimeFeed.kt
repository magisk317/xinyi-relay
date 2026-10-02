package io.github.magisk317.relay.desktop.remote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.magisk317.relay.contract.remote.RealtimeEvent
import io.github.magisk317.relay.net.RelayHttpClients
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Console realtime feed mirroring the webUI RealtimeProvider: a bearer
 * authenticated WebSocket on /api/v1/realtime/ws, a ping every 25s and a
 * reconnect 2s after the socket drops.
 */
class DesktopRealtimeFeed(
    private val baseUrlProvider: () -> String?,
    private val tokenProvider: () -> String?,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val client: OkHttpClient = RelayHttpClients.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var connected by mutableStateOf(false)
        private set

    var lastEvent by mutableStateOf<RealtimeEvent?>(null)
        private set

    private var socket: WebSocket? = null
    private var stopped = true
    private var pingJob: Job? = null

    fun start() {
        if (!stopped) return
        stopped = false
        connect()
    }

    fun stop() {
        stopped = true
        pingJob?.cancel()
        pingJob = null
        socket?.close(NORMAL_CLOSURE, "shutdown")
        socket = null
    }

    private fun connect() {
        val baseUrl = baseUrlProvider()?.trimEnd('/') ?: return
        val wsUrl = when {
            baseUrl.startsWith("https://") -> "wss://" + baseUrl.removePrefix("https://")
            baseUrl.startsWith("http://") -> "ws://" + baseUrl.removePrefix("http://")
            else -> return
        } + REALTIME_PATH
        val builder = Request.Builder().url(wsUrl)
        tokenProvider()?.let { builder.header("Authorization", "Bearer $it") }
        socket = client.newWebSocket(builder.build(), listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            connected = true
            startPing()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            lastEvent = runCatching { json.decodeFromString<RealtimeEvent>(text) }.getOrNull()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            connected = false
            scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            connected = false
            if (!stopped) scheduleReconnect()
        }
    }

    private fun startPing() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                socket?.send("ping")
            }
        }
    }

    private fun scheduleReconnect() {
        pingJob?.cancel()
        scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (!stopped) connect()
        }
    }

    private companion object {
        const val REALTIME_PATH = "/api/v1/realtime/ws"
        const val PING_INTERVAL_MS = 25_000L
        const val RECONNECT_DELAY_MS = 2_000L
        const val NORMAL_CLOSURE = 1000
    }
}
