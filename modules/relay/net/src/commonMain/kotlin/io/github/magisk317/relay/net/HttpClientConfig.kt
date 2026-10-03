package io.github.magisk317.relay.net

import java.net.InetSocketAddress
import java.net.Proxy
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
    val type: Proxy.Type,
    val host: String,
    val port: String,
    val authenticate: Boolean = false,
    val username: String = "",
    val password: String = "",
)

fun OkHttpClient.Builder.applyProxy(config: ProxyConfig): OkHttpClient.Builder {
    val port = config.port.toIntOrNull()
    if (config.type == Proxy.Type.DIRECT || config.host.isBlank() || port == null || port <= 0) {
        return this
    }

    proxy(Proxy(config.type, InetSocketAddress(config.host, port)))
    if (config.authenticate && config.username.isNotBlank() && config.password.isNotBlank()) {
        proxyAuthenticator { _, response ->
            response.request.newBuilder()
                .header("Proxy-Authorization", Credentials.basic(config.username, config.password))
                .build()
        }
    }
    return this
}
