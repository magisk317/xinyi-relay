package com.github.magisk317.smscode.forwarder.utils.sender

import android.text.TextUtils
import android.util.Base64
import com.github.magisk317.smscode.forwarder.entity.MsgInfo
import com.github.magisk317.smscode.forwarder.entity.result.DingtalkResult
import com.github.magisk317.smscode.forwarder.entity.setting.DingtalkGroupRobotSetting
import com.github.magisk317.smscode.forwarder.utils.HttpUtils
import com.google.gson.Gson
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object DingtalkGroupRobotUtils {

    private const val TAG = "DingtalkGroupRobot"

    suspend fun sendMsg(setting: DingtalkGroupRobotSetting, msgInfo: MsgInfo) {
        val content = msgInfo.content
        val title = "SmsCode: ${msgInfo.from}"

        var requestUrl = if (setting.token.startsWith("http")) setting.token else "https://oapi.dingtalk.com/robot/send?access_token=" + setting.token

        if (!TextUtils.isEmpty(setting.secret)) {
            val timestamp = System.currentTimeMillis()
            val stringToSign = "$timestamp\n" + setting.secret
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(setting.secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
            val signData = mac.doFinal(stringToSign.toByteArray(StandardCharsets.UTF_8))
            val sign = URLEncoder.encode(String(Base64.encode(signData, Base64.NO_WRAP)), "UTF-8")
            requestUrl += "&timestamp=$timestamp&sign=$sign"
        }

        SLog.i(TAG, "requestUrl:$requestUrl")

        val msgMap: MutableMap<String, Any> = mutableMapOf()
        msgMap["msgtype"] = setting.msgtype

        var textContent = content

        val atMap: MutableMap<String, Any> = mutableMapOf()
        msgMap["at"] = atMap
        if (setting.atAll) {
            atMap["isAtAll"] = true
        } else {
            atMap["isAtAll"] = false
            if (!TextUtils.isEmpty(setting.atMobiles)) {
                val atMobilesArray = setting.atMobiles.replace("[,，;；]".toRegex(), ",").trim(',').split(',').toTypedArray()
                if (atMobilesArray.isNotEmpty()) {
                    atMap["atMobiles"] = atMobilesArray
                    for (atMobile in atMobilesArray) {
                        if (!textContent.contains("@$atMobile")) {
                            textContent += " @$atMobile"
                        }
                    }
                }
            }
            if (!TextUtils.isEmpty(setting.atDingtalkIds)) {
                val atDingtalkIdsArray = setting.atDingtalkIds.replace("[,，;；]".toRegex(), ",").trim(',').split(',').toTypedArray()
                if (atDingtalkIdsArray.isNotEmpty()) {
                    atMap["atDingtalkIds"] = atDingtalkIdsArray
                    for (atDingtalkId in atDingtalkIdsArray) {
                        if (!textContent.contains("@$atDingtalkId")) {
                            textContent += " @$atDingtalkId"
                        }
                    }
                }
            }
        }

        if ("markdown" == msgMap["msgtype"]) {
            msgMap["markdown"] = mutableMapOf<String, Any>("title" to title, "text" to textContent)
        } else {
            msgMap["text"] = mutableMapOf<String, Any>("content" to textContent)
        }

        val requestMsg: String = Gson().toJson(msgMap)
        SLog.i(TAG, "requestMsg:$requestMsg")

        val response = HttpUtils.postJson(requestUrl, requestMsg).getOrElse { e ->
            SLog.e(TAG, "Dingtalk Request Exception", e)
            throw IllegalStateException("钉钉群机器人请求失败: ${e.message}", e)
        }
        SLog.i(TAG, "Response: $response")
        val resp = try {
            Gson().fromJson(response, DingtalkResult::class.java)
        } catch (@Suppress("SwallowedException") e: com.google.gson.JsonSyntaxException) {
            null
        }
        if (resp?.errcode == 0L) {
            SLog.i(TAG, "Dingtalk Send Success")
        } else {
            SLog.e(TAG, "Dingtalk Send Failed: $response")
            throw IllegalStateException("钉钉群机器人返回失败: $response")
        }
    }
}
