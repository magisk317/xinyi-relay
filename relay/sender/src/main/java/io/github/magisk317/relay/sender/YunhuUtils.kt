package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.net.HttpUtils
import io.github.magisk317.relay.sender.config.YunhuSetting
import io.github.magisk317.relay.sender.result.YunhuResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

object YunhuUtils {

    private const val TAG = "YunhuUtils"
    private const val BASE_URL = "https://chat-go.jwzhd.com/open-apis/v1/bot/send"

    suspend fun sendMsg(setting: YunhuSetting, msgInfo: MsgInfo) {
        val title = setting.titleTemplate.ifBlank { "信息驿站: ${msgInfo.from}" }
        val text = if (title.isBlank()) msgInfo.content else "$title\n${msgInfo.content}"
        val contentType = setting.contentType.ifBlank { "text" }
        val recvType = setting.recvType.ifBlank { "user" }

        val requestJson = buildJsonObject {
            put("recvId", setting.recvId)
            put("recvType", recvType)
            put("contentType", contentType)
            putJsonObject("content") {
                put("text", text)
            }
        }

        val requestUrl = "$BASE_URL?token=${setting.token}"
        val requestMsg = SenderWireJson.encode(requestJson)
        SLog.i(TAG, "requestMsg:$requestMsg")

        val response = HttpUtils.postJson(requestUrl, requestMsg).getOrElse { e ->
            SLog.e(TAG, "Yunhu Request Exception", e)
            throw IllegalStateException("云湖请求失败: ${e.message}", e)
        }
        SLog.i(TAG, "Response: $response")
        val resp = SenderWireJson.decodeOrNull<YunhuResult>(response)
        if (resp?.code == 1L) {
            SLog.i(TAG, "Yunhu Send Success")
        } else {
            SLog.e(TAG, "Yunhu Send Failed: $response")
            throw IllegalStateException("云湖返回失败: $response")
        }
    }
}
