package com.github.magisk317.smscode.forwarder.utils.sender

import com.github.magisk317.smscode.forwarder.entity.MsgInfo
import com.github.magisk317.smscode.forwarder.entity.result.WeworkRobotResult
import com.github.magisk317.smscode.forwarder.entity.setting.WeworkRobotSetting
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object WeworkRobotUtils {
    private const val TAG = "WeworkRobotUtils"
    private val client = OkHttpClient()

    suspend fun sendMsg(setting: WeworkRobotSetting, msgInfo: MsgInfo) {
        val content = msgInfo.content
        val msgType = if (setting.msgType == "markdown") "markdown" else "text"
        val bodyMap = mutableMapOf<String, Any>("msgtype" to msgType)

        if (msgType == "markdown") {
            bodyMap["markdown"] = mapOf("content" to content)
        } else {
            val textMap = mutableMapOf<String, Any>("content" to content)
            if (setting.atAll) {
                textMap["mentioned_list"] = listOf("@all")
            } else {
                if (setting.atUserIds.isNotBlank()) {
                    textMap["mentioned_list"] = setting.atUserIds.split(',').map { it.trim() }.filter { it.isNotBlank() }
                }
                if (setting.atMobiles.isNotBlank()) {
                    textMap["mentioned_mobile_list"] = setting.atMobiles.split(',').map { it.trim() }.filter { it.isNotBlank() }
                }
            }
            bodyMap["text"] = textMap
        }

        val json = Gson().toJson(bodyMap)
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
            val result = runCatching { Gson().fromJson(body, WeworkRobotResult::class.java) }.getOrNull()
            if (result?.errcode == 0L) {
                SLog.i(TAG, "Wework robot send success")
            } else {
                SLog.e(TAG, "Wework robot response unexpected: $body")
                throw IllegalStateException("企业微信机器人返回失败: $body")
            }
        }
    }
}
