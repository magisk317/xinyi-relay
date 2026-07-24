package io.github.magisk317.relay.sender

import android.text.TextUtils
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.sender.result.PushplusResult
import io.github.magisk317.relay.sender.config.PushplusSetting
import io.github.magisk317.relay.net.HttpUtils
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.github.magisk317.xposed.logging.MagiskOtel

object PushplusUtils {

    private const val TAG = "PushplusUtils"

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
                "stage" to "pushplus_send",
                "reason" to reason,
                "sender_type" to "pushplus",
            ),
            statusOk = statusOk,
        )
    }


    suspend fun sendMsg(setting: PushplusSetting, msgInfo: MsgInfo) {
        val startedAt = System.nanoTime()
        try {

        val title = SenderTemplateRenderer.renderTitle(setting.titleTemplate, msgInfo)
        val content = msgInfo.content

        // Using standard domain if not otherwise configured
        val website = if (TextUtils.isEmpty(setting.website)) "www.pushplus.plus" else setting.website
        val requestUrl = "https://$website/send"
        
        val requestJson = buildJsonObject {
            put("token", setting.token)
            put("content", content)
            put("title", title)
            if (!TextUtils.isEmpty(setting.template)) put("template", setting.template)
            if (!TextUtils.isEmpty(setting.topic)) put("topic", setting.topic)

            if (website.contains("pushplus.plus")) {
                if (!TextUtils.isEmpty(setting.channel)) put("channel", setting.channel)
                if (!TextUtils.isEmpty(setting.webhook)) put("webhook", setting.webhook)
                if (!TextUtils.isEmpty(setting.callbackUrl)) put("callbackUrl", setting.callbackUrl)
                if (!TextUtils.isEmpty(setting.validTime)) {
                    val validTime = setting.validTime.toIntOrNull() ?: 0
                    if (validTime > 0) {
                        put("timestamp", System.currentTimeMillis() + validTime * 1000L)
                    }
                }
            }
        }

        val requestMsg: String = SenderWireJson.encode(requestJson)
        SLog.i(TAG, "requestMsg:$requestMsg")

        val response = HttpUtils.postJson(requestUrl, requestMsg).getOrElse { e ->
            SLog.e(TAG, "Pushplus Request Exception", e)
            throw IllegalStateException("Pushplus 请求失败: ${e.message}", e)
        }
        SLog.i(TAG, "Response: $response")
        val resp = SenderWireJson.decodeOrNull<PushplusResult>(response)
        if (resp?.code == 200L) {
            SLog.i(TAG, "Pushplus Send Success")
        } else {
            SLog.e(TAG, "Pushplus Send Failed: $response")
            throw IllegalStateException("Pushplus 返回失败: $response")
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
}
