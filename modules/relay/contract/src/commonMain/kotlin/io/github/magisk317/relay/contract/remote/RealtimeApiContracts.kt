package io.github.magisk317.relay.contract.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

object RealtimeEventTypes {
    val ALL: List<String> = listOf(
        "device.registered",
        "device.updated",
        "device.revoked",
        "device.heartbeat",
        "device.config.updated",
        "device.config.command.updated",
        "records.ingested",
    )
}

@Serializable
data class RealtimeEvent(
    val type: String,
    val time: String,
    val data: JsonObject? = null,
)
