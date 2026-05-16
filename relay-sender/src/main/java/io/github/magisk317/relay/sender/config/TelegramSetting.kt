package io.github.magisk317.relay.sender.config

import kotlinx.serialization.Serializable as KotlinSerializable
import java.io.Serializable
import java.net.Proxy


@KotlinSerializable
data class TelegramSetting(
    val method: String = "POST",
    var apiToken: String = "",
    val chatId: String = "",
    val messageThreadId: String = "",
    @KotlinSerializable(with = ProxyTypeSerializer::class)
    val proxyType: Proxy.Type = Proxy.Type.DIRECT,
    val proxyHost: String = "",
    val proxyPort: String = "",
    val proxyAuthenticator: Boolean = false,
    val proxyUsername: String = "",
    val proxyPassword: String = "",
    val parseMode: String = "HTML",
) : Serializable {

}
