package io.github.magisk317.relay.sender.config

import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.net.Proxy

data class WeworkAgentSetting(
    @SerializedName(value = "corpID", alternate = ["o"])
    var corpID: String = "",
    @SerializedName(value = "agentID", alternate = ["p"])
    val agentID: String = "",
    @SerializedName(value = "secret", alternate = ["q"])
    val secret: String = "",
    @SerializedName(value = "atAll", alternate = ["r"])
    val atAll: Boolean = false,
    @SerializedName(value = "toUser", alternate = ["s"])
    val toUser: String = "@all",
    @SerializedName(value = "toParty", alternate = ["t"])
    val toParty: String = "",
    @SerializedName(value = "toTag", alternate = ["u"])
    val toTag: String = "",
    @SerializedName(value = "proxyType", alternate = ["v"])
    val proxyType: Proxy.Type = Proxy.Type.DIRECT,
    @SerializedName(value = "proxyHost", alternate = ["w"])
    val proxyHost: String = "",
    @SerializedName(value = "proxyPort", alternate = ["x"])
    val proxyPort: String = "",
    @SerializedName(value = "proxyAuthenticator", alternate = ["y"])
    val proxyAuthenticator: Boolean = false,
    @SerializedName(value = "proxyUsername", alternate = ["z"])
    val proxyUsername: String = "",
    @SerializedName(value = "proxyPassword", alternate = ["A"])
    val proxyPassword: String = "",
    @SerializedName(value = "customizeAPI", alternate = ["B"])
    val customizeAPI: String = "https://qyapi.weixin.qq.com",
) : Serializable {

}
