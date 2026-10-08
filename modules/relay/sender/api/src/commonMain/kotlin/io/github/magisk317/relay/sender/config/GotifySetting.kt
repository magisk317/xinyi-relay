package io.github.magisk317.relay.sender.config

import kotlinx.serialization.Serializable as KotlinSerializable


@KotlinSerializable
data class GotifySetting(
    var webServer: String = "",
    val title: String = "",
    val priority: String = "",
)
