package io.github.magisk317.relay.sender.config

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class WeworkRobotSetting(
    @SerializedName(value = "webHook", alternate = ["o"])
    var webHook: String = "",
    @SerializedName(value = "msgType", alternate = ["p"])
    val msgType: String = "text",
    @SerializedName(value = "atAll", alternate = ["q"])
    var atAll: Boolean = false,
    @SerializedName(value = "atUserIds", alternate = ["r"])
    var atUserIds: String = "",
    @SerializedName(value = "atMobiles", alternate = ["s"])
    var atMobiles: String = "",
) : Serializable {

}
