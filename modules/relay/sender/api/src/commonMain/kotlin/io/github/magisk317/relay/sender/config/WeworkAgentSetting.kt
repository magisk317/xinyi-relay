package io.github.magisk317.relay.sender.config

import kotlinx.serialization.Serializable as KotlinSerializable
import java.io.Serializable
import java.net.Proxy


@KotlinSerializable
data class WeworkAgentSetting(
    var corpID: String = "",
    val agentID: String = "",
    val secret: String = "",
    val atAll: Boolean = false,
    val toUser: String = "@all",
    val toParty: String = "",
    val toTag: String = "",
    @KotlinSerializable(with = ProxyTypeSerializer::class)
    val proxyType: Proxy.Type = Proxy.Type.DIRECT,
    val proxyHost: String = "",
    val proxyPort: String = "",
    val proxyAuthenticator: Boolean = false,
    val proxyUsername: String = "",
    val proxyPassword: String = "",
    val customizeAPI: String = "https://qyapi.weixin.qq.com",
) : Serializable {

}
