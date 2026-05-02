package io.github.magisk317.relay.contract.model

import com.google.gson.JsonObject

data class RemoteConfigSnapshot(
    val revision: Long,
    val content: JsonObject,
)
