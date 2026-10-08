package io.github.magisk317.relay.net

import io.github.magisk317.relay.contract.model.ProxyType
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy

actual fun OkHttpClient.Builder.applyProxy(config: ProxyConfig): OkHttpClient.Builder {
    val port = config.port.toIntOrNull()
    if (config.type == ProxyType.DIRECT || config.host.isBlank() || port == null || port <= 0) {
        return this
    }
    val javaType = when (config.type) {
        ProxyType.DIRECT -> Proxy.Type.DIRECT
        ProxyType.HTTP -> Proxy.Type.HTTP
        ProxyType.SOCKS -> Proxy.Type.SOCKS
    }
    proxy(Proxy(javaType, InetSocketAddress(config.host, port)))
    if (config.authenticate && config.username.isNotBlank() && config.password.isNotBlank()) {
        proxyAuthenticator { _, response ->
            response.request.newBuilder()
                .header("Proxy-Authorization", Credentials.basic(config.username, config.password))
                .build()
        }
    }
    return this
}
