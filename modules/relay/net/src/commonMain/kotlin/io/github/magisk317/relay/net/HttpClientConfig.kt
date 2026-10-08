package io.github.magisk317.relay.net

import io.github.magisk317.relay.contract.model.ProxyType
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

data class BasicAuthUrl(
    val url: String,
    val authorization: String?,
)

fun parseBasicAuthUrl(url: String): BasicAuthUrl {
    // Trim before parsing, otherwise a pasted URL with surrounding whitespace fails
    // to parse here and is handed back with its credentials still embedded.
    val parsedUrl = url.trim().toHttpUrlOrNull() ?: return BasicAuthUrl(url, null)
    val username = parsedUrl.username
    val password = parsedUrl.password
    if (username.isEmpty() && password.isEmpty()) {
        return BasicAuthUrl(url, null)
    }
    return BasicAuthUrl(
        url = parsedUrl.newBuilder().username("").password("").build().toString(),
        authorization = Credentials.basic(username, password),
    )
}

data class ProxyConfig(
    val type: ProxyType,
    val host: String,
    val port: String,
    val authenticate: Boolean = false,
    val username: String = "",
    val password: String = "",
)

expect fun OkHttpClient.Builder.applyProxy(config: ProxyConfig): OkHttpClient.Builder
