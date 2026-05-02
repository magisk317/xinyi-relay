package io.github.magisk317.relay.sender.config

import java.io.Serializable
import java.net.Proxy
import com.google.gson.annotations.SerializedName

data class TelegramSetting(
    @SerializedName(value = "method", alternate = ["o"])
    val method: String = "POST",
    @SerializedName(value = "apiToken", alternate = ["p"])
    var apiToken: String = "",
    @SerializedName(value = "chatId", alternate = ["q"])
    val chatId: String = "",
    @SerializedName(value = "messageThreadId", alternate = ["topicId", "topic_id", "message_thread_id", "r"])
    val messageThreadId: String = "",
    @SerializedName(value = "proxyType", alternate = ["s"])
    val proxyType: Proxy.Type = Proxy.Type.DIRECT,
    @SerializedName(value = "proxyHost", alternate = ["t"])
    val proxyHost: String = "",
    @SerializedName(value = "proxyPort", alternate = ["u"])
    val proxyPort: String = "",
    @SerializedName(value = "proxyAuthenticator", alternate = ["v"])
    val proxyAuthenticator: Boolean = false,
    @SerializedName(value = "proxyUsername", alternate = ["w"])
    val proxyUsername: String = "",
    @SerializedName(value = "proxyPassword", alternate = ["x"])
    val proxyPassword: String = "",
    @SerializedName(value = "parseMode", alternate = ["y"])
    val parseMode: String = "HTML",
) : Serializable {

}
