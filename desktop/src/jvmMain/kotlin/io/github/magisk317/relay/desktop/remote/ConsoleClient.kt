package io.github.magisk317.relay.desktop.remote

import io.github.magisk317.relay.contract.remote.BindCodeResponse
import io.github.magisk317.relay.contract.remote.BootstrapAdminRequest
import io.github.magisk317.relay.contract.remote.BootstrapAdminResponse
import io.github.magisk317.relay.contract.remote.ChangePasswordRequest
import io.github.magisk317.relay.contract.remote.DeviceConfigCommandResponse
import io.github.magisk317.relay.contract.remote.DesktopExchangeRequest
import io.github.magisk317.relay.contract.remote.DesktopLogoutRequest
import io.github.magisk317.relay.contract.remote.DesktopRefreshRequest
import io.github.magisk317.relay.contract.remote.DesktopSessionResponse
import io.github.magisk317.relay.contract.remote.DeviceConfigAuditLogsResponse
import io.github.magisk317.relay.contract.remote.DeviceConfigCommandRequest
import io.github.magisk317.relay.contract.remote.SimpleOKResponse
import io.github.magisk317.relay.contract.remote.DeviceConfigStateResponse
import io.github.magisk317.relay.contract.remote.DevicesResponse
import io.github.magisk317.relay.contract.remote.MeResponse
import io.github.magisk317.relay.contract.remote.PatchDeviceRequest
import io.github.magisk317.relay.contract.remote.RecordsResponse
import io.github.magisk317.relay.contract.remote.RelayRecord
import io.github.magisk317.relay.contract.remote.SystemInfoResponse
import io.github.magisk317.relay.net.RelayHttpClients
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ConsoleApiException(
    val status: Int,
    body: String,
) : RuntimeException("console api failed ($status): $body")

/**
 * HTTP client for the management console backend, mirroring the Rust
 * remote_store surface: bearer-authenticated JSON under /api/v1 with the
 * DTOs from relay:contract as the single source of truth.
 */
class ConsoleClient(
    val baseUrl: String,
    private val client: OkHttpClient = RelayHttpClients.default,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    @Volatile
    var accessToken: String? = null

    private fun url(path: String): String = baseUrl.trimEnd('/') + path

    private suspend inline fun <reified T> send(
        path: String,
        method: String = "GET",
        body: String? = null,
        auth: Boolean = true,
    ): T = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url(path))
        if (auth) {
            accessToken?.let { builder.header("Authorization", "Bearer $it") }
        }
        val requestBody = when {
            body != null -> body.toRequestBody(jsonMedia)
            method != "GET" && method != "HEAD" -> "".toRequestBody(null)
            else -> null
        }
        builder.method(method, requestBody)
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) {
                throw ConsoleApiException(response.code, text.take(500))
            }
            json.decodeFromString<T>(text)
        }
    }

    /** Browser-handoff entry URL; the local callback listener supplies redirectUri. */
    fun desktopStartUrl(redirectUri: String, state: String, clientName: String): String =
        url("/api/v1/auth/desktop/start") +
            "?redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8") +
            "&state=" + URLEncoder.encode(state, "UTF-8") +
            "&client_name=" + URLEncoder.encode(clientName, "UTF-8")

    suspend fun systemInfo(): SystemInfoResponse = send("/api/v1/system/info", auth = false)

    suspend fun me(): MeResponse = send("/api/v1/auth/me")

    suspend fun desktopExchange(code: String): DesktopSessionResponse = send(
        "/api/v1/auth/desktop/exchange",
        method = "POST",
        body = json.encodeToString(DesktopExchangeRequest(code)),
        auth = false,
    )

    suspend fun desktopRefresh(refreshToken: String): DesktopSessionResponse = send(
        "/api/v1/auth/desktop/refresh",
        method = "POST",
        body = json.encodeToString(DesktopRefreshRequest(refreshToken)),
        auth = false,
    )

    suspend fun desktopLogout(refreshToken: String): JsonObject = send(
        "/api/v1/auth/desktop/logout",
        method = "POST",
        body = json.encodeToString(DesktopLogoutRequest(refreshToken)),
        auth = false,
    )

    suspend fun devices(): DevicesResponse = send("/api/v1/devices")

    suspend fun patchDevice(deviceId: Long, request: PatchDeviceRequest): JsonObject = send(
        "/api/v1/devices/$deviceId",
        method = "PATCH",
        body = json.encodeToString(request),
    )

    suspend fun revokeDevice(deviceId: Long): JsonObject = send("/api/v1/devices/$deviceId/revoke", method = "POST")

    suspend fun createBindCode(): BindCodeResponse = send("/api/v1/devices/bind-codes", method = "POST")

    suspend fun deviceConfig(deviceId: Long): DeviceConfigStateResponse? = try {
        send("/api/v1/devices/$deviceId/config")
    } catch (e: ConsoleApiException) {
        if (e.status == 404) null else throw e
    }

    /** Queues a config command and returns the persisted command (pending until the agent acks it). */
    suspend fun queueDeviceConfigCommand(
        deviceId: Long,
        request: DeviceConfigCommandRequest,
    ): DeviceConfigCommandResponse = send(
        "/api/v1/devices/$deviceId/config/commands",
        method = "POST",
        body = json.encodeToString(request),
    )

    suspend fun deviceConfigAuditLogs(
        deviceId: Long,
        limit: Int = 50,
        offset: Int = 0,
    ): DeviceConfigAuditLogsResponse = send("/api/v1/devices/$deviceId/config/audit?limit=$limit&offset=$offset")

    /** Creates the first admin account; the backend only accepts this once. */
    suspend fun bootstrapAdmin(username: String, password: String): BootstrapAdminResponse = send(
        "/api/v1/bootstrap/admin",
        method = "POST",
        body = json.encodeToString(BootstrapAdminRequest(username = username, password = password)),
    )

    suspend fun changePassword(currentPassword: String, newPassword: String): SimpleOKResponse = send(
        "/api/v1/auth/password",
        method = "POST",
        body = json.encodeToString(
            ChangePasswordRequest(currentPassword = currentPassword, newPassword = newPassword),
        ),
    )

    suspend fun records(limit: Int = 50, deviceId: Long? = null): RecordsResponse {
        val query = buildString {
            append("?limit=").append(limit)
            if (deviceId != null) append("&device_id=").append(deviceId)
        }
        return send("/api/v1/records$query")
    }

    suspend fun record(id: Long): RelayRecord = send("/api/v1/records/$id")
}
