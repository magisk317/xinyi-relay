package io.github.magisk317.relay.sender

import android.util.Base64
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.net.RelayHttpClients
import io.github.magisk317.relay.sender.config.TelegramSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetSocketAddress
import java.net.Proxy

object TelegramUtils {
    private const val TAG = "TelegramUtils"
    private const val CAPTION_MAX_LENGTH = 1024

    private fun String.escapeMarkdownV2(): String {
        return this.replace(Regex("""([_*\[\]()~`>#+\-=|{}.!\\])""")) { "\\${it.value}" }
    }

    suspend fun sendMsg(setting: TelegramSetting, msgInfo: MsgInfo) = withContext(Dispatchers.IO) {
        SenderTelemetry.trace(
            senderType = "telegram",
            stage = "telegram_send",
        ) {
            val content = if (setting.parseMode == "MarkdownV2") {
                "*信息驿站: ${msgInfo.from.escapeMarkdownV2()}*\n${msgInfo.content.escapeMarkdownV2()}"
            } else {
                "<b>信息驿站: ${msgInfo.from}</b>\n${msgInfo.content}"
            }

            val base = setting.apiBase.ifBlank { "https://api.telegram.org" }.trimEnd('/')
            val client = buildClient(setting)

            val iconBytes = decodeIconBytes(msgInfo.appIcon)
            if (iconBytes != null && content.length <= CAPTION_MAX_LENGTH) {
                sendPhoto(client, base, setting, content, iconBytes)
            } else {
                sendMessage(client, base, setting, content)
            }
        }
    }

    private fun decodeIconBytes(appIcon: String): ByteArray? {
        if (appIcon.isBlank()) return null
        return runCatching {
            Base64.decode(appIcon, Base64.NO_WRAP)
        }.getOrNull()
    }

    private fun buildClient(setting: TelegramSetting): okhttp3.OkHttpClient {
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
        return clientBuilder.build()
    }

    private fun sendPhoto(
        client: okhttp3.OkHttpClient,
        base: String,
        setting: TelegramSetting,
        caption: String,
        photoBytes: ByteArray,
    ) {
        val requestUrl = "${base}/bot${setting.apiToken}/sendPhoto"

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", setting.chatId)
            .addFormDataPart(
                "photo",
                "icon.png",
                photoBytes.toRequestBody("image/png".toMediaType()),
            )
            .addFormDataPart("caption", caption)
            .addFormDataPart("parse_mode", setting.parseMode)
            .apply {
                if (setting.messageThreadId.isNotEmpty()) {
                    addFormDataPart("message_thread_id", setting.messageThreadId)
                }
            }
            .build()

        val request = Request.Builder()
            .url(requestUrl)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val respBody = response.body.string()
            if (!response.isSuccessful) {
                SLog.e(TAG, "Telegram sendPhoto failed: ${response.code} ${response.message} $respBody")
                throw IllegalStateException("Telegram HTTP ${response.code}: ${response.message}")
            }
            if (!respBody.contains("\"ok\":true")) {
                SLog.e(TAG, "Telegram API failed: $respBody")
                throw IllegalStateException("Telegram API 返回失败: $respBody")
            }
            SLog.i(TAG, "Telegram sendPhoto success")
        }
    }

    private fun sendMessage(
        client: okhttp3.OkHttpClient,
        base: String,
        setting: TelegramSetting,
        content: String,
    ) {
        var requestUrl = "${base}/bot${setting.apiToken}/sendMessage"

        val request = if (setting.method == "GET") {
            requestUrl += "?chat_id=${setting.chatId}&text=${SenderSigning.urlEncode(content)}&parse_mode=${setting.parseMode}"
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
