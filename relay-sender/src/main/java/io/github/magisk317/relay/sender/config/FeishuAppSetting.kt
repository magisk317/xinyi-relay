package io.github.magisk317.relay.sender.config

import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Suppress("SENSELESS_COMPARISON")
data class FeishuAppSetting(
    @SerializedName(value = "appId", alternate = ["o"])
    var appId: String = "",
    @SerializedName(value = "appSecret", alternate = ["p"])
    val appSecret: String = "",
    @SerializedName(value = "receiveId", alternate = ["q"])
    val receiveId: String = "",
    @SerializedName(value = "msgType", alternate = ["r"])
    val msgType: String = "interactive",
    @SerializedName(value = "titleTemplate", alternate = ["s"])
    val titleTemplate: String = "",
    @SerializedName(value = "receiveIdType", alternate = ["t"])
    val receiveIdType: String = "user_id",
    @SerializedName(value = "messageCard", alternate = ["u"])
    val messageCard: String = "", //自定义消息卡片
) : Serializable {

}
