package io.github.magisk317.relay.sender.config

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class UrlSchemeSetting(
    @SerializedName(value = "urlScheme", alternate = ["o"])
    var urlScheme: String = "",
) : Serializable
