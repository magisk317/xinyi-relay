package com.github.magisk317.smscode.forwarder.utils.sender

import android.text.TextUtils
import com.github.magisk317.smscode.forwarder.entity.MsgInfo
import com.github.magisk317.smscode.forwarder.entity.result.DingtalkInnerRobotResult
import com.github.magisk317.smscode.forwarder.entity.setting.DingtalkInnerRobotSetting
import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap

object DingtalkInnerRobotUtils {
    private const val TAG = "DingtalkInnerRobotUtils"

    private data class TokenCache(val token: String, val expiresAt: Long)
    private val tokenCache = ConcurrentHashMap<String, TokenCache>()

    suspend fun sendMsg(setting: DingtalkInnerRobotSetting, msgInfo: MsgInfo) {
        val cacheKey = setting.agentID
        val now = System.currentTimeMillis()
        var token = tokenCache[cacheKey]?.takeIf { it.expiresAt > now }?.token
        if (token.isNullOrBlank()) {
            token = fetchToken(setting) ?: throw IllegalStateException("钉钉内部机器人获取 token 失败")
        }
        sendInternal(setting, token, msgInfo)
    }

    private fun fetchToken(setting: DingtalkInnerRobotSetting): String? {
        val client = buildClient(setting)
        val payload = mapOf(
            "appKey" to setting.appKey,
            "appSecret" to setting.appSecret,
        )
        val request = Request.Builder()
            .url("https://api.dingtalk.com/v1.0/oauth2/accessToken")
            .post(Gson().toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    SLog.e(TAG, "Get token failed: ${response.code} ${response.message} $body")
                    throw IllegalStateException("钉钉内部机器人 token HTTP ${response.code}: ${response.message}")
                }
                val result = Gson().fromJson(body, DingtalkInnerRobotResult::class.java)
                if (!result.accessToken.isNullOrBlank()) {
                    val expires = (result.expireIn ?: 7200L)
                    tokenCache[setting.agentID] = TokenCache(
                        token = result.accessToken!!,
                        expiresAt = System.currentTimeMillis() + (expires - 120) * 1000,
                    )
                    result.accessToken
                } else {
                    SLog.e(TAG, "Get token response unexpected: $body")
                    throw IllegalStateException("钉钉内部机器人 token 返回失败: $body")
                }
            }
        }.onFailure {
            SLog.e(TAG, "Get token exception", it)
        }.getOrElse { throw it }
    }

    private fun sendInternal(setting: DingtalkInnerRobotSetting, token: String, msgInfo: MsgInfo) {
        val client = buildClient(setting)
        val msgParam = if (setting.msgKey == "sampleMarkdown") {
            mapOf(
                "title" to "SmsCode: ${msgInfo.from}",
                "text" to msgInfo.content,
            )
        } else {
            mapOf("content" to msgInfo.content)
        }

        val userIds = setting.userIds.replace("[,，;；|]".toRegex(), "|")
            .trim('|')
            .split('|')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val payload = mapOf(
            "robotCode" to setting.appKey,
            "userIds" to userIds,
            "msgKey" to setting.msgKey,
            "msgParam" to Gson().toJson(msgParam),
        )

        val request = Request.Builder()
            .url("https://api.dingtalk.com/v1.0/robot/oToMessages/batchSend")
            .header("x-acs-dingtalk-access-token", token)
            .post(Gson().toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    SLog.e(TAG, "Dingtalk inner send failed: ${response.code} ${response.message} $body")
                    throw IllegalStateException("钉钉内部机器人发送 HTTP ${response.code}: ${response.message}")
                }
                val result = runCatching { Gson().fromJson(body, DingtalkInnerRobotResult::class.java) }.getOrNull()
                if (!result?.processQueryKey.isNullOrBlank()) {
                    SLog.i(TAG, "Dingtalk inner send success")
                } else {
                    SLog.e(TAG, "Dingtalk inner response unexpected: $body")
                    throw IllegalStateException("钉钉内部机器人发送返回失败: $body")
                }
            }
        }.onFailure {
            SLog.e(TAG, "Dingtalk inner send exception", it)
        }.getOrElse { throw it }
    }

    private fun buildClient(setting: DingtalkInnerRobotSetting): OkHttpClient {
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
