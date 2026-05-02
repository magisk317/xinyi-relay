package io.github.magisk317.relay.sender.config

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class PushplusSetting(
    @SerializedName(value = "website", alternate = ["o"])
    var website: String = "www.pushplus.plus",
    @SerializedName(value = "token", alternate = ["p"])
    var token: String = "",
    @SerializedName(value = "topic", alternate = ["q"])
    val topic: String = "",
    @SerializedName(value = "template", alternate = ["r"])
    val template: String = "",
    @SerializedName(value = "channel", alternate = ["s"])
    val channel: String = "",
    @SerializedName(value = "webhook", alternate = ["t"])
    val webhook: String = "",
    @SerializedName(value = "callbackUrl", alternate = ["u"])
    val callbackUrl: String = "",
    @SerializedName(value = "validTime", alternate = ["v"])
    val validTime: String = "",
    @SerializedName(value = "titleTemplate", alternate = ["w"])
    val titleTemplate: String = "",
) : Serializable
