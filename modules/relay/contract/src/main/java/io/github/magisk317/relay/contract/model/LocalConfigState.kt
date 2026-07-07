package io.github.magisk317.relay.contract.model

import kotlinx.serialization.json.JsonObject

data class LocalConfigRevision(
    val value: Long,
)

data class LocalDirtyState(
    val revision: LocalConfigRevision,
    val pendingLocalChanges: Int,
    val dirty: Boolean,
)

data class LocalConfigMirror(
    val revision: LocalConfigRevision,
    val dirtyState: LocalDirtyState,
    val content: JsonObject,
)
