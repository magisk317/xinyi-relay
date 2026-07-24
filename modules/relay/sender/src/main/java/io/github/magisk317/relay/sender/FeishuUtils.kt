package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.net.RelayHttpClients
import io.github.magisk317.relay.sender.result.FeishuResult
import io.github.magisk317.relay.sender.config.FeishuSetting
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import io.github.magisk317.xposed.logging.MagiskOtel

object FeishuUtils {
    private const val TAG = "FeishuUtils"
    private val client = RelayHttpClients.default

    private fun emitForward(
        result: String,
        reason: String,
        durationMs: Long,
        statusOk: Boolean = true,
    ) {
        MagiskOtel.event(
            name = "sms.forward",
            attributes = mapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "app",
                "stage" to "feishu_webhook_send",
                "reason" to reason,
                "sender_type" to "feishu_webhook",
            ),
            statusOk = statusOk,
        )
    }


    suspend fun sendMsg(setting: FeishuSetting, msgInfo: MsgInfo) {
        val startedAt = System.nanoTime()
        try {

        val title = SenderTemplateRenderer.renderTitle(setting.titleTemplate, msgInfo)
        val content = msgInfo.content

        var timestamp: Long? = null
        var sign: String? = null
        if (setting.secret.isNotBlank()) {
            timestamp = System.currentTimeMillis() / 1000
            val stringToSign = "$timestamp\n${setting.secret}"
            sign = SenderSigning.hmacSha256Base64(stringToSign, "")
        }

        val requestJson = buildJsonObject {
            timestamp?.let { put("timestamp", it) }
            sign?.let { put("sign", it) }
            if (setting.msgType == "interactive") {
                put("msg_type", "interactive")
            val cardJson = if (setting.messageCard.isBlank()) {
                """
                {
                  "config": {"wide_screen_mode": true},
                  "header": {
                    "template": "turquoise",
                    "title": {"tag": "plain_text", "content": "${escapeJson(title)}"}
                  },
                  "elements": [
                    {"tag": "div", "text": {"tag": "lark_md", "content": "${escapeJson(content)}"}}
                  ]
                }
                """.trimIndent()
            } else {
                setting.messageCard
                    .replace("{{MSG_TITLE}}", escapeJson(title))
                    .replace("{{MSG_CONTENT}}", escapeJson(content))
            }
                put("card", SenderWireJson.parseElement(cardJson))
            } else {
                put("msg_type", "text")
                put(
                    "content",
                    buildJsonObject {
                        put("text", content)
                    },
                )
            }
        }

        val json = SenderWireJson.encode(requestJson)
        val request = Request.Builder()
            .url(setting.webhook)
            .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                SLog.e(TAG, "Feishu failed: ${response.code} ${response.message} $body")
                throw IllegalStateException("飞书 HTTP ${response.code}: ${response.message}")
            }
            val result = SenderWireJson.decodeOrNull<FeishuResult>(body)
            if (result?.code == 0L) {
                SLog.i(TAG, "Feishu send success")
            } else {
                SLog.e(TAG, "Feishu response unexpected: $body")
                throw IllegalStateException("飞书返回失败: $body")
            }
        }
    
            emitForward(
                result = "ok",
                reason = "success",
                durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
            )
        } catch (error: Exception) {
            emitForward(
                result = "error",
                reason = error.javaClass.simpleName,
                durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
                statusOk = false,
            )
            throw error
        }
}

    private fun escapeJson(text: String): String {
        return SenderWireJson.escapeStringContent(text)
    }
}
