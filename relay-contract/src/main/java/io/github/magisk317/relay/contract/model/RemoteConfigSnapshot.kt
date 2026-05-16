package io.github.magisk317.relay.contract.model

import kotlinx.serialization.json.JsonObject

data class RemoteConfigSnapshot(
    val revision: Long,
    val content: JsonObject,
)
