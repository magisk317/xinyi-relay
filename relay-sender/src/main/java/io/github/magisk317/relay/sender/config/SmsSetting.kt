package io.github.magisk317.relay.sender.config

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class SmsSetting(
    @SerializedName(value = "simSlot", alternate = ["o"])
    var simSlot: Int = 0,
    @SerializedName(value = "mobiles", alternate = ["p"])
    var mobiles: String = "",
    @SerializedName(value = "onlyNoNetwork", alternate = ["q"])
    var onlyNoNetwork: Boolean = false,
) : Serializable {

}
