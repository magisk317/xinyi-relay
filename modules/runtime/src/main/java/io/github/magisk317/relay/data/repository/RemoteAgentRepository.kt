package io.github.magisk317.relay.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.android.data.secret.InternalSecretStore
import io.github.magisk317.relay.bootstrap.RuntimeDependencies
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.contract.model.LocalConfigMirror
import io.github.magisk317.relay.contract.repository.ConfigSyncCoordinator
import io.github.magisk317.relay.contract.repository.LocalConfigRepository
import io.github.magisk317.relay.contract.repository.LocalConfigMirrorRejectedException
import io.github.magisk317.relay.contract.remote.AgentConfigCommandsAckRequest
import io.github.magisk317.relay.contract.remote.AgentConfigCommandsPullRequest
import io.github.magisk317.relay.contract.remote.AgentConfigMirrorRequest
import io.github.magisk317.relay.contract.remote.AgentRegisterRequest
import io.github.magisk317.relay.contract.remote.DeviceConfigCommandResponse
import io.github.magisk317.relay.contract.remote.HeartbeatRequest
import io.github.magisk317.relay.contract.remote.RelayRecordWire
import io.github.magisk317.relay.contract.remote.RelayRecordsBatchRequest
import io.github.magisk317.relay.contract.settings.RemoteAgentSnapshot
import io.github.magisk317.relay.data.remote.DeviceTokenExpiredException
import io.github.magisk317.relay.android.prefs.HookPreferenceMirror
import io.github.magisk317.relay.android.common.utils.DeviceIdentityUtils
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import io.github.magisk317.relay.data.remote.RemoteAgentApi
import io.github.magisk317.relay.data.remote.RemoteApiClient
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

