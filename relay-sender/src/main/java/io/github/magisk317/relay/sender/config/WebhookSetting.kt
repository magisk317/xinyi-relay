package io.github.magisk317.relay.sender.config

import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.net.Proxy

data class WebhookSetting(
    @SerializedName(value = "method", alternate = ["o"])
    val method: String = "POST",
    @SerializedName(value = "webServer", alternate = ["p"])
    var webServer: String = "",
    @SerializedName(value = "secret", alternate = ["q"])
    val secret: String = "",
    @SerializedName(value = "response", alternate = ["r"])
    val response: String = "",
    @SerializedName(value = "webParams", alternate = ["s"])
    val webParams: String = "",
    @SerializedName(value = "headers", alternate = ["t"])
    val headers: Map<String, String> = mapOf(),
    @SerializedName(value = "proxyType", alternate = ["u"])
    val proxyType: Proxy.Type = Proxy.Type.DIRECT,
    @SerializedName(value = "proxyHost", alternate = ["v"])
    val proxyHost: String = "",
    @SerializedName(value = "proxyPort", alternate = ["w"])
    val proxyPort: String = "",
    @SerializedName(value = "proxyAuthenticator", alternate = ["x"])
    val proxyAuthenticator: Boolean = false,
    @SerializedName(value = "proxyUsername", alternate = ["y"])
    val proxyUsername: String = "",
    @SerializedName(value = "proxyPassword", alternate = ["z"])
    val proxyPassword: String = "",
) : Serializable {
}
