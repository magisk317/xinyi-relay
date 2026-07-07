package io.github.magisk317.relay.contract.repository

import io.github.magisk317.relay.contract.model.LocalConfigMirror
import io.github.magisk317.relay.contract.settings.RemoteAgentSnapshot

interface ConfigSyncCoordinator {
    suspend fun getSnapshot(): RemoteAgentSnapshot
    suspend fun updateBackendBaseUrl(baseUrl: String): RemoteAgentSnapshot
    suspend fun clearBinding(): RemoteAgentSnapshot
    suspend fun bindDevice(bindCode: String): RemoteAgentSnapshot
    suspend fun sendHeartbeat(): RemoteAgentSnapshot
    suspend fun pullPendingCommands(): LocalConfigMirror
    suspend fun pushLocalMirror(): RemoteAgentSnapshot
    suspend fun uploadRecentRecords(limit: Int = 100): RemoteAgentSnapshot
    fun scheduleRecordUpload(reason: String)
    fun scheduleMessageTriggeredSync(reason: String)
    suspend fun startupSync()
    fun onAppForegrounded()
    fun onAppBackgrounded()
}