class RemoteAgentRepository(
    private val appContext: Context,
    private val preferenceDataSource: PreferenceDataSource,
    private val localConfigRepository: LocalConfigRepository,
    private val remoteApiClient: RemoteAgentApi = RemoteApiClient(),
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
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_PENDING_LOCAL_CHANGES, "0")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_DEVICE_APP_INFOS, "")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_APP_CATALOG_DIGEST, "")
        InternalSecretStore.putString(appContext, PrefConst.KEY_REMOTE_AGENT_DEVICE_TOKEN, "")
        publishHookPrefs()
        getSnapshot()
    }

    override suspend fun bindDevice(bindCode: String): RemoteAgentSnapshot = withContext(Dispatchers.IO) {
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
        runCatching { pushInstalledAppCatalogIfNeeded() }
        val snapshot = getSnapshot()
        scheduleBackgroundSync("bind")
        snapshot
    }

    override suspend fun sendHeartbeat(): RemoteAgentSnapshot = withContext(Dispatchers.IO) {
        syncMutex.withLock {
        val snapshot = getSnapshot()
        require(snapshot.bound) { "device not bound" }
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
            ),
        )
        executeSimpleAgentWrite(PrefConst.KEY_REMOTE_AGENT_LAST_HEARTBEAT_AT, "heartbeat") {
            remoteApiClient.sendHeartbeat(baseUrl, token, request)
        }
        getSnapshot()
        }
    }

    override suspend fun pullPendingCommands(): LocalConfigMirror = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val snapshot = getSnapshot()
            require(snapshot.bound) { "device not bound" }
            val token = InternalSecretStore.getString(appContext, PrefConst.KEY_REMOTE_AGENT_DEVICE_TOKEN, "")
            val baseUrl = normalizedBackendBaseUrl()
            setSyncState("pulling")
            return@withContext runCatching {
                val localRevision = localConfigRepository.getRevision().value
                val localDirtyState = localConfigRepository.getDirtyState()
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
                }
                payload.pendingCommands.forEach { command ->
                    currentMirror = applyPulledCommand(baseUrl, token, command, currentMirror)
                }
                preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_PULL_AT, nowEpochMillis().toString())
                preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_ERROR, "")
                setSyncState("idle")
                publishHookPrefs()
                currentMirror
            }.onFailure {
                recordAgentFailure(it)
            }.getOrThrow()
        }
    }

    override suspend fun pushLocalMirror(): RemoteAgentSnapshot = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val snapshot = getSnapshot()
            require(snapshot.bound) { "device not bound" }
            val token = InternalSecretStore.getString(appContext, PrefConst.KEY_REMOTE_AGENT_DEVICE_TOKEN, "")
            val localMirror = localConfigRepository.exportMirror()
            val appCatalogDigest = computeDeviceAppCatalogDigest(
                mirrorContent = localMirror.content,
                deviceId = snapshot.deviceId.toString(),
            )
            val request = AgentConfigMirrorRequest(
                localRevision = localMirror.revision.value,
                mirrorContent = localMirror.content,
                summary = "android_local_commit",
            )
            val baseUrl = normalizedBackendBaseUrl()
            setSyncState("pushing")
            runCatching {
                withTokenRefresh { remoteApiClient.pushConfigMirror(baseUrl, token, request) }
                localConfigRepository.markMirrorSynced(localMirror.revision.value)
                preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_APP_CATALOG_DIGEST, appCatalogDigest)
                preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_PUSH_AT, nowEpochMillis().toString())
                preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_ERROR, "")
                setSyncState("idle")
                publishHookPrefs()
            }.onFailure {
                recordAgentFailure(it)
            }.getOrThrow()
            getSnapshot()
        }
    }

    override suspend fun uploadRecentRecords(limit: Int): RemoteAgentSnapshot = withContext(Dispatchers.IO) {
        syncMutex.withLock {
        val snapshot = getSnapshot()
        require(snapshot.bound) { "device not bound" }
        val token = InternalSecretStore.getString(appContext, PrefConst.KEY_REMOTE_AGENT_DEVICE_TOKEN, "")
        val records = RuntimeDependencies.get().relayRecordRepository.listRecords(limit).map {
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
        val baseUrl = normalizedBackendBaseUrl()
        executeSimpleAgentWrite(PrefConst.KEY_REMOTE_AGENT_LAST_PUSH_AT, "records_upload") {
            remoteApiClient.uploadRelayRecords(baseUrl, token, RelayRecordsBatchRequest(records))
        }
        getSnapshot()
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
    ): LocalConfigMirror {
        val dirtyState = localConfigRepository.getDirtyState()
        when {
            dirtyState.dirty -> {
                return ackPulledCommandFailed(baseUrl, deviceToken, command, currentMirror, "local_dirty")
            }
            command.targetRevision <= dirtyState.revision.value -> {
                return ackPulledCommandFailed(baseUrl, deviceToken, command, currentMirror, "stale_local_revision")
            }
        }

        val nextSnapshot = applyConfigMutationBatch(currentMirror.content, command.mutation)
        if (nextSnapshot == null) {
            return ackPulledCommandFailed(baseUrl, deviceToken, command, currentMirror, "unsupported_mutation")
        }

        val appliedMirror = try {
            localConfigRepository.applyMirror(
                mirrorContent = nextSnapshot,
                revision = command.targetRevision,
                source = "command_${command.id}",
            )
        } catch (e: LocalConfigMirrorRejectedException) {
            return ackPulledCommandFailed(baseUrl, deviceToken, command, currentMirror, e.reason)
        }
        RuntimeSettingsCache.clear()
        withTokenRefresh {
            remoteApiClient.ackConfigCommand(
                baseUrl = baseUrl,
                deviceToken = deviceToken,
                request = AgentConfigCommandsAckRequest(
                    commandId = command.id,
                    status = "applied",
                    appliedRevision = appliedMirror.revision.value,
                    failureReason = "",
                    mirrorContent = appliedMirror.content,
                ),
            )
        }
        return appliedMirror
    }

    private suspend fun ackPulledCommandFailed(
        baseUrl: String,
        deviceToken: String,
        command: DeviceConfigCommandResponse,
        currentMirror: LocalConfigMirror,
        reason: String,
    ): LocalConfigMirror {
        withTokenRefresh {
            remoteApiClient.ackConfigCommand(
                baseUrl = baseUrl,
                deviceToken = deviceToken,
                request = AgentConfigCommandsAckRequest(
                    commandId = command.id,
                    status = "failed",
                    appliedRevision = currentMirror.revision.value,
                    failureReason = reason,
                    mirrorContent = currentMirror.content,
                ),
            )
        }
        return currentMirror
    }

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
                runCatching { sendHeartbeat() }
                val localState = runCatching { localConfigRepository.getDirtyState() }.getOrNull()
                if (preferConfigPush && localState?.dirty == true) {
                    runCatching { pushLocalMirror() }
                }
                runCatching { pullPendingCommands() }
                runCatching { pushInstalledAppCatalogIfNeeded() }
                runCatching { uploadRecentRecords() }
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
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(
                    appContext.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            }
            packageInfo.versionName ?: PackageInfoCompat.getLongVersionCode(packageInfo).toString()
        }.getOrDefault("unknown")
    }

    private suspend fun publishHookPrefs() {
        HookPreferenceMirror.publish(appContext)
    }
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
