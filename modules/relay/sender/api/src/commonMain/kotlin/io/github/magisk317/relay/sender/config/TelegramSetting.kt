package io.github.magisk317.relay.sender.config

import kotlinx.serialization.Serializable as KotlinSerializable
import io.github.magisk317.relay.contract.model.ProxyType


@KotlinSerializable
data class TelegramSetting(
    val apiBase: String = "https://api.telegram.org",
    val method: String = "POST",
    var apiToken: String = "",
    val chatId: String = "",
    val messageThreadId: String = "",
    @KotlinSerializable(with = ProxyTypeSerializer::class)
    val proxyType: ProxyType = ProxyType.DIRECT,
    val proxyHost: String = "",
    val proxyPort: String = "",
    val proxyAuthenticator: Boolean = false,
    val proxyUsername: String = "",
    val proxyPassword: String = "",
    val parseMode: String = "HTML",
) {

}
