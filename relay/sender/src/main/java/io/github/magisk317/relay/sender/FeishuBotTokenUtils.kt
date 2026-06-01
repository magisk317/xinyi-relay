package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.net.RelayHttpClients
import io.github.magisk317.relay.sender.result.FeishuBotTokenResult
import io.github.magisk317.relay.sender.config.FeishuBotTokenSetting
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object FeishuBotTokenUtils {
    private const val TAG = "FeishuBotTokenUtils"

    suspend fun sendMsg(setting: FeishuBotTokenSetting, msgInfo: MsgInfo) {
        sendMessage(setting, msgInfo)
    }

    private fun sendMessage(setting: FeishuBotTokenSetting, msgInfo: MsgInfo) {
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
            .header("Authorization", "Bearer ${setting.token}")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        runCatching {
            RelayHttpClients.default.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    SLog.e(TAG, "Feishu bot token send failed: ${response.code} ${response.message} $body")
                    throw IllegalStateException("飞书新版机器人发送 HTTP ${response.code}: ${response.message}")
                }
                val result = SenderWireJson.decodeOrNull<FeishuBotTokenResult>(body)
                if (result?.code == 0L) {
                    SLog.i(TAG, "Feishu bot token send success")
                } else {
                    SLog.e(TAG, "Feishu bot token response unexpected: $body")
                    throw IllegalStateException("飞书新版机器人发送返回失败: $body")
                }
            }
        }.onFailure {
            SLog.e(TAG, "Feishu bot token send exception", it)
        }.getOrElse { throw it }
    }

    private fun escapeJson(text: String): String {
        return SenderWireJson.escapeStringContent(text)
    }
}
