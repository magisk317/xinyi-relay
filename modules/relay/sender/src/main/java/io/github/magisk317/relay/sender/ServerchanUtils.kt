package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.net.RelayHttpClients
import io.github.magisk317.relay.sender.result.ServerchanResult
import io.github.magisk317.relay.sender.config.ServerchanSetting
import okhttp3.FormBody
import okhttp3.Request
import io.github.magisk317.xposed.logging.MagiskOtel

object ServerchanUtils {
    private const val TAG = "ServerchanUtils"
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
                "stage" to "serverchan_send",
                "reason" to reason,
                "sender_type" to "serverchan",
            ),
            statusOk = statusOk,
        )
    }


    suspend fun sendMsg(setting: ServerchanSetting, msgInfo: MsgInfo) {
        val startedAt = System.nanoTime()
        try {

        val title = SenderTemplateRenderer.renderTitle(setting.titleTemplate, msgInfo)
        val content = msgInfo.content

        val match = Regex("^sctp(\\d+)t", RegexOption.IGNORE_CASE).find(setting.sendKey)
        val url = if (match != null) {
            "https://${match.groupValues[1]}.push.ft07.com/send/${setting.sendKey}.send"
        } else {
            "https://sctapi.ftqq.com/${setting.sendKey}.send"
        }

        val formBuilder = FormBody.Builder()
            .add("title", title)
            .add("desp", content)

        if (setting.channel.isNotBlank()) formBuilder.add("channel", setting.channel)
        if (setting.openid.isNotBlank()) formBuilder.add("openid", setting.openid)

        val request = Request.Builder()
            .url(url)
            .post(formBuilder.build())
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                SLog.e(TAG, "Serverchan failed: ${response.code} ${response.message} $body")
                throw IllegalStateException("Server酱 HTTP ${response.code}: ${response.message}")
            }
            val result = SenderWireJson.decodeOrNull<ServerchanResult>(body)
            if (result?.code == 0L) {
                SLog.i(TAG, "Serverchan send success")
            } else {
                SLog.e(TAG, "Serverchan response unexpected: $body")
                throw IllegalStateException("Server酱返回失败: $body")
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
}
