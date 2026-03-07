package com.github.magisk317.smscode.forwarder.utils.sender

import android.util.Base64
import com.github.magisk317.smscode.forwarder.entity.MsgInfo
import com.github.magisk317.smscode.forwarder.entity.result.FeishuResult
import com.github.magisk317.smscode.forwarder.entity.setting.FeishuSetting
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object FeishuUtils {
    private const val TAG = "FeishuUtils"
    private val client = OkHttpClient()

    suspend fun sendMsg(setting: FeishuSetting, msgInfo: MsgInfo) {
        val title = if (setting.titleTemplate.isBlank()) "SmsCode: ${msgInfo.from}" else setting.titleTemplate
        val content = msgInfo.content

        val bodyMap = mutableMapOf<String, Any>()
        if (setting.secret.isNotBlank()) {
            val timestamp = System.currentTimeMillis() / 1000
            val stringToSign = "$timestamp\n${setting.secret}"
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(stringToSign.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
            val signData = mac.doFinal(byteArrayOf())
            val sign = String(Base64.encode(signData, Base64.NO_WRAP))
            bodyMap["timestamp"] = timestamp
            bodyMap["sign"] = sign
        }

        if (setting.msgType == "interactive") {
            bodyMap["msg_type"] = "interactive"
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
            bodyMap["card"] = JsonParser.parseString(cardJson)
        } else {
            bodyMap["msg_type"] = "text"
            bodyMap["content"] = mapOf("text" to content)
        }

        val json = Gson().toJson(bodyMap)
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
            val result = runCatching { Gson().fromJson(body, FeishuResult::class.java) }.getOrNull()
            if (result?.code == 0L) {
                SLog.i(TAG, "Feishu send success")
            } else {
                SLog.e(TAG, "Feishu response unexpected: $body")
                throw IllegalStateException("飞书返回失败: $body")
            }
        }
    }

    private fun escapeJson(text: String): String {
        val json = Gson().toJson(text)
        return if (json.length >= 2) json.substring(1, json.length - 1) else json
    }
}
