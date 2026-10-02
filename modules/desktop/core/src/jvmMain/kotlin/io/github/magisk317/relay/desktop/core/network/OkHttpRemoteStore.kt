package io.github.magisk317.relay.desktop.core.network

import io.github.magisk317.relay.desktop.core.model.BindCode
import io.github.magisk317.relay.desktop.core.model.Device
import io.github.magisk317.relay.desktop.core.model.DeviceConfigAuditLog
import io.github.magisk317.relay.desktop.core.model.DeviceConfigCommand
import io.github.magisk317.relay.desktop.core.model.DeviceConfigState
import io.github.magisk317.relay.desktop.core.model.Paginated
import io.github.magisk317.relay.desktop.core.model.Record
import io.github.magisk317.relay.desktop.core.model.SystemInfo
import io.github.magisk317.relay.desktop.core.store.RemoteStore
import io.github.magisk317.relay.desktop.core.store.StoreError
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
private data class ErrorPayload(val error: String? = null)

@Serializable
private data class DevicesResponse(val devices: List<Device> = emptyList())

@Serializable
private data class RecordsResponse(
    val records: List<Record> = emptyList(),
    val limit: Int = 0,
    val offset: Int = 0,
)

@Serializable
private data class AuditLogsResponse(
    val logs: List<DeviceConfigAuditLog> = emptyList(),
    val limit: Int = 0,
    val offset: Int = 0,
)

@Serializable
private data class BindCodeResponse(
    val code: String,
    @SerialName("expires_at")
    val expiresAt: String,
)

/**
 * HTTP implementation of the backend store.
 *
 * Endpoint paths, request bodies and the 409 handling are a direct port of
 * `remote_store.rs`; the desktop client remains a consumer for devices and
 * records, which are authoritative on the backend.
 */
class OkHttpRemoteStore(
    private val baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    },
) : RemoteStore {

    @Volatile
    private var accessToken: String? = null

    override fun setAccessToken(token: String?) {
        accessToken = token
    }

    override suspend fun getDeviceConfig(deviceId: Long): DeviceConfigState? =
        json.get("/api/v1/devices/$deviceId/config", auth = true)

    override suspend fun queueDeviceConfigCommand(
        deviceId: Long,
        baseRevision: Long,
        summary: String,
        mutation: JsonElement,
    ): DeviceConfigCommand {
        val body = buildJsonObject {
            put("baseRevision", JsonPrimitive(baseRevision))
            put("summary", JsonPrimitive(summary))
            put("mutation", mutation)
        }
        return try {
            json.post("/api/v1/devices/$deviceId/config/commands", body, auth = true)
        } catch (error: StoreError.Conflict) {
            val remoteRevision = getDeviceConfig(deviceId)?.revision ?: 0L
            throw StoreError.Conflict(local = baseRevision, remote = remoteRevision)
        }
    }

    override suspend fun listDeviceConfigAuditLogs(
        deviceId: Long,
        limit: Int,
        offset: Int,
    ): Paginated<DeviceConfigAuditLog> {
        val response: AuditLogsResponse =
            json.get("/api/v1/devices/$deviceId/config/audit?limit=$limit&offset=$offset", auth = true)
        return Paginated(items = response.logs, limit = response.limit, offset = response.offset)
    }

    override suspend fun listDevices(): List<Device> {
        val response: DevicesResponse = json.get("/api/v1/devices", auth = true)
        return response.devices
    }

    override suspend fun patchDevice(
        deviceId: Long,
        displayName: String?,
        enabled: Boolean?,
    ): JsonElement {
        val body = buildJsonObject {
            if (displayName != null) put("displayName", JsonPrimitive(displayName))
            if (enabled != null) put("enabled", JsonPrimitive(enabled))
        }
        return json.patch("/api/v1/devices/$deviceId", body, auth = true)
    }

    override suspend fun revokeDevice(deviceId: Long): JsonElement =
        json.post("/api/v1/devices/$deviceId/revoke", JsonObject(emptyMap<String, JsonElement>()), auth = true)

    override suspend fun createBindCode(): BindCode {
        val response: BindCodeResponse =
            json.post("/api/v1/devices/bind-codes", JsonObject(emptyMap<String, JsonElement>()), auth = true)
        return BindCode(code = response.code, expiresAt = response.expiresAt)
    }

    override suspend fun listRecords(limit: Int, deviceId: Long?): Paginated<Record> {
        val path = buildString {
            append("/api/v1/records?limit=")
            append(limit)
            if (deviceId != null) {
                append("&device_id=")
                append(deviceId)
            }
        }
        val response: RecordsResponse = json.get(path, auth = true)
        return Paginated(items = response.records, limit = response.limit, offset = response.offset)
    }

    override suspend fun getRecord(recordId: Long): Record? =
        json.get("/api/v1/records/$recordId", auth = true)

    override suspend fun getSystemInfo(): SystemInfo =
        json.get("/api/v1/system/info", auth = false)

    private inline fun <reified T> Json.get(path: String, auth: Boolean): T =
        decode(request(path, "GET", null, auth))

    private inline fun <reified T> Json.post(path: String, body: JsonElement, auth: Boolean): T =
        decode(request(path, "POST", body, auth))

    private inline fun <reified T> Json.patch(path: String, body: JsonElement, auth: Boolean): T =
        decode(request(path, "PATCH", body, auth))

    private inline fun <reified T> Json.decode(payload: ResponsePayload): T {
        if (payload.status == 409) {
            throw StoreError.Conflict(local = 0L, remote = 0L)
        }
        if (payload.status !in 200..299) {
            throw StoreError.Internal(messageFor(payload))
        }
        return runCatching { decodeFromString<T>(payload.body) }
            .getOrElse { error -> throw StoreError.Internal(error.message ?: "malformed response") }
    }

    private fun Json.messageFor(payload: ResponsePayload): String {
        val parsed = runCatching { decodeFromString<ErrorPayload>(payload.body) }.getOrNull()
        val detail = parsed?.error?.takeIf { it.isNotBlank() }
            ?: payload.body.takeIf { it.isNotBlank() }
        return detail ?: "Backend request failed with status ${payload.status}."
    }

    private fun request(path: String, method: String, body: JsonElement?, auth: Boolean): ResponsePayload {
        val url = resolve(path)
        val builder = Request.Builder().url(url)
        if (auth) {
            accessToken?.let { builder.header("Authorization", "Bearer $it") }
        }
        val requestBody = when {
            body == null && (method == "POST" || method == "PATCH") ->
                "{}".toRequestBody(JSON_MEDIA_TYPE)

            body != null -> json.encodeToString(body).toRequestBody(JSON_MEDIA_TYPE)

            else -> null
        }
        builder.method(method, requestBody)
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body.string()
            return ResponsePayload(status = response.code, body = text)
        }
    }

    private fun resolve(path: String): String {
        val base = baseUrl.trimEnd('/')
        val absolute = "$base$path".toHttpUrlOrNull()
            ?: throw StoreError.Internal("invalid backend url: $base$path")
        return absolute.toString()
    }

    internal data class ResponsePayload(val status: Int, val body: String)

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

