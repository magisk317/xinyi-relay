package io.github.magisk317.relay.sender.config

import kotlinx.serialization.Serializable as KotlinSerializable
import java.io.Serializable

@KotlinSerializable
data class PushdeerSetting(
    var server: String = "https://api2.pushdeer.com",
    var pushkey: String = "",
    val type: String = "markdown",
    val titleTemplate: String = "",
) : Serializable
