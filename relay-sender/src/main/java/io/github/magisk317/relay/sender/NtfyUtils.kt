package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.sender.config.NtfySetting
import io.github.magisk317.relay.sender.SenderSettingSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object NtfyUtils {
    private const val TAG = "NtfyUtils"
    private const val DEFAULT_PRIORITY = "3"
    private val client = OkHttpClient()
    private val textPlain = "text/plain; charset=utf-8".toMediaType()

    suspend fun sendMsg(setting: NtfySetting, msgInfo: MsgInfo) = withContext(Dispatchers.IO) {
        val safeSetting = SenderSettingSanitizer.sanitizeNtfySetting(setting)
        val requestUrl = buildPublishUrl(safeSetting.server, safeSetting.topic)
        val headers = buildHeaders(safeSetting, msgInfo)
        val requestBody = msgInfo.content.toRequestBody(textPlain)
        val requestBuilder = Request.Builder()
            .url(requestUrl)
            .post(requestBody)

        headers.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }
        val request = requestBuilder.build()
        val authMode = if (headers.containsKey("Authorization")) "bearer" else "none"
        SLog.d(
            TAG,
            "Ntfy request prepared: url=$requestUrl priority=${headers["Priority"]} " +
                "tags=${headers["Tags"] ?: "<none>"} auth=$authMode",
        )

        client.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                val bodyPreview = responseBody.take(400)
                SLog.e(TAG, "Ntfy failed: ${response.code} ${response.message} $bodyPreview")
                throw IllegalStateException("ntfy HTTP ${response.code}: ${response.message}")
            }
            SLog.i(TAG, "Ntfy send success: ${response.code}")
        }
    }

    internal fun buildPublishUrl(server: String, topic: String): String {
        val normalizedServer = normalizeServer(server)
        val normalizedTopic = topic.trim()
        if (normalizedTopic.isBlank()) {
            throw IllegalStateException("ntfy topic 不能为空")
        }
        val baseUrl = normalizedServer.toHttpUrlOrNull()
            ?: throw IllegalStateException("ntfy server 地址无效")
        return baseUrl.newBuilder()
            .addPathSegment(normalizedTopic)
            .build()
            .toString()
    }

    internal fun normalizeServer(server: String): String {
        return server.trim().trimEnd('/')
    }

    internal fun normalizePriority(priority: String): String {
        val value = priority.trim()
        if (value.isBlank()) return DEFAULT_PRIORITY
        val parsed = value.toIntOrNull() ?: return DEFAULT_PRIORITY
        return if (parsed in 1..5) parsed.toString() else DEFAULT_PRIORITY
    }

    internal fun normalizeTags(tags: String): String {
        return tags
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(",")
    }

    internal fun buildHeaders(setting: NtfySetting, msgInfo: MsgInfo): Map<String, String> {
        val title = setting.title.trim().ifBlank { "信息驿站: ${msgInfo.from}" }
        val priority = normalizePriority(setting.priority)
        val tags = normalizeTags(setting.tags)
        val token = setting.token.trim()

        return buildMap {
            put("Title", title)
            put("Priority", priority)
            if (tags.isNotBlank()) {
                put("Tags", tags)
            }
            if (token.isNotBlank()) {
                put("Authorization", "Bearer $token")
            }
        }
    }
}
