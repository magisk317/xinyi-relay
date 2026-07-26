package io.github.magisk317.relay.net

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import okhttp3.Credentials
import okhttp3.OkHttpClient

data class BasicAuthUrl(
    val url: String,
    val authorization: String?,
)

fun parseBasicAuthUrl(url: String): BasicAuthUrl = runCatching {
    val parsedUrl = URL(url)
    val userInfo = parsedUrl.userInfo
    if (userInfo.isNullOrBlank()) {
        BasicAuthUrl(url, null)
    } else {
        val credentials = userInfo.split(":", limit = 2)
        val cleanUrl = URL(parsedUrl.protocol, parsedUrl.host, parsedUrl.port, parsedUrl.file).toString()
        BasicAuthUrl(
            url = cleanUrl,
            authorization = Credentials.basic(
                credentials.getOrElse(0) { "" },
                credentials.getOrElse(1) { "" },
            ),
        )
    }
}.getOrElse { BasicAuthUrl(url, null) }

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
