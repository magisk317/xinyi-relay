package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.net.RelayHttpClients
import io.github.magisk317.relay.sender.config.TelegramSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URLEncoder

object TelegramUtils {
    private const val TAG = "TelegramUtils"

    private fun String.escapeMarkdownV2(): String {
        return this.replace(Regex("""([_*\[\]()~`>#+\-=|{}.!\\])""")) { "\\${it.value}" }
    }

    suspend fun sendMsg(setting: TelegramSetting, msgInfo: MsgInfo) = withContext(Dispatchers.IO) {
        val content = if (setting.parseMode == "MarkdownV2") {
            "*信息驿站: ${msgInfo.from.escapeMarkdownV2()}*\n${msgInfo.content.escapeMarkdownV2()}"
        } else {
            "<b>信息驿站: ${msgInfo.from}</b>\n${msgInfo.content}"
        }
        var requestUrl = "https://api.telegram.org/bot${setting.apiToken}/sendMessage"

        val clientBuilder = RelayHttpClients.newBuilder()
        if (setting.proxyType != Proxy.Type.DIRECT && setting.proxyHost.isNotEmpty() && setting.proxyPort.isNotEmpty()) {
            val port = setting.proxyPort.toIntOrNull() ?: 0
            val proxy = Proxy(setting.proxyType, InetSocketAddress(setting.proxyHost, port))
            clientBuilder.proxy(proxy)

            if (setting.proxyAuthenticator && setting.proxyUsername.isNotEmpty() && setting.proxyPassword.isNotEmpty()) {
                clientBuilder.proxyAuthenticator { _, response ->
                    val credential = Credentials.basic(setting.proxyUsername, setting.proxyPassword)
                    response.request.newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build()
                }
            }
        }
        val client = clientBuilder.build()

        val request = if (setting.method == "GET") {
            requestUrl += "?chat_id=${setting.chatId}&text=${URLEncoder.encode(content, "UTF-8")}&parse_mode=${setting.parseMode}"
            if (setting.messageThreadId.isNotEmpty()) {
                requestUrl += "&message_thread_id=${setting.messageThreadId}"
            }
            Request.Builder().url(requestUrl).get().build()
        } else {
            val requestMsg = SenderWireJson.encode(
                buildJsonObject {
                    put("chat_id", setting.chatId)
                    put("text", content)
                    put("parse_mode", setting.parseMode)
                    if (setting.messageThreadId.isNotEmpty()) {
                        put("message_thread_id", setting.messageThreadId)
                    }
                },
            )
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestMsg.toRequestBody(mediaType)
            Request.Builder().url(requestUrl).post(body).build()
        }

        client.newCall(request).execute().use { response ->
            val respBody = response.body.string()
            if (!response.isSuccessful) {
                SLog.e(TAG, "Telegram send failed: ${response.code} ${response.message} $respBody")
                throw IllegalStateException("Telegram HTTP ${response.code}: ${response.message}")
            }
            if (!respBody.contains("\"ok\":true")) {
                SLog.e(TAG, "Telegram API failed: $respBody")
                throw IllegalStateException("Telegram API 返回失败: $respBody")
            }
            SLog.i(TAG, "Telegram send success")
        }
    }
}
