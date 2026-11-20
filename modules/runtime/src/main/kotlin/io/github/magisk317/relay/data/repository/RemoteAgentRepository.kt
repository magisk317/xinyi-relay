package io.github.magisk317.relay.data.repository

import io.github.magisk317.relay.android.platform.compat.PlatformCompat
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.android.data.secret.InternalSecretStore
import io.github.magisk317.relay.bootstrap.RuntimeDependencies
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.forward.contract.json.RelayJson
import io.github.magisk317.relay.contract.model.LocalConfigMirror
import io.github.magisk317.relay.contract.model.SnapshotScheduledTask
import io.github.magisk317.relay.contract.repository.ConfigSyncCoordinator
import io.github.magisk317.relay.contract.repository.LocalConfigRepository
import io.github.magisk317.relay.contract.repository.LocalConfigMirrorRejectedException
import io.github.magisk317.relay.contract.remote.AgentConfigCommandsAckRequest
import io.github.magisk317.relay.contract.remote.AgentConfigCommandsPullRequest
import io.github.magisk317.relay.contract.remote.AgentConfigMirrorRequest
import io.github.magisk317.relay.contract.remote.AgentRegisterRequest
import io.github.magisk317.relay.contract.remote.DeviceActionAckRequest
import io.github.magisk317.relay.contract.remote.DeviceActionCommandResponse
import io.github.magisk317.relay.contract.remote.DeviceConfigCommandResponse
import io.github.magisk317.relay.contract.remote.DeviceConfigStateResponse
import io.github.magisk317.relay.contract.remote.HeartbeatRequest
import io.github.magisk317.relay.contract.remote.MAX_RELAY_RECORDS_PER_SNAPSHOT
import io.github.magisk317.relay.contract.remote.MAX_RELAY_RECORD_SNAPSHOT_BYTES
import io.github.magisk317.relay.contract.remote.RelayRecordWire
import io.github.magisk317.relay.contract.remote.RelayRecordsBatchRequest
import io.github.magisk317.relay.contract.settings.RemoteAgentSnapshot
import io.github.magisk317.relay.data.remote.DeviceTokenExpiredException
import io.github.magisk317.relay.android.prefs.HookPreferenceMirror
import io.github.magisk317.relay.android.common.utils.DeviceIdentityUtils
import io.github.magisk317.relay.contract.constant.DeviceActionConst
import io.github.magisk317.relay.domain.action.DeviceActionExecutor
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import io.github.magisk317.relay.runtime.BuildConfig
import io.github.magisk317.relay.data.remote.RemoteAgentApi
import io.github.magisk317.relay.data.remote.RemoteApiClient
import io.github.magisk317.xposed.logging.MagiskOtel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.security.MessageDigest
import java.time.Instant
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import io.github.magisk317.xposed.logging.XLog

