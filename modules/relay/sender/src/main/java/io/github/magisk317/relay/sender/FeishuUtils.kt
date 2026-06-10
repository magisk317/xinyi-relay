package io.github.magisk317.relay.sender

import android.util.Base64
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.net.RelayHttpClients
import io.github.magisk317.relay.sender.result.FeishuResult
import io.github.magisk317.relay.sender.config.FeishuSetting
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object FeishuUtils {
    private const val TAG = "FeishuUtils"
    private val client = RelayHttpClients.default

    suspend fun sendMsg(setting: FeishuSetting, msgInfo: MsgInfo) {
        val title = if (setting.titleTemplate.isBlank()) "信息驿站: ${msgInfo.from}" else setting.titleTemplate
        val content = msgInfo.content

        var timestamp: Long? = null
        var sign: String? = null
        if (setting.secret.isNotBlank()) {
            timestamp = System.currentTimeMillis() / 1000
            val stringToSign = "$timestamp\n${setting.secret}"
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(stringToSign.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
            val signData = mac.doFinal(byteArrayOf())
            sign = String(Base64.encode(signData, Base64.NO_WRAP))
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
    }

    private fun escapeJson(text: String): String {
        return SenderWireJson.escapeStringContent(text)
    }
}
