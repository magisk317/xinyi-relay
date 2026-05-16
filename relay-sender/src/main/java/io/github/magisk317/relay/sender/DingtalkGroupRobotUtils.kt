package io.github.magisk317.relay.sender

import android.text.TextUtils
import android.util.Base64
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.sender.result.DingtalkResult
import io.github.magisk317.relay.sender.config.DingtalkGroupRobotSetting
import io.github.magisk317.relay.engine.sender.utils.HttpUtils
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object DingtalkGroupRobotUtils {

    private const val TAG = "DingtalkGroupRobot"

    suspend fun sendMsg(setting: DingtalkGroupRobotSetting, msgInfo: MsgInfo) {
        val content = msgInfo.content
        val title = setting.titleTemplate.ifBlank { "信息驿站: ${msgInfo.from}" }

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

        var textContent = content
        var atMobiles = emptyList<String>()
        var atDingtalkIds = emptyList<String>()
        if (setting.atAll) {
            atMobiles = emptyList()
            atDingtalkIds = emptyList()
        } else {
            if (!TextUtils.isEmpty(setting.atMobiles)) {
                atMobiles = setting.atMobiles.replace("[,，;；]".toRegex(), ",").trim(',').split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (atMobiles.isNotEmpty()) {
                    for (atMobile in atMobiles) {
                        if (!textContent.contains("@$atMobile")) {
                            textContent += " @$atMobile"
                        }
                    }
                }
            }
            if (!TextUtils.isEmpty(setting.atDingtalkIds)) {
                atDingtalkIds = setting.atDingtalkIds.replace("[,，;；]".toRegex(), ",").trim(',').split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (atDingtalkIds.isNotEmpty()) {
                    for (atDingtalkId in atDingtalkIds) {
                        if (!textContent.contains("@$atDingtalkId")) {
                            textContent += " @$atDingtalkId"
                        }
                    }
                }
            }
        }

        val requestJson = buildJsonObject {
            put("msgtype", setting.msgtype)
            put(
                "at",
                buildJsonObject {
                    put("isAtAll", setting.atAll)
                    if (atMobiles.isNotEmpty()) {
                        put("atMobiles", JsonArray(atMobiles.map(::JsonPrimitive)))
                    }
                    if (atDingtalkIds.isNotEmpty()) {
                        put("atDingtalkIds", JsonArray(atDingtalkIds.map(::JsonPrimitive)))
                    }
                },
            )
            if (setting.msgtype == "markdown") {
                put(
                    "markdown",
                    buildJsonObject {
                        put("title", title)
                        put("text", textContent)
                    },
                )
            } else {
                put(
                    "text",
                    buildJsonObject {
                        put("content", textContent)
                    },
                )
            }
        }

        val requestMsg: String = SenderWireJson.encode(requestJson)
        SLog.i(TAG, "requestMsg:$requestMsg")

        val response = HttpUtils.postJson(requestUrl, requestMsg).getOrElse { e ->
            SLog.e(TAG, "Dingtalk Request Exception", e)
            throw IllegalStateException("钉钉群机器人请求失败: ${e.message}", e)
        }
        SLog.i(TAG, "Response: $response")
        val resp = SenderWireJson.decodeOrNull<DingtalkResult>(response)
        if (resp?.errcode == 0L) {
            SLog.i(TAG, "Dingtalk Send Success")
        } else {
            SLog.e(TAG, "Dingtalk Send Failed: $response")
            throw IllegalStateException("钉钉群机器人返回失败: $response")
        }
    }
}