class RemoteAgentRepository(
    private val appContext: Context,
    private val preferenceDataSource: PreferenceDataSource,
    private val localConfigRepository: LocalConfigRepository,
    private val remoteApiClient: RemoteAgentApi = RemoteApiClient(),
    private val deviceActionExecutor: DeviceActionExecutor = DeviceActionExecutor(appContext),
) : ConfigSyncCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val backgroundSyncInFlight = AtomicBoolean(false)
    private val backgroundSyncQueued = AtomicBoolean(false)

    init {
        scope.launch {
            localConfigRepository.observeMutationSources().collectLatest { source ->
                val snapshot = getSnapshot()
                if (!snapshot.bound) {
                    publishHookPrefs()
                    return@collectLatest
                }
                val dirtyState = localConfigRepository.getDirtyState()
                if (dirtyState.dirty && preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_SYNC_STATE, "idle") == "idle") {
                    setSyncState("dirty")
                }
                publishHookPrefs()
                scheduleBackgroundSync("local_mutation:$source")
            }
        }
    }

    /**
     * Wrap an API call with 401 handling. If the device token is expired/revoked,
     * sets a clear error state telling the user to re-bind.
     */
    private fun emitAgent(
        stage: String,
        result: String,
        reason: String,
        durationMs: Long,
        statusOk: Boolean = true,
        count: Int? = null,
        errorClass: String? = null,
    ) {
        val attrs = mutableMapOf(
            "result" to result,
            "duration_ms" to durationMs.toString(),
            "process" to "app",
            "stage" to stage,
            "reason" to reason,
        )
        if (count != null) {
            attrs["count"] = count.toString()
        }
        if (!errorClass.isNullOrBlank()) {
            attrs["error_class"] = errorClass
        }
        MagiskOtel.event(
            name = "app.monitor",
            attributes = attrs,
            statusOk = statusOk,
        )
    }

    private suspend fun <T> withTokenRefresh(block: () -> T): T {
        return try {
            block()
        } catch (e: DeviceTokenExpiredException) {
            preferenceDataSource.setString(
                PrefConst.KEY_REMOTE_AGENT_LAST_ERROR,
                "设备令牌已过期，请重新绑定设备",
            )
            setSyncState("token_expired")
            throw e
        }
    }

    override suspend fun getSnapshot(): RemoteAgentSnapshot = withContext(Dispatchers.IO) {
        val token = InternalSecretStore.getString(appContext, PrefConst.KEY_REMOTE_AGENT_DEVICE_TOKEN, "")
        val revision = localConfigRepository.getRevision()
        val dirtyState = localConfigRepository.getDirtyState()
        RemoteAgentSnapshot(
            backendBaseUrl = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_BASE_URL, ""),
            bound = token.isNotBlank(),
            userId = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_USER_ID, "0").toLongOrNull() ?: 0L,
            deviceId = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_DEVICE_ID, "0").toLongOrNull() ?: 0L,
            deviceTokenPresent = token.isNotBlank(),
            localConfigRevision = revision.value,
            syncState = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_SYNC_STATE, "idle"),
            lastError = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_LAST_ERROR, ""),
            lastHeartbeatAt = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_LAST_HEARTBEAT_AT, "0").toLongOrNull() ?: 0L,
            lastPullAt = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_LAST_PULL_AT, "0").toLongOrNull() ?: 0L,
            lastPushAt = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_LAST_PUSH_AT, "0").toLongOrNull() ?: 0L,
            pendingLocalChanges = dirtyState.pendingLocalChanges,
        )
    }

    override suspend fun updateBackendBaseUrl(baseUrl: String): RemoteAgentSnapshot = withContext(Dispatchers.IO) {
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_BASE_URL, normalizeBaseUrl(baseUrl))
        publishHookPrefs()
        getSnapshot()
    }

    override suspend fun clearBinding(): RemoteAgentSnapshot = withContext(Dispatchers.IO) {
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_USER_ID, "0")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_DEVICE_ID, "0")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_REVISION, "0")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_SYNC_STATE, "idle")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_ERROR, "")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_HEARTBEAT_AT, "0")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_PULL_AT, "0")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_PUSH_AT, "0")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_SCHEDULED_TASK_BASELINE, "")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_PENDING_CONFIG_ACK, "")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_CONFIG_BASELINE, "")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_PENDING_LOCAL_CHANGES, "0")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_DEVICE_APP_INFOS, "")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_APP_CATALOG_DIGEST, "")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_RECORD_SNAPSHOT_DIGEST, "")
        InternalSecretStore.putString(appContext, PrefConst.KEY_REMOTE_AGENT_DEVICE_TOKEN, "")
        publishHookPrefs()
        emitAgent(
            stage = "agent_clear",
            result = "ok",
            reason = "cleared",
            durationMs = 0L,
        )
        getSnapshot()
    }

    override suspend fun bindDevice(bindCode: String): RemoteAgentSnapshot = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        try {
        syncMutex.withLock {
        val baseUrl = normalizedBackendBaseUrl()
        val request = AgentRegisterRequest(
            bindCode = bindCode.trim(),
            deviceName = DeviceIdentityUtils.resolveDefaultDeviceName(),
            deviceModel = Build.MODEL.orEmpty(),
            platform = "android",
            appVersion = resolveAppVersion(),
        )
        runCatching {
            setSyncState("binding")
            val payload = remoteApiClient.registerDevice(baseUrl, request)
            preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_USER_ID, payload.userId.toString())
            preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_DEVICE_ID, payload.deviceId.toString())
            InternalSecretStore.putString(appContext, PrefConst.KEY_REMOTE_AGENT_DEVICE_TOKEN, payload.deviceToken)
            preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_ERROR, "")
            setSyncState("bound")
            publishHookPrefs()
        }.onFailure {
            recordAgentFailure(it)
        }.getOrThrow()
        }
        runCatching { pushInstalledAppCatalogIfNeeded() }.onFailure { error ->
            XLog.w("bindDevice: pushInstalledAppCatalogIfNeeded failed: ${error.message}", error)
        }
        val snapshot = getSnapshot()
        scheduleBackgroundSync("bind")
        emitAgent(
            stage = "agent_bind",
            result = "ok",
            reason = "bound",
            durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
        )
        snapshot
        } catch (error: Throwable) {
            emitAgent(
                stage = "agent_bind",
                result = "error",
                reason = "bind_failed",
                errorClass = error.javaClass.simpleName,
                durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
                statusOk = false,
            )
            throw error
        }
    }

    override suspend fun sendHeartbeat(): RemoteAgentSnapshot = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        try {
        syncMutex.withLock {
        val boundSnapshot = getSnapshot()
        require(boundSnapshot.bound) { "device not bound" }
        val token = InternalSecretStore.getString(appContext, PrefConst.KEY_REMOTE_AGENT_DEVICE_TOKEN, "")
        val baseUrl = normalizedBackendBaseUrl()
        val request = HeartbeatRequest(
            appVersion = resolveAppVersion(),
            localAddresses = emptyList(),
            capabilities = mapOf(
                "androidAgent" to true,
                "embeddedWebUi" to false,
                "remoteConfig" to true,
                "recordUpload" to true,
                "scheduledTasks" to BuildConfig.ENABLE_SMS_CHANNEL,
            ),
            timeZone = TimeZone.getDefault().id,
        )
        executeSimpleAgentWrite(PrefConst.KEY_REMOTE_AGENT_LAST_HEARTBEAT_AT, "heartbeat") {
            remoteApiClient.sendHeartbeat(baseUrl, token, request)
        }
        val snapshot = getSnapshot()
        emitAgent(
            stage = "agent_heartbeat",
            result = "ok",
            reason = "heartbeat",
            durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
        )
        snapshot
        }
        } catch (error: Throwable) {
            emitAgent(
                stage = "agent_heartbeat",
                result = "error",
                reason = "heartbeat_failed",
                errorClass = error.javaClass.simpleName,
                durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
                statusOk = false,
            )
            throw error
        }
    }

    override suspend fun pullPendingCommands(): LocalConfigMirror = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        try {
            syncMutex.withLock {
                val snapshot = getSnapshot()
                require(snapshot.bound) { "device not bound" }
                val token = InternalSecretStore.getString(appContext, PrefConst.KEY_REMOTE_AGENT_DEVICE_TOKEN, "")
                val baseUrl = normalizedBackendBaseUrl()
                setSyncState("pulling")
                return@withContext runCatching {
                    val localRevision = localConfigRepository.getRevision().value
                    val localDirtyState = localConfigRepository.getDirtyState()
                    flushPendingConfigAck(baseUrl, token)
                    val payload = withTokenRefresh {
                        remoteApiClient.pullConfigCommands(
                            baseUrl = baseUrl,
                            deviceToken = token,
                            request = AgentConfigCommandsPullRequest(localRevision = localRevision),
                        )
                    }
                    var currentMirror = localConfigRepository.exportMirror()
                    val bootstrapMirrorContent = payload.mirrorContent
                    if (localRevision == 0L && !localDirtyState.dirty && bootstrapMirrorContent?.isNotEmpty() == true) {
                        currentMirror = localConfigRepository.applyMirror(
                            mirrorContent = bootstrapMirrorContent,
                            revision = payload.revision,
                            source = "bootstrap_import",
                        )
                        RuntimeSettingsCache.clear()
                        persistConfigBaseline(currentMirror.content)
                    }
                    val commandCount = payload.pendingCommands.size
                    var remoteBaseTasks = payload.mirrorContent?.get("scheduledTasks") as? JsonArray
                    payload.pendingCommands.forEach { command ->
                        currentMirror = applyPulledCommand(
                            baseUrl,
                            token,
                            command,
                            currentMirror,
                            remoteBaseTasks,
                        )
                        if (commandContainsScheduledTasks(command)) {
                            remoteBaseTasks = currentMirror.content["scheduledTasks"] as? JsonArray
                        }
                    }
                    payload.pendingActions.forEach { action ->
                        applyPulledAction(baseUrl, token, action)
                    }
                    preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_PULL_AT, nowEpochMillis().toString())
                    preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_ERROR, "")
                    setSyncState("idle")
                    publishHookPrefs()
                    emitAgent(
                        stage = "agent_pull",
                        result = "ok",
                        reason = "pulled",
                        durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
                        count = commandCount,
                    )
                    currentMirror
                }.onFailure {
                    recordAgentFailure(it)
                }.getOrThrow()
            }
        } catch (error: Throwable) {
            emitAgent(
                stage = "agent_pull",
                result = "error",
                reason = "pull_failed",
                errorClass = error.javaClass.simpleName,
                durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
                statusOk = false,
            )
            throw error
        }
    }

    override suspend fun pushLocalMirror(): RemoteAgentSnapshot = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        try {
        syncMutex.withLock {
            val boundSnapshot = getSnapshot()
            require(boundSnapshot.bound) { "device not bound" }
            val token = InternalSecretStore.getString(appContext, PrefConst.KEY_REMOTE_AGENT_DEVICE_TOKEN, "")
            val baseUrl = normalizedBackendBaseUrl()
            flushPendingConfigAck(baseUrl, token)
            val localMirror = localConfigRepository.exportMirror()
            val request = AgentConfigMirrorRequest(
                localRevision = localMirror.revision.value,
                mirrorContent = localMirror.content,
                summary = "android_local_commit",
            )
            setSyncState("pushing")
            var syncedRequest = request
            runCatching {
                val result = pushMirrorWithOfflineMerge(baseUrl, token, request)
                syncedRequest = result.first
                persistConfigBaseline(result.second.mirrorContent ?: syncedRequest.mirrorContent)
                localConfigRepository.markMirrorSynced(syncedRequest.localRevision)
                val appCatalogDigest = computeDeviceAppCatalogDigest(
                    mirrorContent = syncedRequest.mirrorContent,
                    deviceId = boundSnapshot.deviceId.toString(),
                )
                preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_APP_CATALOG_DIGEST, appCatalogDigest)
                preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_PUSH_AT, nowEpochMillis().toString())
                preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_ERROR, "")
                setSyncState("idle")
                publishHookPrefs()
            }.onFailure {
                recordAgentFailure(it)
            }.getOrThrow()
            val snapshot = getSnapshot()
            emitAgent(
                stage = "agent_push",
                result = "ok",
                reason = "pushed",
                durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
            )
            snapshot
        }
        } catch (error: Throwable) {
            emitAgent(
                stage = "agent_push",
                result = "error",
                reason = "push_failed",
                errorClass = error.javaClass.simpleName,
                durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
                statusOk = false,
            )
            throw error
        }
    }

    private suspend fun pushMirrorWithOfflineMerge(
        baseUrl: String,
        deviceToken: String,
        initialRequest: AgentConfigMirrorRequest,
    ): Pair<AgentConfigMirrorRequest, DeviceConfigStateResponse> {
        try {
            val response = withTokenRefresh {
                remoteApiClient.pushConfigMirror(baseUrl, deviceToken, initialRequest)
            }
            return initialRequest to response
        } catch (error: IllegalStateException) {
            if (error.message?.contains("outdated_mirror_revision") != true) throw error
            val remoteState = withTokenRefresh {
                remoteApiClient.pullConfigCommands(
                    baseUrl = baseUrl,
                    deviceToken = deviceToken,
                    request = AgentConfigCommandsPullRequest(initialRequest.localRevision),
                )
            }
            val remoteContent = remoteState.mirrorContent?.takeIf { it.isNotEmpty() }
                ?: loadConfigBaseline()
                ?: throw error
            val base = loadConfigBaseline() ?: remoteContent.withEmptyScheduledTasks()
            val mergedContent = mergeDeviceConfigContent(
                base = base,
                local = initialRequest.mirrorContent,
                remote = remoteContent,
                mergeKey = "mirror:${remoteState.revision}",
            )
            localConfigRepository.applyMergedMirror(
                mirrorContent = mergedContent,
                revision = remoteState.revision,
                source = "offline_mirror_merge",
            )
            localConfigRepository.noteLocalMutation("offline_mirror_merge")
            val mergedLocal = localConfigRepository.exportMirror()
            val retryRequest = AgentConfigMirrorRequest(
                localRevision = mergedLocal.revision.value,
                mirrorContent = mergedLocal.content,
                summary = "android_offline_merge",
            )
            val response = withTokenRefresh {
                remoteApiClient.pushConfigMirror(baseUrl, deviceToken, retryRequest)
            }
            return retryRequest to response
        }
    }

    override suspend fun uploadRecentRecords(limit: Int): RemoteAgentSnapshot = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        try {
        syncMutex.withLock {
            val snapshotLimit = normalizeRecordSnapshotLimit(limit)
            val snapshot = getSnapshot()
            require(snapshot.bound) { "device not bound" }
            val token = InternalSecretStore.getString(appContext, PrefConst.KEY_REMOTE_AGENT_DEVICE_TOKEN, "")
            // The remote records view is a bounded recent-history snapshot. Keeping
            // the window bounded prevents old local history from permanently blocking sync.
            val records = RuntimeDependencies.get().relayRecordRepository.listRecords(snapshotLimit).map {
                val metadata = buildJsonObject {
                    put("localRecordId", it.id)
                    put("company", it.company)
                    put("notifyChannelId", it.notifyChannelId)
                    put("simSlot", it.simSlot)
                    put("subId", it.subId)
                    put("contactName", it.contactName)
                    put("phoneArea", it.phoneArea)
                    put("forwardStatus", it.forwardStatus)
                    put("forwardTarget", it.forwardTarget)
                    put("forwardMessage", it.forwardMessage)
                    put("forwardTime", it.forwardTime)
                }
                RelayRecordWire(
                    eventId = "local-record-${it.id}",
                    recordType = when (it.msgType) {
                        1 -> "app_notify"
                        2 -> "call"
                        else -> if (it.smsCode.isNullOrBlank()) "sms_plain" else "sms_code"
                    },
                    sender = it.sender.orEmpty(),
                    body = it.body.orEmpty(),
                    smsCode = it.smsCode.orEmpty(),
                    packageName = it.packageName.orEmpty(),
                    msgType = it.msgType,
                    callType = it.callType,
                    occurredAt = Instant.ofEpochMilli(it.date).toString(),
                    metadata = metadata,
                )
            }
            val recordSnapshotDigest = runCatching {
                computeRecordSnapshotDigest(records)
            }.onFailure {
                recordAgentFailure(it)
            }.getOrThrow()
            val lastRecordSnapshotDigest = preferenceDataSource.getString(
                PrefConst.KEY_REMOTE_AGENT_LAST_RECORD_SNAPSHOT_DIGEST,
                "",
            )
            if (recordSnapshotDigest == lastRecordSnapshotDigest) {
                emitAgent(
                    stage = "agent_upload",
                    result = "skip",
                    reason = "unchanged",
                    durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
                    count = records.size,
                )
                return@withLock getSnapshot()
            }
            val baseUrl = normalizedBackendBaseUrl()
            executeSimpleAgentWrite(PrefConst.KEY_REMOTE_AGENT_LAST_PUSH_AT, "records_upload") {
                remoteApiClient.uploadRelayRecords(
                    baseUrl,
                    token,
                    RelayRecordsBatchRequest(records = records, replaceExisting = true),
                )
            }
            preferenceDataSource.setString(
                PrefConst.KEY_REMOTE_AGENT_LAST_RECORD_SNAPSHOT_DIGEST,
                recordSnapshotDigest,
            )
            emitAgent(
                stage = "agent_upload",
                result = "ok",
                reason = "uploaded",
                durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
                count = records.size,
            )
            getSnapshot()
        }
        } catch (error: Throwable) {
            emitAgent(
                stage = "agent_upload",
                result = "error",
                reason = "upload_failed",
                errorClass = error.javaClass.simpleName,
                durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
                statusOk = false,
            )
            throw error
        }
    }

    override suspend fun startupSync() = withContext(Dispatchers.IO) {
        val snapshot = getSnapshot()
        if (!snapshot.bound) return@withContext
        scheduleBackgroundSync("startup")
    }

    override fun onAppForegrounded() {
        scheduleBackgroundSync("app_foreground")
    }

    override fun onAppBackgrounded() {
        scheduleBackgroundSync("app_background")
    }

    override fun scheduleRecordUpload(reason: String) {
        scheduleBackgroundSync("records:$reason", preferConfigPush = false)
    }

    override fun scheduleMessageTriggeredSync(reason: String) {
        scheduleBackgroundSync("message:$reason", preferConfigPush = true)
    }

    private suspend fun executeSimpleAgentWrite(
        timestampKey: String,
        stateLabel: String,
        block: () -> Unit,
    ) {
        runCatching {
            setSyncState(stateLabel)
            withTokenRefresh { block() }
            preferenceDataSource.setString(timestampKey, nowEpochMillis().toString())
            preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_ERROR, "")
            setSyncState("idle")
            publishHookPrefs()
        }.onFailure {
            recordAgentFailure(it)
        }.getOrThrow()
    }

    private suspend fun applyPulledCommand(
        baseUrl: String,
        deviceToken: String,
        command: DeviceConfigCommandResponse,
        currentMirror: LocalConfigMirror,
        remoteBaseTasks: JsonArray?,
    ): LocalConfigMirror {
        val dirtyState = localConfigRepository.getDirtyState()
        when {
            dirtyState.dirty && !commandContainsScheduledTasks(command) -> {
                return ackPulledCommandFailed(baseUrl, deviceToken, command, currentMirror, "local_dirty")
            }
            command.targetRevision <= dirtyState.revision.value &&
                !(dirtyState.dirty && commandContainsScheduledTasks(command)) -> {
                return ackPulledCommandFailed(baseUrl, deviceToken, command, currentMirror, "stale_local_revision")
            }
        }

        val nextSnapshot = if (dirtyState.dirty && commandContainsScheduledTasks(command)) {
            mergeScheduledTaskCommand(currentMirror.content, command, remoteBaseTasks)
        } else {
            applyConfigMutationBatch(currentMirror.content, command.mutation)
        }
        if (nextSnapshot == null) {
            return ackPulledCommandFailed(baseUrl, deviceToken, command, currentMirror, "unsupported_mutation")
        }

        val plannedAck = AgentConfigCommandsAckRequest(
            commandId = command.id,
            status = "applied",
            appliedRevision = command.targetRevision,
            failureReason = "",
            mirrorContent = nextSnapshot,
        )
        persistConfigAck(plannedAck)

        val appliedMirror = try {
            if (dirtyState.dirty && commandContainsScheduledTasks(command)) {
                localConfigRepository.applyMergedMirror(
                    mirrorContent = nextSnapshot,
                    revision = command.targetRevision,
                    source = "command_${command.id}",
                )
            } else {
                localConfigRepository.applyMirror(
                    mirrorContent = nextSnapshot,
                    revision = command.targetRevision,
                    source = "command_${command.id}",
                )
            }
        } catch (e: LocalConfigMirrorRejectedException) {
            return ackPulledCommandFailed(baseUrl, deviceToken, command, currentMirror, e.reason)
        }
        RuntimeSettingsCache.clear()
        persistConfigAck(
            plannedAck.copy(
                appliedRevision = appliedMirror.revision.value,
                mirrorContent = appliedMirror.content,
            ),
        )
        deliverPendingConfigAck(baseUrl, deviceToken)
        return appliedMirror
    }

    /**
     * Device actions are one-shot and carry no revision semantics: execute
     * locally, then ack done/failed. Execution failures ack with a reason
     * instead of aborting the pull; transport failures propagate so the action
     * stays pending for the next pull.
     */
    private suspend fun applyPulledAction(
        baseUrl: String,
        deviceToken: String,
        action: DeviceActionCommandResponse,
    ) {
        val outcome = deviceActionExecutor.execute(action)
        val status = if (outcome.success) DeviceActionConst.STATUS_DONE else DeviceActionConst.STATUS_FAILED
        withTokenRefresh {
            remoteApiClient.ackDeviceAction(
                baseUrl = baseUrl,
                deviceToken = deviceToken,
                request = DeviceActionAckRequest(
                    actionId = action.id,
                    status = status,
                    failureReason = outcome.failureReason,
                ),
            )
        }
    }

    private suspend fun ackPulledCommandFailed(
        baseUrl: String,
        deviceToken: String,
        command: DeviceConfigCommandResponse,
        currentMirror: LocalConfigMirror,
        reason: String,
    ): LocalConfigMirror {
        persistConfigAck(
            AgentConfigCommandsAckRequest(
                commandId = command.id,
                status = "failed",
                appliedRevision = currentMirror.revision.value,
                failureReason = reason,
                mirrorContent = currentMirror.content,
            ),
        )
        deliverPendingConfigAck(baseUrl, deviceToken)
        return currentMirror
    }

    private fun commandContainsScheduledTasks(command: DeviceConfigCommandResponse): Boolean =
        command.mutation["operations"]?.jsonArray?.any { operation ->
            operation.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "replace_scheduled_tasks"
        } == true

    private suspend fun mergeScheduledTaskCommand(
        currentMirror: JsonObject,
        command: DeviceConfigCommandResponse,
        remoteBaseTasks: JsonArray?,
    ): JsonObject? {
        val remoteTasks = command.mutation["operations"]?.jsonArray
            ?.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "replace_scheduled_tasks" }
            ?.jsonObject?.get("tasks")?.jsonArray ?: return null
        val localTasks = currentMirror["scheduledTasks"]?.jsonArray.orEmpty()
        val baseTasks = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_SCHEDULED_TASK_BASELINE, "")
            .takeIf(String::isNotBlank)
            ?.let { RelayJson.parseElement(it).jsonArray }
            ?: remoteBaseTasks
            ?: localTasks
        val base = baseTasks.map(::scheduledTaskRecord).filterNotNull()
        val local = localTasks.map(::scheduledTaskRecord).filterNotNull()
        val remote = remoteTasks.map(::scheduledTaskRecord).filterNotNull()
        val merged = ScheduledTaskSyncMerger.merge(
            base = base,
            local = local,
            remote = remote,
            mergeKey = "${command.deviceIdOrUnknown()}:${command.id}",
        )
        val tasks = merged.tasks.map { task ->
            RelayJson.parseElement(
                RelayJson.encode(SnapshotScheduledTask.serializer(), task.toSnapshot()),
            )
        }
        return buildJsonObject {
            currentMirror.forEach { (key, value) -> put(key, value) }
            putJsonArray("scheduledTasks") { tasks.forEach(::add) }
        }
    }

    private fun scheduledTaskRecord(element: JsonElement): ScheduledTaskSyncRecord? = runCatching {
        val task = RelayJson.decode(SnapshotScheduledTask.serializer(), element.toString())
        ScheduledTaskSyncRecord(
            syncId = task.syncId,
            name = task.name,
            taskType = task.taskType,
            cronExpression = task.cronExpression,
            simSlot = task.simSlot,
            mobiles = task.mobiles,
            content = task.content,
            status = task.status,
            createdAt = task.createdAt,
            conflictId = task.conflictId,
            conflictSide = task.conflictSide,
            conflictPreviousStatus = task.conflictPreviousStatus,
            syncDeleted = task.syncDeleted,
        )
    }.getOrNull()

    private fun ScheduledTaskSyncRecord.toSnapshot(): SnapshotScheduledTask = SnapshotScheduledTask(
        syncId = syncId,
        name = name,
        taskType = taskType,
        cronExpression = cronExpression,
        simSlot = simSlot,
        mobiles = mobiles,
        content = content,
        status = status,
        createdAt = createdAt,
        conflictId = conflictId,
        conflictSide = conflictSide,
        conflictPreviousStatus = conflictPreviousStatus,
        syncDeleted = syncDeleted,
    )

    private suspend fun persistConfigAck(request: AgentConfigCommandsAckRequest) {
        preferenceDataSource.setString(
            PrefConst.KEY_REMOTE_AGENT_PENDING_CONFIG_ACK,
            RelayJson.encode(AgentConfigCommandsAckRequest.serializer(), request),
        )
    }

    private suspend fun deliverPendingConfigAck(baseUrl: String, deviceToken: String) {
        val encoded = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_PENDING_CONFIG_ACK, "")
        if (encoded.isBlank()) return
        val request = RelayJson.decode(AgentConfigCommandsAckRequest.serializer(), encoded)
        withTokenRefresh { remoteApiClient.ackConfigCommand(baseUrl, deviceToken, request) }
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_PENDING_CONFIG_ACK, "")
        if (request.status == "applied") {
            persistConfigBaseline(request.mirrorContent)
            if (localConfigRepository.getRevision().value <= request.appliedRevision) {
                localConfigRepository.markMirrorSynced(request.appliedRevision)
            }
        }
    }

    private suspend fun flushPendingConfigAck(baseUrl: String, deviceToken: String) {
        val encoded = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_PENDING_CONFIG_ACK, "")
        if (encoded.isBlank()) return
        val request = RelayJson.decode(AgentConfigCommandsAckRequest.serializer(), encoded)
        if (request.status == "applied" && localConfigRepository.getRevision().value < request.appliedRevision) {
            localConfigRepository.applyMergedMirror(
                request.mirrorContent,
                request.appliedRevision,
                "config_ack_recovery",
            )
        }
        deliverPendingConfigAck(baseUrl, deviceToken)
    }

    private suspend fun persistConfigBaseline(content: JsonObject) {
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_CONFIG_BASELINE, content.toString())
        content["scheduledTasks"]?.let { tasks ->
            preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_SCHEDULED_TASK_BASELINE, tasks.toString())
        }
    }

    private suspend fun loadConfigBaseline(): JsonObject? =
        preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_CONFIG_BASELINE, "")
            .takeIf(String::isNotBlank)
            ?.let { runCatching { RelayJson.parseElement(it).jsonObject }.getOrNull() }

    private fun JsonObject.withEmptyScheduledTasks(): JsonObject = buildJsonObject {
        forEach { (key, value) -> put(key, value) }
        if (!containsKey("scheduledTasks")) putJsonArray("scheduledTasks") {}
    }

    private fun mergeDeviceConfigContent(
        base: JsonObject,
        local: JsonObject,
        remote: JsonObject,
        mergeKey: String,
    ): JsonObject = mergeDeviceConfigObject(base, local, remote, mergeKey)

    private fun mergeDeviceConfigObject(
        base: JsonObject,
        local: JsonObject,
        remote: JsonObject,
        mergeKey: String,
    ): JsonObject {
        val keys = base.keys + local.keys + remote.keys
        return buildJsonObject {
            keys.forEach { key ->
                val merged = mergeDeviceConfigValue(key, base[key], local[key], remote[key], mergeKey)
                if (merged != null && merged !is JsonNull) put(key, merged)
            }
        }
    }

    private fun mergeDeviceConfigValue(
        key: String,
        base: JsonElement?,
        local: JsonElement?,
        remote: JsonElement?,
        mergeKey: String,
    ): JsonElement? {
        if (key == "scheduledTasks" && local is JsonArray && remote is JsonArray) {
            val baseTasks = (base as? JsonArray).orEmpty().map(::scheduledTaskRecord).filterNotNull()
            val merged = ScheduledTaskSyncMerger.merge(
                base = baseTasks,
                local = local.map(::scheduledTaskRecord).filterNotNull(),
                remote = remote.map(::scheduledTaskRecord).filterNotNull(),
                mergeKey = mergeKey,
            )
            return RelayJson.parseElement(
                RelayJson.encode(
                    ListSerializer(SnapshotScheduledTask.serializer()),
                    merged.tasks.map { it.toSnapshot() },
                ),
            )
        }
        if (local == base) return remote
        if (remote == base) return local
        if (local == remote) return local

        val baseObject = base as? JsonObject ?: JsonObject(emptyMap())
        val localObject = local as? JsonObject
        val remoteObject = remote as? JsonObject
        if (localObject != null && remoteObject != null) {
            return mergeDeviceConfigObject(baseObject, localObject, remoteObject, mergeKey)
        }
        return local ?: remote
    }

    private fun DeviceConfigCommandResponse.deviceIdOrUnknown(): String =
        actorId.takeIf { it > 0 }?.toString() ?: "device"

    private suspend fun recordAgentFailure(error: Throwable) {
        if (error is DeviceTokenExpiredException) {
            return
        }
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_ERROR, error.message ?: error.javaClass.simpleName)
        setSyncState("error")
    }

    private suspend fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().removeSuffix("/")
        require(trimmed.isNotBlank()) { "backend base url required" }
        require(trimmed.startsWith("https://") || trimmed.startsWith("http://")) { "backend base url must start with http:// or https://" }
        return trimmed
    }

    private suspend fun normalizedBackendBaseUrl(): String {
        return normalizeBaseUrl(preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_BASE_URL, ""))
    }

    private suspend fun setSyncState(state: String) {
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_SYNC_STATE, state)
    }

    private fun nowEpochMillis(): Long = Instant.now().toEpochMilli()

    private suspend fun pushInstalledAppCatalogIfNeeded() {
        val snapshot = getSnapshot()
        if (!snapshot.bound) return
        val localMirror = localConfigRepository.exportMirror()
        val digest = computeDeviceAppCatalogDigest(
            mirrorContent = localMirror.content,
            deviceId = snapshot.deviceId.toString(),
        )
        val lastDigest = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_LAST_APP_CATALOG_DIGEST, "")
        if (digest == lastDigest) return
        localConfigRepository.noteLocalMutation("config.app_catalog_refresh")
        publishHookPrefs()
        pushLocalMirror()
    }

    private fun scheduleBackgroundSync(
        reason: String,
        preferConfigPush: Boolean = true,
    ) {
        if (!backgroundSyncInFlight.compareAndSet(false, true)) {
            backgroundSyncQueued.set(true)
            return
        }
        scope.launch {
            try {
                val snapshot = getSnapshot()
                if (!snapshot.bound) return@launch
                runCatching { sendHeartbeat() }.onFailure { error ->
                    XLog.w("scheduleBackgroundSync: sendHeartbeat failed: ${error.message}", error)
                }
                runCatching { pullPendingCommands() }.onFailure { error ->
                    XLog.w("scheduleBackgroundSync: pullPendingCommands failed: ${error.message}", error)
                }
                val localState = runCatching { localConfigRepository.getDirtyState() }.getOrNull()
                if (preferConfigPush && localState?.dirty == true) {
                    runCatching { pushLocalMirror() }.onFailure { error ->
                        XLog.w("scheduleBackgroundSync: pushLocalMirror failed: ${error.message}", error)
                    }
                }
                runCatching { pushInstalledAppCatalogIfNeeded() }.onFailure { error ->
                    XLog.w("scheduleBackgroundSync: pushInstalledAppCatalogIfNeeded failed: ${error.message}", error)
                }
                runCatching { uploadRecentRecords() }.onFailure { XLog.w("recent-record upload failed: ${it.message}", it) }
            } finally {
                backgroundSyncInFlight.set(false)
                if (backgroundSyncQueued.compareAndSet(true, false)) {
                    scheduleBackgroundSync("$reason:coalesced", preferConfigPush = true)
                }
            }
        }
    }

    private fun resolveAppVersion(): String {
        return runCatching {
            val packageInfo = PlatformCompat.getPackageInfo(appContext.packageManager, appContext.packageName)
            packageInfo.versionName ?: PackageInfoCompat.getLongVersionCode(packageInfo).toString()
        }.getOrDefault("unknown")
    }

    private suspend fun publishHookPrefs() {
        HookPreferenceMirror.publish(appContext)
    }
}

