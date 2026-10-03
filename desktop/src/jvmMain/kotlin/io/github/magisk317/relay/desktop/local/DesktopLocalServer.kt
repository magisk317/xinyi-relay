package io.github.magisk317.relay.desktop.local

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.magisk317.relay.contract.json.RelayJson
import io.github.magisk317.relay.contract.remote.AgentConfigCommandsAckRequest
import io.github.magisk317.relay.contract.remote.AgentConfigCommandsPullRequest
import io.github.magisk317.relay.contract.remote.AgentConfigCommandsPullResponse
import io.github.magisk317.relay.contract.remote.AgentConfigMirrorRequest
import io.github.magisk317.relay.contract.remote.AgentRegisterResponse
import io.github.magisk317.relay.contract.remote.DeviceConfigCommandResponse
import io.github.magisk317.relay.contract.remote.DeviceConfigStateResponse
import io.github.magisk317.relay.contract.remote.RelayRecordWire
import io.github.magisk317.relay.contract.remote.RelayRecordsBatchRequest
import io.github.magisk317.relay.contract.remote.RelayRecordsBatchResponse
import io.github.magisk317.relay.contract.remote.SystemInfoResponse
import io.github.magisk317.relay.desktop.core.model.DeviceConfigCommand
import io.github.magisk317.relay.desktop.core.model.DeviceConfigState
import io.github.magisk317.relay.desktop.core.model.Record
import io.github.magisk317.relay.desktop.core.store.DesktopLocalStore
import io.github.magisk317.relay.desktop.core.store.StoreError
import java.io.InputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put

/**
 * JVM implementation of the embedded Android-agent server.
 *
 * The Tauri implementation deliberately binds loopback only. Android can use
 * the resulting address through `adb reverse`; a future TLS/LAN design must be
 * an explicit change rather than silently exposing bearer-token endpoints on a
 * network interface. The server owns its executor and can therefore be stopped
 * when the local mirror is closed.
 */
