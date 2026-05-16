package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.net.RelayHttpClients
import io.github.magisk317.relay.sender.result.FeishuAppResult
import io.github.magisk317.relay.sender.config.FeishuAppSetting
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap

object FeishuAppUtils {
    private const val TAG = "FeishuAppUtils"

    private data class TokenCache(val token: String, val expiresAt: Long)
    private val tokenCache = ConcurrentHashMap<String, TokenCache>()

    suspend fun sendMsg(setting: FeishuAppSetting, msgInfo: MsgInfo) {
        val now = System.currentTimeMillis()
        var token = tokenCache[setting.appId]?.takeIf { it.expiresAt > now }?.token
        if (token.isNullOrBlank()) {
            token = fetchToken(setting) ?: throw IllegalStateException("飞书应用获取 token 失败")
        }
        sendMessage(setting, token, msgInfo)
    }

    private fun fetchToken(setting: FeishuAppSetting): String? {
        val requestBody = SenderWireJson.encode(
            buildJsonObject {
                put("app_id", setting.appId)
                put("app_secret", setting.appSecret)
            },
        )
        val request = Request.Builder()
            .url("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")
            .post(requestBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return runCatching {
            RelayHttpClients.default.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    SLog.e(TAG, "Fetch token failed: ${response.code} ${response.message} $body")
                    throw IllegalStateException("飞书应用 token HTTP ${response.code}: ${response.message}")
                }
                val result = SenderWireJson.decode<FeishuAppResult>(body)
                if (result.code == 0L && !result.tenant_access_token.isNullOrBlank()) {
                    val expires = result.expire ?: 7200L
                    tokenCache[setting.appId] = TokenCache(
                        token = result.tenant_access_token!!,
                        expiresAt = System.currentTimeMillis() + (expires - 120) * 1000,
                    )
                    result.tenant_access_token
                } else {
                    SLog.e(TAG, "Fetch token response unexpected: $body")
                    throw IllegalStateException("飞书应用 token 返回失败: $body")
                }
            }
        }.onFailure {
            SLog.e(TAG, "Fetch token exception", it)
        }.getOrElse { throw it }
    }

    private fun sendMessage(setting: FeishuAppSetting, token: String, msgInfo: MsgInfo) {
        val content = msgInfo.content
        val title = setting.titleTemplate.ifBlank { "信息驿站: ${msgInfo.from}" }
        val contentJson = if (setting.msgType == "interactive") {
            if (setting.messageCard.isBlank()) {
                "{" +
                    "\"elements\":[{" +
                    "\"tag\":\"markdown\",\"content\":\"**${escapeJson(title)}**\\n${escapeJson(content)}\"" +
                    "}]" +
                    "}"
            } else {
                setting.messageCard
                    .replace("{{MSG_TITLE}}", escapeJson(title))
                    .replace("{{MSG_CONTENT}}", escapeJson(content))
            }
        } else {
            "{\"text\":\"${escapeJson(content)}\"}"
        }

        val payload = SenderWireJson.encode(
            buildJsonObject {
                put("receive_id", setting.receiveId)
                put("msg_type", setting.msgType)
                put("content", contentJson)
            },
        )

        val request = Request.Builder()
            .url("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=${setting.receiveIdType}")
            .header("Authorization", "Bearer $token")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        runCatching {
            RelayHttpClients.default.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    SLog.e(TAG, "Feishu app send failed: ${response.code} ${response.message} $body")
                    throw IllegalStateException("飞书应用发送 HTTP ${response.code}: ${response.message}")
                }
                val result = SenderWireJson.decodeOrNull<FeishuAppResult>(body)
                if (result?.code == 0L) {
                    SLog.i(TAG, "Feishu app send success")
                } else {
                    SLog.e(TAG, "Feishu app response unexpected: $body")
                    throw IllegalStateException("飞书应用发送返回失败: $body")
                }
            }
        }.onFailure {
            SLog.e(TAG, "Feishu app send exception", it)
        }.getOrElse { throw it }
    }

    private fun escapeJson(text: String): String {
        return SenderWireJson.escapeStringContent(text)
    }
}
