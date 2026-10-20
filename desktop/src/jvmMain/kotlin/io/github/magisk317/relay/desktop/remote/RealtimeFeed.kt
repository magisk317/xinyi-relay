package io.github.magisk317.relay.desktop.remote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.magisk317.relay.contract.remote.RealtimeEvent
import io.github.magisk317.relay.desktop.platform.DesktopLog
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
    /** Whether the active profile accepts a self-signed console certificate. */
    private val allowSelfSignedProvider: () -> Boolean = { false },
) {
    private val json = Json { ignoreUnknownKeys = true }

    // The feed outlives profile switches, so the client is rebuilt when the
    // TLS override changes instead of pinning the flag read at construction.
    private var clientAllowSelfSigned: Boolean? = null
    private var client: OkHttpClient? = null

    private fun currentClient(): OkHttpClient {
        val allowSelfSigned = allowSelfSignedProvider()
        client?.let { if (clientAllowSelfSigned == allowSelfSigned) return it }
        return SelfSignedTls.newBuilder(allowSelfSigned)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
            .also {
                client = it
                clientAllowSelfSigned = allowSelfSigned
            }
    }

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
        socket = currentClient().newWebSocket(builder.build(), listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            connected = true
            startPing()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            lastEvent = runCatching { json.decodeFromString<RealtimeEvent>(text) }
                .onFailure {
                    DesktopLog.warn(
                        key = "realtime.decode",
                        message = "dropped undecodable realtime event",
                        fields = mapOf("error" to (it::class.simpleName ?: "Throwable")),
                    )
                }
                .getOrNull()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            connected = false
            DesktopLog.warn(
                key = "realtime.onFailure",
                message = "realtime websocket failed; scheduling reconnect",
                fields = buildMap {
                    put("error", t::class.simpleName ?: "Throwable")
                    response?.let { put("httpCode", it.code.toString()) }
                },
            )
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

/** Realtime events that invalidate the overview and analytics pages. */
internal val SUMMARY_PAGE_EVENTS = setOf(
    "device.registered",
    "device.updated",
    "device.heartbeat",
    "device.revoked",
    "device.config.updated",
    "device.config.command.updated",
    "records.ingested",
)

/**
 * True when [eventType] refreshes the overview and analytics pages, mirroring
 * the webUI filter: those two pages react to device and record mutations only.
 */
fun shouldRefreshSummaryOn(eventType: String?): Boolean = eventType in SUMMARY_PAGE_EVENTS
