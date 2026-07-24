package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.net.RelayHttpClients
import io.github.magisk317.relay.sender.config.PushdeerSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.Request
import io.github.magisk317.xposed.logging.MagiskOtel

object PushdeerUtils {
    private const val TAG = "PushdeerUtils"
    private val client = RelayHttpClients.default

    @Serializable
    private data class PushdeerResult(
        val code: Int = -1,
        val error: String? = null,
    )

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
                "stage" to "pushdeer_send",
                "reason" to reason,
                "sender_type" to "pushdeer",
            ),
            statusOk = statusOk,
        )
    }


    suspend fun sendMsg(setting: PushdeerSetting, msgInfo: MsgInfo) = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        try {

        val title = SenderTemplateRenderer.renderTitle(setting.titleTemplate, msgInfo)
        val content = msgInfo.content

        val serverUrl = if (setting.server.isBlank()) {
            "https://api2.pushdeer.com"
        } else {
            setting.server.removeSuffix("/")
        }

        val url = "$serverUrl/message/push"

        val formBuilder = FormBody.Builder()
            .add("pushkey", setting.pushkey)
            .add("text", title)
            .add("desp", content)
            .add("type", setting.type.ifBlank { "markdown" })

        val request = Request.Builder()
            .url(url)
            .post(formBuilder.build())
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                SLog.e(TAG, "PushDeer failed: ${response.code} ${response.message} $body")
                throw IllegalStateException("PushDeer HTTP ${response.code}: ${response.message}")
            }
            val result = SenderWireJson.decodeOrNull<PushdeerResult>(body)
            if (result?.code == 0) {
                SLog.i(TAG, "PushDeer send success")
            } else {
                val errorMsg = result?.error ?: "未知错误"
                SLog.e(TAG, "PushDeer response unexpected: $body")
                throw IllegalStateException("PushDeer 返回失败: $errorMsg")
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
