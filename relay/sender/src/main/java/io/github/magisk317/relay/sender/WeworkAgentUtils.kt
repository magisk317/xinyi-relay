package io.github.magisk317.relay.sender

import android.text.TextUtils
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.net.RelayHttpClients
import io.github.magisk317.relay.sender.result.WeworkAgentResult
import io.github.magisk317.relay.sender.config.WeworkAgentSetting
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap

object WeworkAgentUtils {
    private const val TAG = "WeworkAgentUtils"

    private data class TokenCache(val token: String, val expiresAt: Long)
    private val tokenCache = ConcurrentHashMap<String, TokenCache>()

    suspend fun sendMsg(setting: WeworkAgentSetting, msgInfo: MsgInfo) {
        val cacheKey = "${setting.corpID}:${setting.agentID}"
        val now = System.currentTimeMillis()
        var token = tokenCache[cacheKey]?.takeIf { it.expiresAt > now }?.token
        if (token.isNullOrBlank()) {
            token = fetchToken(setting) ?: throw IllegalStateException("企业微信应用获取 token 失败")
        }
        sendText(setting, token, msgInfo)
    }

    private fun fetchToken(setting: WeworkAgentSetting): String? {
        val client = buildClient(setting)
        val base = if (setting.customizeAPI.isBlank()) "https://qyapi.weixin.qq.com" else setting.customizeAPI
        val url = "$base/cgi-bin/gettoken".toHttpUrl().newBuilder()
            .addQueryParameter("corpid", setting.corpID)
            .addQueryParameter("corpsecret", setting.secret)
            .build()

        val request = Request.Builder().url(url).get().build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    SLog.e(TAG, "Get token failed: ${response.code} ${response.message} $body")
                    throw IllegalStateException("企业微信应用 token HTTP ${response.code}: ${response.message}")
                }
                val result = SenderWireJson.decode<WeworkAgentResult>(body)
                val accessToken = result.access_token.orEmpty()
                if (result.errcode == 0L && accessToken.isNotBlank()) {
                    val expires = (result.expires_in ?: 7200L)
                    tokenCache["${setting.corpID}:${setting.agentID}"] = TokenCache(
                        token = accessToken,
                        expiresAt = System.currentTimeMillis() + (expires - 120) * 1000,
                    )
                    accessToken
                } else {
                    SLog.e(TAG, "Get token response unexpected: $body")
                    throw IllegalStateException("企业微信应用 token 返回失败: $body")
                }
            }
        }.onFailure {
            SLog.e(TAG, "Get token exception", it)
        }.getOrElse { throw it }
    }

    private fun sendText(setting: WeworkAgentSetting, token: String, msgInfo: MsgInfo) {
        val client = buildClient(setting)
        val base = if (setting.customizeAPI.isBlank()) "https://qyapi.weixin.qq.com" else setting.customizeAPI
        val url = "$base/cgi-bin/message/send?access_token=$token"

        val content = msgInfo.content
        val json = SenderWireJson.encode(
            buildJsonObject {
                put("touser", setting.toUser)
                put("toparty", setting.toParty)
                put("totag", setting.toTag)
                put("msgtype", "text")
                put("agentid", setting.agentID)
                put(
                    "text",
                    buildJsonObject {
                        put("content", content)
                    },
                )
            },
        )
        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    SLog.e(TAG, "Wework agent send failed: ${response.code} ${response.message} $body")
                    throw IllegalStateException("企业微信应用发送 HTTP ${response.code}: ${response.message}")
                }
                val result = SenderWireJson.decodeOrNull<WeworkAgentResult>(body)
                if (result?.errcode == 0L) {
                    SLog.i(TAG, "Wework agent send success")
                } else {
                    SLog.e(TAG, "Wework agent response unexpected: $body")
                    throw IllegalStateException("企业微信应用发送返回失败: $body")
                }
            }
        }.onFailure {
            SLog.e(TAG, "Wework agent send exception", it)
        }.getOrElse { throw it }
    }

    private fun buildClient(setting: WeworkAgentSetting): OkHttpClient {
        val builder = RelayHttpClients.newBuilder()
        if ((setting.proxyType == Proxy.Type.HTTP || setting.proxyType == Proxy.Type.SOCKS)
            && !TextUtils.isEmpty(setting.proxyHost)
            && !TextUtils.isEmpty(setting.proxyPort)
        ) {
            val port = setting.proxyPort.toIntOrNull() ?: 0
            if (port > 0) {
                builder.proxy(Proxy(setting.proxyType, InetSocketAddress(setting.proxyHost, port)))
                if (setting.proxyAuthenticator
                    && setting.proxyUsername.isNotBlank()
                    && setting.proxyPassword.isNotBlank()
                ) {
                    builder.proxyAuthenticator(Authenticator { _, response ->
                        val credential = Credentials.basic(setting.proxyUsername, setting.proxyPassword)
                        response.request.newBuilder().header("Proxy-Authorization", credential).build()
                    })
                }
            }
        }
        return builder.build()
    }
}
