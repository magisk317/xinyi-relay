package com.github.magisk317.smscode.forwarder.utils.sender

import com.github.magisk317.smscode.forwarder.entity.MsgInfo
import com.github.magisk317.smscode.forwarder.entity.result.GotifyResult
import com.github.magisk317.smscode.forwarder.entity.setting.GotifySetting
import com.google.gson.Gson
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL

object GotifyUtils {
    private const val TAG = "GotifyUtils"
    private val client = OkHttpClient()

    suspend fun sendMsg(setting: GotifySetting, msgInfo: MsgInfo) {
        val title = if (setting.title.isBlank()) "SmsCode: ${msgInfo.from}" else setting.title
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
            val result = runCatching { Gson().fromJson(body, GotifyResult::class.java) }.getOrNull()
            if (result?.id != null) {
                SLog.i(TAG, "Gotify send success")
            } else {
                SLog.e(TAG, "Gotify response unexpected: $body")
                throw IllegalStateException("Gotify 返回失败: $body")
            }
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
