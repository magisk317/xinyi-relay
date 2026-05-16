package io.github.magisk317.relay.sender.config

import kotlinx.serialization.Serializable as KotlinSerializable
import java.io.Serializable


@KotlinSerializable
data class NtfySetting(
    var server: String = "",
    var topic: String = "",
    var token: String = "",
    var title: String = "",
    var priority: String = "3",
    var tags: String = "",
) : Serializable
