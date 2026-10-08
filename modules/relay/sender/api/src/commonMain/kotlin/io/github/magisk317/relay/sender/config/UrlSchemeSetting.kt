package io.github.magisk317.relay.sender.config

import kotlinx.serialization.Serializable as KotlinSerializable


@KotlinSerializable
data class UrlSchemeSetting(
    var urlScheme: String = "",
)
