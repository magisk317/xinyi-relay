package io.github.magisk317.relay.sender.config

import kotlinx.serialization.Serializable as KotlinSerializable
import java.io.Serializable


@KotlinSerializable
data class UrlSchemeSetting(
    var urlScheme: String = "",
) : Serializable
