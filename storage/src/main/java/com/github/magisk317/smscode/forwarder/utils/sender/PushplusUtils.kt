package com.github.magisk317.smscode.forwarder.utils.sender

import android.text.TextUtils
import com.github.magisk317.smscode.forwarder.entity.MsgInfo
import com.github.magisk317.smscode.forwarder.entity.result.PushplusResult
import com.github.magisk317.smscode.forwarder.entity.setting.PushplusSetting
import com.github.magisk317.smscode.forwarder.utils.HttpUtils
import com.google.gson.Gson

object PushplusUtils {

    private const val TAG = "PushplusUtils"

    suspend fun sendMsg(setting: PushplusSetting, msgInfo: MsgInfo) {
        val title = "SmsCode: ${msgInfo.from}"
        val content = msgInfo.content

        // Using standard domain if not otherwise configured
        val website = if (TextUtils.isEmpty(setting.website)) "www.pushplus.plus" else setting.website
        val requestUrl = "https://$website/send"
        
        val msgMap: MutableMap<String, Any> = mutableMapOf()
        msgMap["token"] = setting.token
        msgMap["content"] = content
        msgMap["title"] = title

        if (!TextUtils.isEmpty(setting.template)) msgMap["template"] = setting.template
        if (!TextUtils.isEmpty(setting.topic)) msgMap["topic"] = setting.topic

        if (website.contains("pushplus.plus")) {
            if (!TextUtils.isEmpty(setting.channel)) msgMap["channel"] = setting.channel
            if (!TextUtils.isEmpty(setting.webhook)) msgMap["webhook"] = setting.webhook
            if (!TextUtils.isEmpty(setting.callbackUrl)) msgMap["callbackUrl"] = setting.callbackUrl
            if (!TextUtils.isEmpty(setting.validTime)) {
                val validTime = setting.validTime.toIntOrNull() ?: 0
                if (validTime > 0) {
                    msgMap["timestamp"] = System.currentTimeMillis() + validTime * 1000L
                }
            }
        }

        val requestMsg: String = Gson().toJson(msgMap)
        SLog.i(TAG, "requestMsg:$requestMsg")

        val response = HttpUtils.postJson(requestUrl, requestMsg).getOrElse { e ->
            SLog.e(TAG, "Pushplus Request Exception", e)
            throw IllegalStateException("Pushplus 请求失败: ${e.message}", e)
        }
        SLog.i(TAG, "Response: $response")
        val resp = try {
            Gson().fromJson(response, PushplusResult::class.java)
        } catch (@Suppress("SwallowedException") e: com.google.gson.JsonSyntaxException) {
            null
        }
        if (resp?.code == 200L) {
            SLog.i(TAG, "Pushplus Send Success")
        } else {
            SLog.e(TAG, "Pushplus Send Failed: $response")
            throw IllegalStateException("Pushplus 返回失败: $response")
        }
    }
}
