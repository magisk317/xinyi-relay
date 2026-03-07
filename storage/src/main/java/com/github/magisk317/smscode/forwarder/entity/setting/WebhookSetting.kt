package com.github.magisk317.smscode.forwarder.entity.setting

import java.io.Serializable
import java.net.Proxy

data class WebhookSetting(
    val method: String = "POST",
    var webServer: String = "",
    val secret: String = "",
    val response: String = "",
    val webParams: String = "",
    val headers: Map<String, String> = mapOf(),
    val proxyType: Proxy.Type = Proxy.Type.DIRECT,
    val proxyHost: String = "",
    val proxyPort: String = "",
    val proxyAuthenticator: Boolean = false,
    val proxyUsername: String = "",
    val proxyPassword: String = "",
) : Serializable {
}
