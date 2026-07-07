package io.github.magisk317.relay.contract.repository

import io.github.magisk317.relay.contract.model.LocalConfigRevision
import io.github.magisk317.relay.contract.model.LocalConfigMirror
import io.github.magisk317.relay.contract.model.LocalDirtyState
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

interface LocalConfigRepository {
    suspend fun exportMirror(): LocalConfigMirror
    suspend fun getRevision(): LocalConfigRevision
    suspend fun getDirtyState(): LocalDirtyState
    suspend fun noteLocalMutation(source: String): LocalDirtyState
    suspend fun markMirrorSynced(revision: Long): LocalDirtyState
    suspend fun applyMirror(mirrorContent: JsonObject, revision: Long, source: String = "remote_command"): LocalConfigMirror
    fun observeMutationSources(): Flow<String>
}

class LocalConfigMirrorRejectedException(
    val reason: String,
) : IllegalStateException(reason)
