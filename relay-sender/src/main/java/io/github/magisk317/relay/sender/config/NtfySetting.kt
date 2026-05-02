package io.github.magisk317.relay.sender.config

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class NtfySetting(
    @SerializedName(value = "server", alternate = ["o"])
    var server: String = "",
    @SerializedName(value = "topic", alternate = ["p"])
    var topic: String = "",
    @SerializedName(value = "token", alternate = ["q"])
    var token: String = "",
    @SerializedName(value = "title", alternate = ["r"])
    var title: String = "",
    @SerializedName(value = "priority", alternate = ["s"])
    var priority: String = "3",
    @SerializedName(value = "tags", alternate = ["t"])
    var tags: String = "",
) : Serializable