class DesktopLocalServer(
    private val store: DesktopLocalStore,
    private val bindAddress: InetSocketAddress = InetSocketAddress("127.0.0.1", 0),
    private val serverFactory: (InetSocketAddress) -> HttpServer = { address ->
        HttpServer.create(address, 0)
    },
) : AutoCloseable {

    private var server: HttpServer? = null
    private var executor: ExecutorService? = null

    /** The listener address after [start], or null while stopped. */
    val address: InetSocketAddress?
        @Synchronized get() = server?.address

    /** Starts the server once and returns its bound address. */
    @Synchronized
    fun start(): InetSocketAddress {
        server?.let { return it.address }
        val next = serverFactory(bindAddress)
        val nextExecutor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "xinyi-relay-local-server").apply { isDaemon = true }
        }
        next.executor = nextExecutor
        next.createContext("/") { exchange -> handle(exchange) }
        next.start()
        server = next
        executor = nextExecutor
        return next.address
    }

    /** Stops the listener and interrupts in-flight handlers. */
    @Synchronized
    fun stop() {
        server?.stop(0)
        server = null
        executor?.shutdownNow()
        executor = null
    }

    override fun close() = stop()

    private fun handle(exchange: HttpExchange) {
        try {
            val response = route(exchange)
            sendJson(exchange, response.status, response.body)
        } catch (error: RequestError) {
            sendError(exchange, error.status, error.message)
        } catch (error: StoreError) {
            sendStoreError(exchange, error)
        } catch (error: SerializationException) {
            sendError(exchange, HTTP_BAD_REQUEST, "invalid JSON request")
        } catch (error: Exception) {
            sendError(exchange, HTTP_INTERNAL_SERVER_ERROR, error.message ?: "internal server error")
        } finally {
            exchange.close()
        }
    }

    private fun route(exchange: HttpExchange): Response {
        val path = exchange.requestURI.path
        return when (path) {
            "/healthz" -> requireMethod(exchange, "GET") { Response(HTTP_OK, "ok") }
            "/api/v1/system/info" -> requireMethod(exchange, "GET") {
                val info = runBlocking { store.getSystemInfo() }
                Response(HTTP_OK, RelayJson.encode(SystemInfoResponse.serializer(), info.toResponse()))
            }
            "/api/v1/agent/register" -> requireMethod(exchange, "POST") { register(exchange) }
            "/api/v1/agent/heartbeat" -> requireMethod(exchange, "POST") {
                val deviceId = authenticate(exchange)
                val body = bodyObject(exchange)
                runBlocking {
                    store.updateLocalDeviceHeartbeat(
                        deviceId = deviceId,
                        appVersion = body.string("appVersion"),
                        localAddresses = body["localAddresses"] ?: JsonObject(emptyMap()),
                        capabilities = body["capabilities"] ?: JsonObject(emptyMap()),
                    )
                }
                Response(HTTP_OK, "{\"ok\":true}")
            }
            "/api/v1/agent/config/mirror" -> requireMethod(exchange, "POST") {
                val deviceId = authenticate(exchange)
                val body = bodyObject(exchange)
                val request = decode<AgentConfigMirrorRequest>(body)
                val state = runBlocking {
                    store.upsertDeviceConfigMirror(
                        deviceId = deviceId,
                        revision = request.localRevision,
                        snapshot = request.mirrorContent,
                        updatedAt = null,
                    )
                }
                Response(
                    HTTP_OK,
                    RelayJson.encode(DeviceConfigStateResponse.serializer(), state.toResponse()),
                )
            }
            "/api/v1/agent/config/commands:pull" -> requireMethod(exchange, "POST") {
                val deviceId = authenticate(exchange)
                val body = bodyObject(exchange)
                val request = decode<AgentConfigCommandsPullRequest>(body)
                val state = runBlocking { store.getDeviceConfig(deviceId) }
                val mirrorContent = if (request.localRevision > 0 && request.localRevision >= state.revision) {
                    null
                } else {
                    state.snapshot.asObjectOrNull()
                }
                val response = AgentConfigCommandsPullResponse(
                    deviceId = deviceId,
                    revision = state.revision,
                    mirrorContent = mirrorContent,
                    pendingCommands = state.pendingCommands.map { it.toResponse() },
                    updatedAt = state.updatedAt.orEmpty(),
                )
                Response(HTTP_OK, RelayJson.encode(AgentConfigCommandsPullResponse.serializer(), response))
            }
            "/api/v1/agent/config/commands:ack" -> requireMethod(exchange, "POST") {
                val deviceId = authenticate(exchange)
                val body = bodyObject(exchange)
                val request = decode<AgentConfigCommandsAckRequest>(body)
                val command = runBlocking {
                    store.ackLocalDeviceConfigCommand(
                        deviceId = deviceId,
                        commandId = request.commandId,
                        status = request.status,
                        appliedRevision = request.appliedRevision,
                        failureReason = request.failureReason,
                        snapshot = request.mirrorContent,
                    )
                }
                Response(HTTP_OK, RelayJson.encode(DeviceConfigCommandResponse.serializer(), command.toResponse()))
            }
            "/api/v1/agent/records:batch" -> requireMethod(exchange, "POST") {
                val deviceId = authenticate(exchange)
                val body = bodyObject(exchange)
                val request = decode<RelayRecordsBatchRequest>(body)
                if (request.records.size > MAX_RECORDS_PER_BATCH) {
                    throw RequestError(HTTP_BAD_REQUEST, "too many records")
                }
                val records = request.records.map { it.toRecord(deviceId) }
                val result = runBlocking {
                    store.syncLocalDeviceRecords(deviceId, records, request.replaceExisting)
                }
                val response = RelayRecordsBatchResponse(
                    inserted = result.inserted.toLong(),
                    updated = result.updated.toLong(),
                    deleted = result.deleted.toLong(),
                )
                Response(HTTP_OK, RelayJson.encode(RelayRecordsBatchResponse.serializer(), response))
            }
            else -> Response(HTTP_NOT_FOUND, errorJson("not found"))
        }
    }

    private fun register(exchange: HttpExchange): Response {
        val body = bodyObject(exchange)
        val bindCode = body.string("bindCode").trim()
        val deviceName = body.string("deviceName").trim().sanitize(MAX_STRING_LEN)
        if (bindCode.isEmpty() || deviceName.isEmpty()) {
            throw RequestError(HTTP_BAD_REQUEST, "bindCode and deviceName are required")
        }
        val deviceToken = java.util.UUID.randomUUID().toString()
        val device = runBlocking {
            store.registerLocalDevice(
                bindCode = bindCode,
                deviceName = deviceName,
                deviceModel = body.string("deviceModel").sanitize(MAX_STRING_LEN),
                platform = body.string("platform", "android").sanitize(32),
                appVersion = body.string("appVersion").sanitize(64),
                deviceToken = deviceToken,
            )
        } ?: throw RequestError(HTTP_UNAUTHORIZED, "invalid or expired bind code")
        val response = AgentRegisterResponse(
            userId = device.userId,
            deviceId = device.id,
            deviceToken = deviceToken,
        )
        return Response(HTTP_CREATED, RelayJson.encode(AgentRegisterResponse.serializer(), response))
    }

    private fun authenticate(exchange: HttpExchange): Long {
        val header = exchange.requestHeaders.getFirst("Authorization")
        val token = header?.takeIf { it.startsWith("Bearer ") }
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw RequestError(HTTP_UNAUTHORIZED, "missing device token")
        return runBlocking { store.authenticateLocalDevice(token) }
            ?: throw RequestError(HTTP_UNAUTHORIZED, "invalid or revoked device token")
    }

    private fun bodyObject(exchange: HttpExchange): JsonObject {
        val raw = readBody(exchange)
        val element = runCatching { RelayJson.parseElement(raw) }
            .getOrElse { throw RequestError(HTTP_BAD_REQUEST, "invalid JSON request") }
        return element as? JsonObject ?: throw RequestError(HTTP_BAD_REQUEST, "request body must be a JSON object")
    }

    private fun readBody(exchange: HttpExchange): String {
        val bytes = exchange.requestBody.use { input: InputStream -> input.readNBytes(MAX_REQUEST_BODY_BYTES + 1) }
        if (bytes.size > MAX_REQUEST_BODY_BYTES) {
            throw RequestError(HTTP_REQUEST_TOO_LARGE, "request body too large")
        }
        return String(bytes, StandardCharsets.UTF_8)
    }

    private inline fun <reified T> decode(body: JsonObject): T =
        RelayJson.format.decodeFromJsonElement(body)

    private fun <T> requireMethod(exchange: HttpExchange, expected: String, block: () -> T): T {
        if (exchange.requestMethod != expected) {
            exchange.responseHeaders.add("Allow", expected)
            throw RequestError(HTTP_METHOD_NOT_ALLOWED, "method not allowed")
        }
        return block()
    }

    private fun sendJson(exchange: HttpExchange, status: Int, body: String?) {
        val payload = body?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
        if (body != null) exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(status, payload.size.toLong())
        exchange.responseBody.use { it.write(payload) }
    }

    private fun sendError(exchange: HttpExchange, status: Int, message: String) =
        sendJson(exchange, status, errorJson(message))

    private fun sendStoreError(exchange: HttpExchange, error: StoreError) {
        when (error) {
            is StoreError.Conflict -> sendJson(
                exchange,
                HTTP_CONFLICT,
                buildJsonObject {
                    put("error", "conflict")
                    put("local", error.local)
                    put("remote", error.remote)
                }.toString(),
            )
            is StoreError.Internal -> sendError(exchange, HTTP_INTERNAL_SERVER_ERROR, error.message ?: "internal server error")
        }
    }

    private data class Response(val status: Int, val body: String?)

    private class RequestError(val status: Int, override val message: String) : RuntimeException(message)

    private companion object {
        const val MAX_STRING_LEN = 512
        const val MAX_RECORDS_PER_BATCH = 200
        const val MAX_REQUEST_BODY_BYTES = 8 shl 20
        const val HTTP_OK = 200
        const val HTTP_CREATED = 201
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
        const val HTTP_METHOD_NOT_ALLOWED = 405
        const val HTTP_REQUEST_TOO_LARGE = 413
        const val HTTP_CONFLICT = 409
        const val HTTP_INTERNAL_SERVER_ERROR = 500

        fun errorJson(message: String): String = buildJsonObject { put("error", message) }.toString()

        fun String.sanitize(maxLength: Int): String = take(maxLength)

        fun JsonObject.string(name: String, default: String = ""): String =
            (get(name) as? JsonPrimitive)?.contentOrNull ?: default

        fun DeviceConfigState.toResponse(): DeviceConfigStateResponse = DeviceConfigStateResponse(
            deviceId = deviceId,
            revision = revision,
            mirrorContent = snapshot.asObjectOrNull(),
            pendingCommands = pendingCommands.map { it.toResponse() },
            updatedAt = updatedAt.orEmpty(),
        )

        fun DeviceConfigCommand.toResponse(): DeviceConfigCommandResponse = DeviceConfigCommandResponse(
            id = id,
            baseRevision = baseRevision,
            targetRevision = targetRevision,
            mutation = mutation.asObjectOrNull() ?: JsonObject(emptyMap()),
            summary = summary,
            actorType = actorType,
            actorId = actorId,
            status = status,
            failureReason = failureReason,
            createdAt = createdAt,
            updatedAt = updatedAt,
            appliedAt = appliedAt,
        )

        fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject

        fun io.github.magisk317.relay.desktop.core.model.SystemInfo.toResponse(): SystemInfoResponse =
            SystemInfoResponse(
                service = service,
                appEnv = appEnv,
                localBaseUrl = localBaseUrl,
                publicBaseUrl = publicBaseUrl,
                databaseReady = databaseReady,
                userCount = userCount,
                time = time,
            )

        fun RelayRecordWire.toRecord(deviceId: Long): Record = Record(
            id = 0,
            deviceId = deviceId,
            eventId = eventId.sanitize(128),
            recordType = recordType.sanitize(32),
            sender = sender.sanitize(MAX_STRING_LEN),
            body = body.sanitize(4096),
            smsCode = smsCode.sanitize(32),
            packageName = packageName.sanitize(256),
            metadata = metadata,
            msgType = msgType,
            callType = callType,
            occurredAt = occurredAt.sanitize(64),
            uploadedAt = "",
        )
    }
}
