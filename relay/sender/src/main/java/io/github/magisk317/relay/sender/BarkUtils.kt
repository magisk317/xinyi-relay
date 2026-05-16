package io.github.magisk317.relay.sender

import android.text.TextUtils
import android.util.Base64
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.engine.network.RelayHttpClients
import io.github.magisk317.relay.sender.result.BarkResult
import io.github.magisk317.relay.sender.config.BarkSetting
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URL

object BarkUtils {
    private const val TAG = "BarkUtils"
    private val client = RelayHttpClients.default

    suspend fun sendMsg(setting: BarkSetting, msgInfo: MsgInfo) {
        val title = if (setting.title.isBlank()) "信息驿站: ${msgInfo.from}" else setting.title
        val content = msgInfo.content

        val parsed = parseBasicAuthUrl(setting.server)
        val url = parsed.first
        val basicAuth = parsed.second

        val json = SenderWireJson.encode(
            buildJsonObject {
                put("title", title)
                put("body", content)
                put("isArchive", 1)
                if (!TextUtils.isEmpty(setting.group)) put("group", setting.group)
                if (!TextUtils.isEmpty(setting.icon)) put("icon", setting.icon)
                if (!TextUtils.isEmpty(setting.sound)) put("sound", setting.sound)
                if (!TextUtils.isEmpty(setting.badge)) put("badge", setting.badge)
                if (!TextUtils.isEmpty(setting.url)) put("url", setting.url)
                if (!TextUtils.isEmpty(setting.level)) put("level", setting.level)
                if (!TextUtils.isEmpty(setting.call)) put("call", setting.call)
            },
        )

        // 根据加密模式处理消息
        val requestBody = when (setting.transformation) {
            "AES/GCM/NoPadding" -> {
                if (!AesUtils.isValidKey(setting.key, setting.transformation)) {
                    throw IllegalStateException("Bark GCM 加密密钥无效，需要 Base64 编码的 256 位密钥")
                }
                val result = AesUtils.encryptAesGcm(setting.key, json)
                SLog.i(TAG, "Bark GCM encryption applied, nonce length: ${result.iv.length}")
                SenderWireJson.encode(
                    buildJsonObject {
                        put("ciphertext", result.ciphertext)
                        put("iv", result.iv)
                    },
                )
            }
            "AES/CBC/PKCS5Padding" -> {
                if (!AesUtils.isValidKey(setting.key, setting.transformation)) {
                    throw IllegalStateException("Bark CBC 加密密钥无效，需要 Base64 编码的 256 位密钥")
                }
                if (!AesUtils.isValidIv(setting.iv, setting.transformation)) {
                    throw IllegalStateException("Bark CBC 加密 IV 无效，需要 Base64 编码的 128 位 IV")
                }
                val ciphertext = AesUtils.encryptAesCbc(setting.key, setting.iv, json)
                SLog.i(TAG, "Bark CBC encryption applied")
                SenderWireJson.encode(
                    buildJsonObject {
                        put("ciphertext", ciphertext)
                        put("iv", setting.iv)
                    },
                )
            }
            else -> {
                json
            }
        }

        val request = Request.Builder()
            .url(url)
            .apply {
                if (basicAuth != null) {
                    header("Authorization", basicAuth)
                }
            }
            .post(requestBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                SLog.e(TAG, "Bark send failed: ${response.code} ${response.message} $body")
                throw IllegalStateException("Bark HTTP ${response.code}: ${response.message}")
            }
            val result = SenderWireJson.decodeOrNull<BarkResult>(body)
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
