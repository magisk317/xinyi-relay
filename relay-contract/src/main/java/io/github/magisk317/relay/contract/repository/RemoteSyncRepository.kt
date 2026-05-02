package io.github.magisk317.relay.contract.repository

import io.github.magisk317.relay.contract.model.RemoteConfigSnapshot
import io.github.magisk317.relay.contract.settings.RemoteAgentSnapshot

interface RemoteSyncRepository {
    suspend fun getSnapshot(): RemoteAgentSnapshot
    suspend fun updateBackendBaseUrl(baseUrl: String): RemoteAgentSnapshot
    suspend fun clearBinding(): RemoteAgentSnapshot
    suspend fun bindDevice(bindCode: String): RemoteAgentSnapshot
    suspend fun sendHeartbeat(): RemoteAgentSnapshot
    suspend fun pullConfigSnapshot(): RemoteConfigSnapshot
    suspend fun pushConfigSnapshot(): RemoteAgentSnapshot
    suspend fun uploadRecentRecords(limit: Int = 100): RemoteAgentSnapshot
    suspend fun noteLocalMutation(source: String)
    fun scheduleRecordUpload(reason: String)
    fun scheduleMessageTriggeredSync(reason: String)
    suspend fun startupSync()
    fun onAppForegrounded()
    fun onAppBackgrounded()
}
