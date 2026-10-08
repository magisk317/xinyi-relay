package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.net.RelayHttpClients
import io.github.magisk317.relay.net.ProxyConfig
import io.github.magisk317.relay.net.applyProxy
import io.github.magisk317.relay.sender.config.MatrixSetting
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Suppress("DEPRECATION")
object MatrixUtils {
    private const val TAG = "MatrixUtils"
    private const val MATRIX_MESSAGE_TYPE = "m.text"
    private const val MATRIX_HTML_FORMAT = "org.matrix.custom.html"
    private const val SENDER_MESSAGE_TYPE_MARKDOWN = "markdown"
    private const val HTTP_UNAUTHORIZED = 401
    private const val RESPONSE_BODY_PREVIEW_LENGTH = 400
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun sendMsg(setting: MatrixSetting, msgInfo: MsgInfo) = withContext(Dispatchers.IO) {
        SenderTelemetry.trace(
            senderType = "matrix",
            stage = "matrix_plaintext",
        ) {
            val safeSetting = SenderSettingSanitizer.sanitizeMatrixSetting(setting)
            val client = buildClient(safeSetting)
            val token = getAuthToken(safeSetting)

            val response = executeSend(client, safeSetting, msgInfo, token)
            if (response.code == HTTP_UNAUTHORIZED && safeSetting.username.isNotBlank()) {
                // Token expired or invalidated — force re-login and retry once
                SLog.w(TAG, "Matrix send got 401, re-login and retrying...")
                invalidateCachedAuthToken()
                val freshToken = getAuthToken(safeSetting, forceRefresh = true)
                val retryResponse = executeSend(client, safeSetting, msgInfo, freshToken)
                if (!retryResponse.isSuccessful) {
                    val bodyPreview = retryResponse.body.take(RESPONSE_BODY_PREVIEW_LENGTH)
                    val errorMsg = if (retryResponse.message.isNotBlank()) retryResponse.message else "Forbidden/Error"
                    throw IllegalStateException("Matrix HTTP ${retryResponse.code} $errorMsg: $bodyPreview")
                }
                SLog.i(TAG, "Matrix send success on retry: ${retryResponse.code}")
            } else if (!response.isSuccessful) {
                val bodyPreview = response.body.take(RESPONSE_BODY_PREVIEW_LENGTH)
                val errorMsg = if (response.message.isNotBlank()) response.message else "Forbidden/Error"
                throw IllegalStateException("Matrix HTTP ${response.code} $errorMsg: $bodyPreview")
            } else {
                SLog.i(TAG, "Matrix send success: ${response.code}")
            }
        }
    }

    internal data class SendResponse(val code: Int, val message: String, val body: String, val isSuccessful: Boolean)

