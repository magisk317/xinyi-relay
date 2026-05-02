package io.github.magisk317.relay.sender.config

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class DingtalkGroupRobotSetting(
    @SerializedName(value = "token", alternate = ["o"])
    var token: String = "",
    @SerializedName(value = "secret", alternate = ["p"])
    var secret: String = "",
    @SerializedName(value = "atAll", alternate = ["q"])
    var atAll: Boolean = false,
    @SerializedName(value = "atMobiles", alternate = ["r"])
    var atMobiles: String = "",
    @SerializedName(value = "atDingtalkIds", alternate = ["s"])
    var atDingtalkIds: String = "",
    @SerializedName(value = "msgtype", alternate = ["t"])
    var msgtype: String = "text",
    @SerializedName(value = "titleTemplate", alternate = ["u"])
    val titleTemplate: String = "",
) : Serializable {

}
