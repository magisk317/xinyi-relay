package io.github.magisk317.relay.sender.config

import kotlinx.serialization.Serializable as KotlinSerializable
import java.io.Serializable
import java.net.Proxy


@KotlinSerializable
data class WebhookSetting(
    val method: String = "POST",
    var webServer: String = "",
    val secret: String = "",
    val response: String = "",
    val webParams: String = "",
    val headers: Map<String, String> = mapOf(),
    @KotlinSerializable(with = ProxyTypeSerializer::class)
    val proxyType: Proxy.Type = Proxy.Type.DIRECT,
    val proxyHost: String = "",
    val proxyPort: String = "",
    val proxyAuthenticator: Boolean = false,
    val proxyUsername: String = "",
    val proxyPassword: String = "",
) : Serializable {
}
