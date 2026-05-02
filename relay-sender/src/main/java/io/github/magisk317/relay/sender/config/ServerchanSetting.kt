package io.github.magisk317.relay.sender.config

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class ServerchanSetting(
    @SerializedName(value = "sendKey", alternate = ["o"])
    var sendKey: String = "",
    @SerializedName(value = "channel", alternate = ["p"])
    var channel: String = "",
    @SerializedName(value = "openid", alternate = ["q"])
    var openid: String = "",
    @SerializedName(value = "titleTemplate", alternate = ["r"])
    var titleTemplate: String = "",
) : Serializable
