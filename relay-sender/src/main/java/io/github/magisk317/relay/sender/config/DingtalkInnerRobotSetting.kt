package io.github.magisk317.relay.sender.config

import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.net.Proxy

data class DingtalkInnerRobotSetting(
    @SerializedName(value = "agentID", alternate = ["o"])
    val agentID: String = "",
    @SerializedName(value = "appKey", alternate = ["p"])
    val appKey: String = "",
    @SerializedName(value = "appSecret", alternate = ["q"])
    val appSecret: String = "",
    @SerializedName(value = "userIds", alternate = ["r"])
    val userIds: String = "",
    @SerializedName(value = "msgKey", alternate = ["s"])
    val msgKey: String = "sampleText",
    @SerializedName(value = "titleTemplate", alternate = ["t"])
    val titleTemplate: String = "",
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
