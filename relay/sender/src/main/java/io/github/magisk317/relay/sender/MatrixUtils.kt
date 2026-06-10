package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.net.RelayHttpClients
import io.github.magisk317.relay.sender.config.MatrixSetting
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object MatrixUtils {
    private const val TAG = "MatrixUtils"
    private const val MATRIX_MESSAGE_TYPE = "m.text"
    private const val MATRIX_HTML_FORMAT = "org.matrix.custom.html"
    private const val SENDER_MESSAGE_TYPE_MARKDOWN = "markdown"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun sendMsg(setting: MatrixSetting, msgInfo: MsgInfo) = withContext(Dispatchers.IO) {
        val safeSetting = SenderSettingSanitizer.sanitizeMatrixSetting(setting)
        val client = buildClient(safeSetting)
        val requestUrl = buildSendUrl(
            homeserver = safeSetting.homeserver,
            roomId = safeSetting.roomId,
            transactionId = newTransactionId(),
        )
        val request = Request.Builder()
            .url(requestUrl)
            .put(buildMessageJson(safeSetting, msgInfo).toRequestBody(jsonMediaType))
            .header("Authorization", "Bearer ${safeSetting.accessToken.trim()}")
            .build()

        SLog.d(TAG, "Matrix request prepared: homeserver=${safeSetting.homeserver}")
        client.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                val bodyPreview = responseBody.take(400)
                SLog.e(TAG, "Matrix failed: ${response.code} ${response.message} $bodyPreview")
                val errorMsg = if (response.message.isNotBlank()) response.message else "Forbidden/Error"
                throw IllegalStateException("Matrix HTTP ${response.code} $errorMsg: $bodyPreview")
            }
            SLog.i(TAG, "Matrix send success: ${response.code}")
        }
    }

    internal fun buildSendUrl(homeserver: String, roomId: String, transactionId: String): String {
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

    internal fun buildMessageJson(setting: MatrixSetting, msgInfo: MsgInfo): String {
        val title = setting.titleTemplate.ifBlank { "信息驿站: ${msgInfo.from}" }
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

    internal fun buildClient(setting: MatrixSetting): OkHttpClient {
        val builder = RelayHttpClients.newBuilder()
        if (setting.proxyType != Proxy.Type.DIRECT && setting.proxyHost.isNotBlank() && setting.proxyPort.isNotBlank()) {
            val port = setting.proxyPort.toIntOrNull() ?: 0
            if (port > 0) {
                builder.proxy(Proxy(setting.proxyType, InetSocketAddress(setting.proxyHost, port)))
                if (setting.proxyAuthenticator && setting.proxyUsername.isNotBlank() && setting.proxyPassword.isNotBlank()) {
                    builder.proxyAuthenticator { _, response ->
                        val credential = Credentials.basic(setting.proxyUsername, setting.proxyPassword)
                        response.request.newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build()
                    }
                }
            }
        }
        return builder.build()
    }

    internal fun buildFormattedBody(title: String, content: String): String {
        return "<strong>${escapeHtml(title)}</strong><br />${markdownInlineToHtml(content).replace("\n", "<br />")}"
    }

    internal fun normalizeHomeserver(homeserver: String): String {
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
}
