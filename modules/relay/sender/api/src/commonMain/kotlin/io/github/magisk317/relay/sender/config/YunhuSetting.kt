package io.github.magisk317.relay.sender.config

import kotlinx.serialization.Serializable as KotlinSerializable
import java.io.Serializable


@KotlinSerializable
data class YunhuSetting(
    var token: String = "",
    var recvId: String = "",
    var recvType: String = "user",
    var contentType: String = "text",
    val titleTemplate: String = "",
) : Serializable
