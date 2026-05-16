package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.network.RelayHttpClients
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.sender.result.WeworkRobotResult
import io.github.magisk317.relay.sender.config.WeworkRobotSetting
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object WeworkRobotUtils {
    private const val TAG = "WeworkRobotUtils"
    private val client = RelayHttpClients.default

    suspend fun sendMsg(setting: WeworkRobotSetting, msgInfo: MsgInfo) {
        val content = msgInfo.content
        val msgType = if (setting.msgType == "markdown") "markdown" else "text"
        val json = SenderWireJson.encode(
            buildJsonObject {
                put("msgtype", msgType)
                if (msgType == "markdown") {
                    put(
                        "markdown",
                        buildJsonObject {
                            put("content", content)
                        },
                    )
                } else {
                    put(
                        "text",
                        buildJsonObject {
                            put("content", content)
                            if (setting.atAll) {
                                put("mentioned_list", JsonArray(listOf(JsonPrimitive("@all"))))
                            } else {
                                if (setting.atUserIds.isNotBlank()) {
                                    put(
                                        "mentioned_list",
                                        JsonArray(
                                            setting.atUserIds.split(',')
                                                .map { it.trim() }
                                                .filter { it.isNotBlank() }
                                                .map(::JsonPrimitive),
                                        ),
                                    )
                                }
                                if (setting.atMobiles.isNotBlank()) {
                                    put(
                                        "mentioned_mobile_list",
                                        JsonArray(
                                            setting.atMobiles.split(',')
                                                .map { it.trim() }
                                                .filter { it.isNotBlank() }
                                                .map(::JsonPrimitive),
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            },
        )
        val request = Request.Builder()
            .url(setting.webHook)
            .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                SLog.e(TAG, "Wework robot failed: ${response.code} ${response.message} $body")
                throw IllegalStateException("企业微信机器人 HTTP ${response.code}: ${response.message}")
            }
            val result = SenderWireJson.decodeOrNull<WeworkRobotResult>(body)
            if (result?.errcode == 0L) {
                SLog.i(TAG, "Wework robot send success")
            } else {
                SLog.e(TAG, "Wework robot response unexpected: $body")
                throw IllegalStateException("企业微信机器人返回失败: $body")
            }
        }
    }
}
