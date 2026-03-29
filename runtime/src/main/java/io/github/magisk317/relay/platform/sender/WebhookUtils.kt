package io.github.magisk317.relay.platform.sender

import android.text.TextUtils
import android.util.Base64
import io.github.magisk317.relay.domain.model.MsgInfo
import io.github.magisk317.relay.platform.sender.config.WebhookSetting
import io.github.magisk317.relay.domain.sender.SenderSettingSanitizer
import io.github.magisk317.relay.runtime.BuildConfig
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WebhookUtils {
    private const val TAG = "WebhookUtils"
    private const val MAX_ATTEMPTS = 2
    private const val RETRY_DELAY_MS = 400L
    private val client = OkHttpClient.Builder().build()

    private val receiveTimeTag = Regex("\\[receive_time(:(.*?))?]")

    suspend fun sendMsg(setting: WebhookSetting, msgInfo: MsgInfo, traceId: String? = null) = withContext(Dispatchers.IO) {
        val safeSetting = SenderSettingSanitizer.sanitizeWebhookSetting(setting)
        var requestUrl: String = safeSetting.webServer
        val from: String = msgInfo.from
        val content: String = msgInfo.content
        val orgContent: String = msgInfo.content
        val simInfo: String = msgInfo.simInfo
        val timestamp = System.currentTimeMillis()
        val method = safeSetting.method.ifBlank { "POST" }.uppercase(Locale.ROOT)
        fun t(message: String): String = if (traceId.isNullOrBlank()) message else "[trace=$traceId] $message"

        if (!BuildConfig.ALLOW_HTTP_WEBHOOK && requestUrl.trim().startsWith("http://", ignoreCase = true)) {
            SLog.w(TAG, t("Webhook blocked: cleartext http is disabled in this flavor"))
            throw IllegalStateException("当前构建版本仅支持 HTTPS Webhook 地址")
        }

        var sign = ""
        if (!TextUtils.isEmpty(safeSetting.secret)) {
            val stringToSign = "$timestamp\n" + safeSetting.secret
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(safeSetting.secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
            val signData = mac.doFinal(stringToSign.toByteArray(StandardCharsets.UTF_8))
            sign = URLEncoder.encode(String(Base64.encode(signData, Base64.NO_WRAP)), "UTF-8")
        }

        val regex = "^(https?://)([^:]+):([^@]+)@(.+)".toRegex(RegexOption.IGNORE_CASE)
        val matches = regex.find(requestUrl)
        var basicAuthUser: String? = null
        var basicAuthPassword: String? = null
        if (matches != null) {
            val groupValues = matches.groupValues
            if (groupValues.size >= 5) {
                basicAuthUser = groupValues[2]
                basicAuthPassword = groupValues[3]
                requestUrl = groupValues[1] + groupValues[4]
            }
        }

        fun appendSignQuery(url: String): String {
            if (sign.isEmpty()) return url
            return if (url.contains("?")) {
                "$url&timestamp=$timestamp&sign=$sign"
            } else {
                "$url?timestamp=$timestamp&sign=$sign"
            }
        }

        fun applyTemplate(raw: String, urlEncode: Boolean = false, escapeForJson: Boolean = false): String {
            fun encodeIfNeeded(value: String): String {
                if (!urlEncode) return value
                return URLEncoder.encode(value, "UTF-8")
            }

            fun jsonIfNeeded(value: String): String {
                if (!escapeForJson) return value
                return escapeJson(value)
            }

            val receiveTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(msgInfo.date)
            val replaced = raw
                .replace("[from]", jsonIfNeeded(encodeIfNeeded(from)))
                .replace("[content]", jsonIfNeeded(encodeIfNeeded(content)))
                .replace("[msg]", jsonIfNeeded(encodeIfNeeded(content)))
                .replace("[org_content]", jsonIfNeeded(encodeIfNeeded(orgContent)))
                .replace("[title]", jsonIfNeeded(encodeIfNeeded(simInfo)))
                .replace("[card_slot]", jsonIfNeeded(encodeIfNeeded(simInfo)))
                .replace("[timestamp]", encodeIfNeeded(timestamp.toString()))
                .replace("[sign]", encodeIfNeeded(sign))
                .replace(receiveTimeTag) {
                    val format = it.groups[2]?.value?.removePrefix(":") ?: "yyyy-MM-dd HH:mm:ss"
                    val dateText = runCatching { SimpleDateFormat(format, Locale.getDefault()).format(msgInfo.date) }
                        .getOrElse { receiveTime }
                    encodeIfNeeded(dateText)
                }
            return if (urlEncode) replaced.replace("\n", "%0A") else replaced
        }

        val headersMap = safeSetting.headers
        val contentType = headersMap.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value
            ?.trim()
            ?.lowercase()
            .orEmpty()
        val hasJsonContentType = contentType.contains("application/json")
        val isTextContentType = contentType.startsWith("text/")
        val methodNeedsBody = method == "POST" || method == "PUT" || method == "PATCH"

        val headersBuilder = Headers.Builder()
        for ((key, value) in headersMap) {
            headersBuilder.add(key, value)
        }
        if (basicAuthUser != null && basicAuthPassword != null &&
            headersMap.keys.none { it.equals("Authorization", ignoreCase = true) }
        ) {
            headersBuilder.add("Authorization", Credentials.basic(basicAuthUser, basicAuthPassword))
        }

        if (method == "GET") {
            val webParams = safeSetting.webParams.trim()
            requestUrl = if (webParams.isBlank()) {
                val withDefaults = if (requestUrl.contains("?")) {
                    "$requestUrl&from=${URLEncoder.encode(from, "UTF-8")}&content=${URLEncoder.encode(content, "UTF-8")}"
                } else {
                    "$requestUrl?from=${URLEncoder.encode(from, "UTF-8")}&content=${URLEncoder.encode(content, "UTF-8")}"
                }
                appendSignQuery(withDefaults)
            } else {
                val renderedParams = applyTemplate(webParams, urlEncode = true)
                if (renderedParams.startsWith("/")) {
                    requestUrl + renderedParams
                } else {
                    if (requestUrl.contains("?")) "$requestUrl&$renderedParams" else "$requestUrl?$renderedParams"
                }
            }

            SLog.d(
                TAG,
                t(
                    "Webhook request prepared: method=$method " +
                    "url=${sanitizeUrlForLog(requestUrl)} " +
                    "contentType=${if (contentType.isBlank()) "<default>" else contentType} " +
                    "headers=${sanitizeHeadersForLog(headersMap)} body=<none>",
                ),
            )

            val request = Request.Builder()
                .url(requestUrl)
                .headers(headersBuilder.build())
                .get()
                .build()

            executeRequestWithRetry(
                request = request,
                traceId = traceId,
                method = method,
                successPrefix = "Webhook GET Success",
                failurePrefix = "Webhook GET Failed",
            )
        } else {
            if (!methodNeedsBody) {
                throw IllegalStateException("Webhook 不支持的请求方法: $method")
            }

            val webParams = safeSetting.webParams.trim()
            val useRawBody = webParams.isNotBlank() && (hasJsonContentType || isTextContentType || webParams.startsWith("{"))
            val requestBuilder = Request.Builder()
                .url(requestUrl)
                .headers(headersBuilder.build())
            var bodyPreviewForLog = "<empty>"

            if (useRawBody) {
                val bodyContent = applyTemplate(
                    raw = webParams,
                    urlEncode = false,
                    escapeForJson = hasJsonContentType || webParams.startsWith("{"),
                )
                bodyPreviewForLog = bodyContent

                val mediaType = when {
                    isTextContentType -> contentType.toMediaType()
                    hasJsonContentType || webParams.startsWith("{") -> "application/json; charset=utf-8".toMediaType()
                    else -> "application/x-www-form-urlencoded; charset=utf-8".toMediaType()
                }
                val body = bodyContent.toRequestBody(mediaType)
                requestBuilder.method(method, body)
            } else {
                val formText = if (webParams.isBlank()) {
                    buildString {
                        append("from=[from]&content=[content]&timestamp=[timestamp]")
                        if (sign.isNotEmpty()) append("&sign=[sign]")
                    }
                } else {
                    webParams
                }

                val formBuilder = FormBody.Builder()
                val formPairsForLog = mutableListOf<String>()
                formText.trim('&').split("&")
                    .filter { it.isNotBlank() }
                    .forEach { pair ->
                        val idx = pair.indexOf("=")
                        if (idx >= 0) {
                            val key = pair.substring(0, idx).trim()
                            val value = pair.substring(idx + 1).trim()
                            val resolvedValue = applyTemplate(value)
                            formBuilder.add(key, resolvedValue)
                            formPairsForLog += "$key=$resolvedValue"
                        }
                    }
                bodyPreviewForLog = formPairsForLog.joinToString("&").ifBlank { "<empty>" }
                requestBuilder.method(method, formBuilder.build())
            }
            SLog.d(
                TAG,
                t(
                    "Webhook request prepared: method=$method " +
                    "url=${sanitizeUrlForLog(requestUrl)} " +
                    "contentType=${if (contentType.isBlank()) "<default>" else contentType} " +
                    "headers=${sanitizeHeadersForLog(headersMap)} " +
                    "body=${sanitizeBodyForLog(bodyPreviewForLog)}",
                ),
            )
            val request = requestBuilder
                .build()

            executeRequestWithRetry(
                request = request,
                traceId = traceId,
                method = method,
                successPrefix = "Webhook POST Success",
                failurePrefix = "Webhook POST Failed",
            )
        }
    }

    private suspend fun executeRequestWithRetry(
        request: Request,
        traceId: String?,
        method: String,
        successPrefix: String,
        failurePrefix: String,
    ) {
        fun t(message: String): String = if (traceId.isNullOrBlank()) message else "[trace=$traceId] $message"
        var attempt = 1
        var lastError: IOException? = null

        while (attempt <= MAX_ATTEMPTS) {
            try {
                client.newCall(request).execute().use { response ->
                    val respBody = response.body.string()
                    if (!response.isSuccessful) {
                        SLog.e(
                            TAG,
                            t(
                                "$failurePrefix: attempt=$attempt http=${response.code} " +
                                    "${response.message} $respBody",
                            ),
                        )
                        throw IllegalStateException("Webhook $method 失败: HTTP ${response.code}")
                    }
                    SLog.i(TAG, t("$successPrefix: ${response.code} attempt=$attempt"))
                    return
                }
            } catch (e: IOException) {
                lastError = e
                val willRetry = attempt < MAX_ATTEMPTS
                SLog.e(
                    TAG,
                    t(
                        "$failurePrefix: attempt=$attempt exception=${e.javaClass.simpleName} " +
                            "message=${e.message ?: "<empty>"} retry=$willRetry",
                    ),
                    e,
                )
                if (!willRetry) {
                    throw e
                }
                delay(RETRY_DELAY_MS)
                attempt++
            }
        }

        throw lastError ?: IllegalStateException("Webhook $method failed without captured IOException")
    }

    // Keep escaping behavior close to SmsForwarder JSON template rendering.
    private fun escapeJson(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun sanitizeUrlForLog(url: String): String = sanitizeTextForLog(url)

    private fun sanitizeBodyForLog(body: String): String = truncateForLog(sanitizeTextForLog(body), 400)

    private fun sanitizeHeadersForLog(headers: Map<String, String>): String {
        if (headers.isEmpty()) return "{}"
        return headers.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            val displayValue = if (isSensitiveHeader(key)) "***" else truncateForLog(value, 120)
            "$key=$displayValue"
        }
    }

    private fun isSensitiveHeader(key: String): Boolean {
        val normalized = key.trim().lowercase(Locale.ROOT)
        return normalized == "authorization" ||
            normalized.contains("token") ||
            normalized.contains("secret") ||
            normalized.contains("sign") ||
            normalized.contains("password")
    }

    private fun sanitizeTextForLog(raw: String): String {
        var text = raw
        val keyValuePattern = Regex("(?i)(access_token|token|secret|sign|authorization|password|passwd|pwd)=([^&\\s,\\\"]+)")
        text = keyValuePattern.replace(text) { match ->
            "${match.groupValues[1]}=***"
        }
        val bearerPattern = Regex("(?i)(bearer\\s+)[A-Za-z0-9._\\-+/=]+")
        text = bearerPattern.replace(text) { match ->
            "${match.groupValues[1]}***"
        }
        return text
    }

    private fun truncateForLog(value: String, max: Int): String {
        if (value.length <= max) return value
        return value.take(max) + "...(len=${value.length})"
    }
}