    private fun executeSend(
        client: OkHttpClient,
        setting: MatrixSetting,
        msgInfo: MsgInfo,
        token: String,
    ): SendResponse {
        val requestUrl = buildSendUrl(
            homeserver = setting.homeserver,
            roomId = setting.roomId,
            transactionId = newTransactionId(),
        )
        val request = Request.Builder()
            .url(requestUrl)
            .put(buildMessageJson(setting, msgInfo).toRequestBody(jsonMediaType))
            .header("Authorization", "Bearer $token")
            .build()

        SLog.d(TAG, "Matrix request prepared: homeserver=${setting.homeserver}")
        client.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            return SendResponse(
                code = response.code,
                message = response.message,
                body = responseBody,
                isSuccessful = response.isSuccessful,
            )
        }
    }

    fun buildSendUrl(homeserver: String, roomId: String, transactionId: String): String {
        val baseUrl = normalizeHomeserver(homeserver).toHttpUrlOrNull()
            ?: throw IllegalStateException("Matrix homeserver 地址无效")
        val normalizedRoomId = roomId.trim()
        if (normalizedRoomId.isBlank()) {
            throw IllegalStateException("Matrix roomId 不能为空")
        }
        return baseUrl.newBuilder()
            .addPathSegments("_matrix/client/v3/rooms")
            .addPathSegment(normalizedRoomId)
            .addPathSegment("send")
            .addPathSegment("m.room.message")
            .addPathSegment(transactionId)
            .build()
            .toString()
    }

    fun buildMessageJson(setting: MatrixSetting, msgInfo: MsgInfo): String {
        val title = SenderTemplateRenderer.renderTitle(setting.titleTemplate, msgInfo)
        val body = "$title\n${msgInfo.content}"
        val messageType = setting.messageType.ifBlank { MatrixSetting().messageType }
        return SenderWireJson.encode(
            buildJsonObject {
                put("msgtype", MATRIX_MESSAGE_TYPE)
                put("body", body)
                if (messageType == SENDER_MESSAGE_TYPE_MARKDOWN) {
                    put("format", MATRIX_HTML_FORMAT)
                    put("formatted_body", buildFormattedBody(title, msgInfo.content))
                }
            },
        )
    }

    fun buildClient(setting: MatrixSetting): OkHttpClient {
        return RelayHttpClients.newBuilder()
            .applyProxy(setting.toProxyConfig())
            .build()
    }

    private fun MatrixSetting.toProxyConfig() = ProxyConfig(
        type = proxyType,
        host = proxyHost,
        port = proxyPort,
        authenticate = proxyAuthenticator,
        username = proxyUsername,
        password = proxyPassword,
    )

    fun buildFormattedBody(title: String, content: String): String {
        return "<strong>${escapeHtml(title)}</strong><br />${markdownInlineToHtml(content).replace("\n", "<br />")}"
    }

    fun normalizeHomeserver(homeserver: String): String {
        return homeserver.trim().trimEnd('/')
    }

    private fun markdownInlineToHtml(content: String): String {
        var html = escapeHtml(content)
        html = Regex("""`([^`]+)`""").replace(html) { match ->
            "<code>${match.groupValues[1]}</code>"
        }
        html = Regex("""\*\*([^*]+)\*\*""").replace(html) { match ->
            "<strong>${match.groupValues[1]}</strong>"
        }
        html = Regex("""\[([^\]]+)]\((https?://[^)\s]+)\)""").replace(html) { match ->
            val text = match.groupValues[1]
            val href = match.groupValues[2]
            """<a href="$href">$text</a>"""
        }
        return html
    }

    private fun escapeHtml(value: String): String {
        return buildString(value.length) {
            value.forEach { char ->
                when (char) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(char)
                }
            }
        }
    }

    private fun newTransactionId(): String = "xinyi-${UUID.randomUUID()}"

    private var cachedAuthToken: String? = null
    private var cachedAuthKey: String? = null

    /**
     * Obtain an access token for Matrix HTTP API calls.
     *
     * Priority:
     * 1. If username+password are configured → login via API (cached per credentials)
     * 2. Else if accessToken is configured → use it directly
     * 3. Else → throw
     *
     * When [forceRefresh] is true, cached token is discarded and a fresh login is performed.
     */
    suspend fun getAuthToken(setting: MatrixSetting, forceRefresh: Boolean = false): String {
        val username = setting.username.trim()
        val password = setting.password

        // Prefer login when credentials are available
        if (username.isNotBlank() && password.isNotBlank()) {
            val cacheKey = "$username:$password"
            if (!forceRefresh && cacheKey == cachedAuthKey && !cachedAuthToken.isNullOrEmpty()) {
                return cachedAuthToken!!
            }

            val client = buildClient(setting)
            val loginUrl = normalizeHomeserver(setting.homeserver).toHttpUrlOrNull()
                ?.newBuilder()
                ?.addPathSegments("_matrix/client/v3/login")
                ?.build() ?: throw IllegalStateException("Matrix homeserver 地址无效")

            val loginBody = buildJsonObject {
                put("type", "m.login.password")
                put("identifier", buildJsonObject {
                    put("type", "m.id.user")
                    put("user", username)
                })
                put("password", password)
                put("initial_device_display_name", "XinyiRelay")
                // Use a fixed device ID so we don't proliferate zombie devices
                put("device_id", "XINYI_RELAY")
            }

            val request = Request.Builder()
                .url(loginUrl)
                .post(loginBody.toString().toRequestBody(jsonMediaType))
                .build()

            return withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Matrix login failed: HTTP ${response.code} ${response.message}")
                    }
                    val body = response.body.string()
                    val json = org.json.JSONObject(body)
                    val token = json.optString("access_token", "")
                    if (token.isEmpty()) {
                        throw IllegalStateException("Matrix login returned empty access_token")
                    }
                    cachedAuthKey = cacheKey
                    cachedAuthToken = token
                    SLog.i(TAG, "Matrix login successful, token obtained for plaintext/query use")
                    token
                }
            }
        }

        // Fallback: use manually configured accessToken
        val configuredToken = setting.accessToken.trim()
        if (configuredToken.isNotEmpty()) {
            return configuredToken
        }

        throw IllegalStateException("Matrix 凭据缺失：未配置用户名和密码，也未配置 accessToken")
    }

    /** Clear cached auth token so the next getAuthToken call performs a fresh login. */
    fun invalidateCachedAuthToken() {
        cachedAuthToken = null
        cachedAuthKey = null
    }
}
