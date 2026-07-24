package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.net.RelayHttpClients
import io.github.magisk317.relay.sender.result.GotifyResult
import io.github.magisk317.relay.sender.config.GotifySetting
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.Request
import java.net.URL
import io.github.magisk317.xposed.logging.MagiskOtel

object GotifyUtils {
    private const val TAG = "GotifyUtils"
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
                "stage" to "gotify_send",
                "reason" to reason,
                "sender_type" to "gotify",
            ),
            statusOk = statusOk,
        )
    }


    suspend fun sendMsg(setting: GotifySetting, msgInfo: MsgInfo) {
        val startedAt = System.nanoTime()
        try {

        val title = SenderTemplateRenderer.renderTitle(setting.title, msgInfo)
        val content = msgInfo.content

        val parsed = parseBasicAuthUrl(setting.webServer)
        val url = parsed.first
        val basicAuth = parsed.second

        val formBody = FormBody.Builder()
            .add("title", title)
            .add("message", content)
            .add("priority", setting.priority.ifBlank { "0" })
            .build()

        val request = Request.Builder()
            .url(url)
            .apply {
                if (basicAuth != null) {
                    header("Authorization", basicAuth)
                }
            }
            .post(formBody)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                SLog.e(TAG, "Gotify failed: ${response.code} ${response.message} $body")
                throw IllegalStateException("Gotify HTTP ${response.code}: ${response.message}")
            }
            val result = SenderWireJson.decodeOrNull<GotifyResult>(body)
            if (result?.id != null) {
                SLog.i(TAG, "Gotify send success")
            } else {
                SLog.e(TAG, "Gotify response unexpected: $body")
                throw IllegalStateException("Gotify 返回失败: $body")
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

    private fun parseBasicAuthUrl(url: String): Pair<String, String?> {
        return runCatching {
            val u = URL(url)
            val userInfo = u.userInfo
            if (userInfo.isNullOrBlank()) {
                url to null
            } else {
                val split = userInfo.split(":", limit = 2)
                val username = split.getOrElse(0) { "" }
                val password = split.getOrElse(1) { "" }
                val clean = URL(u.protocol, u.host, u.port, u.file).toString()
                clean to Credentials.basic(username, password)
            }
        }.getOrElse { url to null }
    }
}
