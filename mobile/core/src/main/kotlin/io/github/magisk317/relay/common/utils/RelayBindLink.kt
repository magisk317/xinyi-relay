package io.github.magisk317.relay.common.utils

import android.net.Uri

/** Bind credentials carried by the `xinyi-relay://bind` QR code. */
data class RelayBindPayload(
    val bindCode: String,
    val baseUrl: String,
)

object RelayBindLink {
    const val SCHEME = "xinyi-relay"
    const val HOST = "bind"

    private const val QUERY_CODE = "code"
    private const val QUERY_BASE_URL = "base_url"

    fun matches(uri: Uri?): Boolean = uri != null && uri.scheme == SCHEME && uri.host == HOST

    fun fromUri(uri: Uri?): RelayBindPayload? {
        if (!matches(uri) || uri == null) return null
        val bindCode = uri.getQueryParameter(QUERY_CODE).orEmpty().trim()
        if (bindCode.isBlank()) return null
        return RelayBindPayload(
            bindCode = bindCode,
            baseUrl = uri.getQueryParameter(QUERY_BASE_URL).orEmpty().trim(),
        )
    }

    /** A QR that is not a bind link carries the bare bind code, so fall back to that reading. */
    fun parse(raw: String): RelayBindPayload {
        val trimmed = raw.trim()
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
        return fromUri(uri) ?: RelayBindPayload(bindCode = trimmed, baseUrl = "")
    }
}
