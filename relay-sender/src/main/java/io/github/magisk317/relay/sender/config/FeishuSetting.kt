package io.github.magisk317.relay.sender.config

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class FeishuSetting(
    @SerializedName(value = "webhook", alternate = ["o"])
    var webhook: String = "",
    @SerializedName(value = "secret", alternate = ["p"])
    val secret: String = "",
    @SerializedName(value = "msgType", alternate = ["q"])
    val msgType: String = "interactive",
    @SerializedName(value = "titleTemplate", alternate = ["r"])
    val titleTemplate: String = "",
    @SerializedName(value = "messageCard", alternate = ["s"])
    val messageCard: String = "", //自定义消息卡片
) : Serializable {

}
