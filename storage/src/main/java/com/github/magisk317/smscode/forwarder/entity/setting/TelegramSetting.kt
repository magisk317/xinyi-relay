package com.github.magisk317.smscode.forwarder.entity.setting

import java.io.Serializable
import java.net.Proxy
import com.google.gson.annotations.SerializedName

data class TelegramSetting(
    val method: String = "POST",
    var apiToken: String = "",
    val chatId: String = "",
    @SerializedName(value = "messageThreadId", alternate = ["topicId", "topic_id", "message_thread_id"])
    val messageThreadId: String = "",
    val proxyType: Proxy.Type = Proxy.Type.DIRECT,
    val proxyHost: String = "",
    val proxyPort: String = "",
    val proxyAuthenticator: Boolean = false,
    val proxyUsername: String = "",
    val proxyPassword: String = "",
    val parseMode: String = "HTML",
) : Serializable {

}
