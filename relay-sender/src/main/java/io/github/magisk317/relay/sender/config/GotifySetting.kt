package io.github.magisk317.relay.sender.config

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class GotifySetting(
    @SerializedName(value = "webServer", alternate = ["o"])
    var webServer: String = "",
    @SerializedName(value = "title", alternate = ["p"])
    val title: String = "",
    @SerializedName(value = "priority", alternate = ["q"])
    val priority: String = "",
) : Serializable