internal fun computeRecordSnapshotDigest(records: List<RelayRecordWire>): String {
    require(records.size <= MAX_RELAY_RECORDS_PER_SNAPSHOT) {
        "record snapshot contains ${records.size} records; maximum is $MAX_RELAY_RECORDS_PER_SNAPSHOT; nothing uploaded"
    }
    val canonical = RelayJson.encode(RelayRecordsBatchRequest.serializer(), RelayRecordsBatchRequest(records, true))
    val canonicalBytes = canonical.toByteArray(Charsets.UTF_8)
    require(canonicalBytes.size <= MAX_RELAY_RECORD_SNAPSHOT_BYTES) {
        "record snapshot is ${canonicalBytes.size} bytes; maximum is $MAX_RELAY_RECORD_SNAPSHOT_BYTES; nothing uploaded"
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(canonicalBytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

internal fun normalizeRecordSnapshotLimit(limit: Int): Int {
    require(limit > 0) { "record upload limit must be positive" }
    return limit.coerceAtMost(MAX_RELAY_RECORDS_PER_SNAPSHOT)
}

internal fun mergeRemoteConfigJson(
    base: JsonObject,
    incoming: JsonObject,
): JsonObject {
    return buildJsonObject {
        base.forEach { (key, value) -> put(key, value) }
        incoming.forEach { (key, incomingValue) ->
            put(key, mergeRemoteConfigElement(base[key], incomingValue))
        }
    }
}

private fun mergeRemoteConfigElement(
    base: JsonElement?,
    incoming: JsonElement?,
): JsonElement {
    if (incoming == null || incoming is JsonNull) {
        return base ?: buildJsonObject {}
    }
    if (base is JsonObject && incoming is JsonObject) {
        return mergeRemoteConfigJson(base, incoming)
    }
    return incoming
}

internal fun computeDeviceAppCatalogDigest(
    mirrorContent: JsonObject,
    deviceId: String,
): String {
    val apps = mirrorContent["deviceAppInfos"]
        ?.jsonObject
        ?.get(deviceId)
        ?.jsonArray
        .orEmpty()
        .mapNotNull { element ->
            val appInfo = element as? JsonObject ?: return@mapNotNull null
            val packageName = appInfo["packageName"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (packageName.isBlank()) return@mapNotNull null
            AppCatalogDigestEntry(
                packageName = packageName,
                label = appInfo["label"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                blocked = appInfo["blocked"]?.jsonPrimitive?.booleanOrNull ?: false,
                forwarding = appInfo["forwarding"]?.jsonPrimitive?.booleanOrNull ?: false,
                forwardingConfigured = appInfo["forwardingConfigured"]?.jsonPrimitive?.booleanOrNull ?: false,
                notifyTemplate = appInfo["notifyTemplate"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }

    return JsonArray(
        apps.sortedBy { it.packageName }.map {
            JsonArray(
                listOf(
                    JsonPrimitive(it.packageName),
                    JsonPrimitive(it.label),
                    JsonPrimitive(it.blocked),
                    JsonPrimitive(it.forwarding),
                    JsonPrimitive(it.forwardingConfigured),
                    JsonPrimitive(it.notifyTemplate),
                ),
            )
        },
    ).toString()
}

private data class AppCatalogDigestEntry(
    val packageName: String,
    val label: String,
    val blocked: Boolean,
    val forwarding: Boolean,
    val forwardingConfigured: Boolean,
    val notifyTemplate: String,
)
