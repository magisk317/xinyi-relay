package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.net.RelayHttpClients
import io.github.magisk317.relay.net.parseBasicAuthUrl
import io.github.magisk317.relay.sender.result.GotifyResult
import io.github.magisk317.relay.sender.config.GotifySetting
import okhttp3.FormBody
import okhttp3.Request

object GotifyUtils {
    private const val TAG = "GotifyUtils"
    private val client = RelayHttpClients.default

    suspend fun sendMsg(setting: GotifySetting, msgInfo: MsgInfo) {
        SenderTelemetry.trace(
            senderType = "gotify",
            stage = "gotify_send",
        ) {
            val title = SenderTemplateRenderer.renderTitle(setting.title, msgInfo)
            val content = msgInfo.content

            val parsed = parseBasicAuthUrl(setting.webServer)
            val url = parsed.url
            val basicAuth = parsed.authorization

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
        }
    }

}
