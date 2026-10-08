package io.github.magisk317.relay.sender.config

import kotlinx.serialization.Serializable as KotlinSerializable


@KotlinSerializable
data class ServerchanSetting(
    var sendKey: String = "",
    var channel: String = "",
    var openid: String = "",
    var titleTemplate: String = "",
)
