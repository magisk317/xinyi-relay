package com.github.magisk317.smscode.forwarder.utils.sender

import android.text.TextUtils
import com.github.magisk317.smscode.forwarder.entity.MsgInfo
import com.github.magisk317.smscode.forwarder.entity.result.WeworkAgentResult
import com.github.magisk317.smscode.forwarder.entity.setting.WeworkAgentSetting
import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
                val result = Gson().fromJson(body, WeworkAgentResult::class.java)
                if (result.errcode == 0L && !result.access_token.isNullOrBlank()) {
                    val expires = (result.expires_in ?: 7200L)
                    tokenCache["${setting.corpID}:${setting.agentID}"] = TokenCache(
                        token = result.access_token!!,
                        expiresAt = System.currentTimeMillis() + (expires - 120) * 1000,
                    )
                    result.access_token
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
        val bodyMap = mutableMapOf<String, Any>(
            "touser" to setting.toUser,
            "toparty" to setting.toParty,
            "totag" to setting.toTag,
            "msgtype" to "text",
            "agentid" to setting.agentID,
            "text" to mapOf("content" to content),
        )

        val json = Gson().toJson(bodyMap)
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
                val result = runCatching { Gson().fromJson(body, WeworkAgentResult::class.java) }.getOrNull()
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
        val builder = OkHttpClient.Builder()
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
