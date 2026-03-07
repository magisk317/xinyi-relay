package com.github.magisk317.smscode.forwarder.utils.sender

import android.text.TextUtils
import android.util.Base64
import com.github.magisk317.smscode.forwarder.entity.MsgInfo
import com.github.magisk317.smscode.forwarder.entity.result.BarkResult
import com.github.magisk317.smscode.forwarder.entity.setting.BarkSetting
import com.google.gson.Gson
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URL

object BarkUtils {
    private const val TAG = "BarkUtils"
    private val client = OkHttpClient()

    suspend fun sendMsg(setting: BarkSetting, msgInfo: MsgInfo) {
        val title = if (setting.title.isBlank()) "SmsCode: ${msgInfo.from}" else setting.title
        val content = msgInfo.content

        val parsed = parseBasicAuthUrl(setting.server)
        val url = parsed.first
        val basicAuth = parsed.second

        val payload = mutableMapOf<String, Any>(
            "title" to title,
            "body" to content,
            "isArchive" to 1,
        )
        if (!TextUtils.isEmpty(setting.group)) payload["group"] = setting.group
        if (!TextUtils.isEmpty(setting.icon)) payload["icon"] = setting.icon
        if (!TextUtils.isEmpty(setting.sound)) payload["sound"] = setting.sound
        if (!TextUtils.isEmpty(setting.badge)) payload["badge"] = setting.badge
        if (!TextUtils.isEmpty(setting.url)) payload["url"] = setting.url
        if (!TextUtils.isEmpty(setting.level)) payload["level"] = setting.level
        if (!TextUtils.isEmpty(setting.call)) payload["call"] = setting.call

        val json = Gson().toJson(payload)
        val request = Request.Builder()
            .url(url)
            .apply {
                if (basicAuth != null) {
                    header("Authorization", basicAuth)
                }
            }
            .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                SLog.e(TAG, "Bark send failed: ${response.code} ${response.message} $body")
                throw IllegalStateException("Bark HTTP ${response.code}: ${response.message}")
            }
            val result = runCatching { Gson().fromJson(body, BarkResult::class.java) }.getOrNull()
            if (result?.code == 200L) {
                SLog.i(TAG, "Bark send success")
            } else {
                SLog.e(TAG, "Bark response unexpected: $body")
                throw IllegalStateException("Bark 返回失败: $body")
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
