package io.github.magisk317.relay.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.android.data.db.entity.AppInfo
import io.github.magisk317.relay.android.data.db.entity.NotifyRouteRule
import io.github.magisk317.relay.android.data.db.entity.SmsCodeRule
import io.github.magisk317.relay.data.mapper.ConfigMapper.toDomain
import io.github.magisk317.relay.data.secret.InternalSecretStore
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.engine.model.ForwardFilterRule
import io.github.magisk317.relay.engine.model.ForwardCommonConfig
import io.github.magisk317.relay.engine.model.Rule
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.android.common.utils.DeviceIdentityUtils
import io.github.magisk317.relay.prefs.HookPreferenceMirror
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class RemoteAgentSnapshot(
    val backendBaseUrl: String,
    val bound: Boolean,
    val userId: Long,
    val deviceId: Long,
    val deviceTokenPresent: Boolean,
    val lastAppliedConfigRevision: Long,
    val syncState: String,
    val lastError: String,
    val lastHeartbeatAt: Long,
    val lastPullAt: Long,
    val lastPushAt: Long,
    val pendingMutations: Int,
)

data class RemoteConfigSnapshot(
    val revision: Long,
    val content: JsonObject,
)

private data class RemoteConfigPayload(
    val general: GeneralSettingsSnapshot,
    val verification: VerificationSettingsSnapshot,
    val relay: RelaySettingsSnapshot,
    val diagnostics: DiagnosticsSettingsSnapshot,
    val advanced: AdvancedSettingsSnapshot,
    val specialAlerts: SpecialAlertSettingsSnapshot,
    val messageTypeGates: MessageTypeGateSnapshot,
    val forwardTypeGates: ForwardTypeGateSnapshot,
    val records: RecordSettingsSnapshot,
    val smsBlacklist: SmsBlacklistSettingsSnapshot,
    val simRemarks: SimRemarkSettingsSnapshot,
    val forwardCommon: ForwardCommonConfig,
    val appNotifyTemplate: String,
    val callNotifyTemplate: String,
    val overview: OverviewSettingsSnapshot,
    val senders: List<Sender>,
    val rules: List<Rule>,
    val smsCodeRules: List<SmsCodeRule>,
    val appInfos: List<AppInfo>,
    val notifyRoutes: List<NotifyRouteRule>,
    val forwardFilters: List<ForwardFilterRule>,
)

private data class AgentRegisterResponse(
    val userId: Long = 0L,
    val deviceId: Long = 0L,
    val deviceToken: String = "",
)

private data class ConfigSnapshotResponse(
    val revision: Long = 0L,
    val snapshot: JsonObject? = null,
)

private data class RelayRecordWire(
    val eventId: String,
    val recordType: String,
    val sender: String,
    val body: String,
    val smsCode: String,
    val packageName: String,
    val msgType: Int,
    val callType: Int,
    val occurredAt: String,
    val metadata: JsonObject,
)

class RemoteAgentRepository(
    private val appContext: Context,
    private val preferenceDataSource: PreferenceDataSource,
) {
    private val gson = Gson()
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val backgroundSyncInFlight = AtomicBoolean(false)
    private val backgroundSyncQueued = AtomicBoolean(false)
    private val applyingRemoteConfigDepth = AtomicInteger(0)

    suspend fun getSnapshot(): RemoteAgentSnapshot = withContext(Dispatchers.IO) {
        val token = InternalSecretStore.getString(appContext, PrefConst.KEY_REMOTE_AGENT_DEVICE_TOKEN, "")
        RemoteAgentSnapshot(
            backendBaseUrl = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_BASE_URL, ""),
            bound = token.isNotBlank(),
            userId = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_USER_ID, "0").toLongOrNull() ?: 0L,
            deviceId = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_DEVICE_ID, "0").toLongOrNull() ?: 0L,
            deviceTokenPresent = token.isNotBlank(),
            lastAppliedConfigRevision = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_LAST_REVISION, "0").toLongOrNull() ?: 0L,
            syncState = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_SYNC_STATE, "idle"),
            lastError = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_LAST_ERROR, ""),
            lastHeartbeatAt = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_LAST_HEARTBEAT_AT, "0").toLongOrNull() ?: 0L,
            lastPullAt = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_LAST_PULL_AT, "0").toLongOrNull() ?: 0L,
            lastPushAt = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_LAST_PUSH_AT, "0").toLongOrNull() ?: 0L,
            pendingMutations = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_PENDING_MUTATIONS, "0").toIntOrNull() ?: 0,
        )
    }

    suspend fun updateBackendBaseUrl(baseUrl: String): RemoteAgentSnapshot = withContext(Dispatchers.IO) {
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_BASE_URL, normalizeBaseUrl(baseUrl))
        publishHookPrefs()
        getSnapshot()
    }

    suspend fun clearBinding(): RemoteAgentSnapshot = withContext(Dispatchers.IO) {
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_USER_ID, "0")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_DEVICE_ID, "0")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_REVISION, "0")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_SYNC_STATE, "idle")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_ERROR, "")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_HEARTBEAT_AT, "0")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_PULL_AT, "0")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_PUSH_AT, "0")
        preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_PENDING_MUTATIONS, "0")
        InternalSecretStore.putString(appContext, PrefConst.KEY_REMOTE_AGENT_DEVICE_TOKEN, "")
        publishHookPrefs()
        getSnapshot()
    }

    suspend fun bindDevice(bindCode: String): RemoteAgentSnapshot = withContext(Dispatchers.IO) {
        syncMutex.withLock {
        val baseUrl = normalizedBackendBaseUrl()
        val requestBody = gson.toJson(
            mapOf(
                "bindCode" to bindCode.trim(),
                "deviceName" to DeviceIdentityUtils.resolveDefaultDeviceName(),
                "deviceModel" to Build.MODEL.orEmpty(),
                "platform" to "android",
                "appVersion" to resolveAppVersion(),
            ),
        ).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/api/v1/agent/register")
            .post(requestBody)
            .build()
        runCatching {
            setSyncState("binding")
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException(response.body.string().ifBlank { "bind failed: ${response.code}" })
                }
                val payload = gson.fromJson(response.body.charStream(), AgentRegisterResponse::class.java)
                preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_USER_ID, payload.userId.toString())
                preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_DEVICE_ID, payload.deviceId.toString())
                val pendingMutations = preferenceDataSource.getString(
                    PrefConst.KEY_REMOTE_AGENT_PENDING_MUTATIONS,
                    "0",
                ).toIntOrNull() ?: 0
                preferenceDataSource.setString(
                    PrefConst.KEY_REMOTE_AGENT_PENDING_MUTATIONS,
                    pendingMutations.coerceAtLeast(1).toString(),
                )
                InternalSecretStore.putString(appContext, PrefConst.KEY_REMOTE_AGENT_DEVICE_TOKEN, payload.deviceToken)
                preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_ERROR, "")
                setSyncState("bound")
                publishHookPrefs()
            }
        }.onFailure {
            preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_ERROR, it.message ?: it.javaClass.simpleName)
            setSyncState("error")
        }.getOrThrow()
        }
        runCatching { pushInstalledAppCatalogIfNeeded() }
        val snapshot = getSnapshot()
        scheduleBackgroundSync("bind")
        snapshot
    }

    suspend fun sendHeartbeat(): RemoteAgentSnapshot = withContext(Dispatchers.IO) {
        syncMutex.withLock {
        val snapshot = getSnapshot()
        require(snapshot.bound) { "device not bound" }
        val token = InternalSecretStore.getString(appContext, PrefConst.KEY_REMOTE_AGENT_DEVICE_TOKEN, "")
        val requestBody = gson.toJson(
            mapOf(
                "appVersion" to resolveAppVersion(),
                "localAddresses" to emptyList<String>(),
                "capabilities" to mapOf(
                    "androidAgent" to true,
                    "embeddedWebUi" to false,
                    "remoteConfig" to true,
                    "recordUpload" to true,
                ),
            ),
        ).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${normalizedBackendBaseUrl()}/api/v1/agent/heartbeat")
            .header("Authorization", "Bearer $token")
            .post(requestBody)
            .build()
        executeSimpleAgentWrite(request, PrefConst.KEY_REMOTE_AGENT_LAST_HEARTBEAT_AT, "heartbeat")
        getSnapshot()
        }
    }

    suspend fun pullConfigSnapshot(): RemoteConfigSnapshot = withContext(Dispatchers.IO) {
        syncMutex.withLock {
        val snapshot = getSnapshot()
        require(snapshot.bound) { "device not bound" }
        val token = InternalSecretStore.getString(appContext, PrefConst.KEY_REMOTE_AGENT_DEVICE_TOKEN, "")
        val request = Request.Builder()
            .url("${normalizedBackendBaseUrl()}/api/v1/config/snapshot")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        setSyncState("pulling")
        return@withContext runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException(response.body.string().ifBlank { "pull failed: ${response.code}" })
                }
                val payload = gson.fromJson(response.body.charStream(), ConfigSnapshotResponse::class.java)
                payload.snapshot?.let { applyRemoteConfigPayload(it) }
                preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_REVISION, payload.revision.toString())
                preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_PULL_AT, nowEpochMillis().toString())
                preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_ERROR, "")
                setSyncState("idle")
                publishHookPrefs()
                RemoteConfigSnapshot(
                    revision = payload.revision,
                    content = payload.snapshot ?: JsonObject(),
                )
            }
        }.onFailure {
            preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_ERROR, it.message ?: it.javaClass.simpleName)
            setSyncState("error")
        }.getOrThrow()
        }
    }

    suspend fun pushConfigSnapshot(): RemoteAgentSnapshot = withContext(Dispatchers.IO) {
        syncMutex.withLock {
        val snapshot = getSnapshot()
        require(snapshot.bound) { "device not bound" }
        val token = InternalSecretStore.getString(appContext, PrefConst.KEY_REMOTE_AGENT_DEVICE_TOKEN, "")
        val appCatalogDigest = computeAppCatalogDigest(
            resolveInstalledAppCatalog(RuntimeGraph.from(appContext).configRepository.getAllAppInfo()),
        )
        val body = gson.toJson(
            mapOf(
                "base_revision" to snapshot.lastAppliedConfigRevision,
                "snapshot" to buildConfigSnapshotPayload(),
            ),
        ).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${normalizedBackendBaseUrl()}/api/v1/config/snapshot")
            .header("Authorization", "Bearer $token")
            .put(body)
            .build()
        setSyncState("pushing")
        runCatching {
            client.newCall(request).execute().use { response ->
                val responseText = response.body.string()
                if (response.code == HTTP_CONFLICT) {
                    val payload = gson.fromJson(responseText, ConfigSnapshotResponse::class.java)
                    payload.snapshot?.let { applyRemoteConfigPayload(it) }
                    preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_REVISION, payload.revision.toString())
                    preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_PENDING_MUTATIONS, "0")
                    preferenceDataSource.setString(
                        PrefConst.KEY_REMOTE_AGENT_LAST_ERROR,
                        "config conflict: cloud revision ${payload.revision} replaced local pending snapshot",
                    )
                    setSyncState("conflict")
                    publishHookPrefs()
                    return@use
                }
                if (!response.isSuccessful) {
                    throw IllegalStateException(responseText.ifBlank { "push failed: ${response.code}" })
                }
                val payload = gson.fromJson(responseText, ConfigSnapshotResponse::class.java)
                preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_REVISION, payload.revision.toString())
                preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_PENDING_MUTATIONS, "0")
                preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_APP_CATALOG_DIGEST, appCatalogDigest)
                preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_PUSH_AT, nowEpochMillis().toString())
                preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_ERROR, "")
                setSyncState("idle")
                publishHookPrefs()
            }
        }.onFailure {
            preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_ERROR, it.message ?: it.javaClass.simpleName)
            setSyncState("error")
        }.getOrThrow()
        getSnapshot()
        }
    }

    suspend fun uploadRecentRecords(limit: Int = 100): RemoteAgentSnapshot = withContext(Dispatchers.IO) {
        syncMutex.withLock {
        val snapshot = getSnapshot()
        require(snapshot.bound) { "device not bound" }
        val token = InternalSecretStore.getString(appContext, PrefConst.KEY_REMOTE_AGENT_DEVICE_TOKEN, "")
        val records = RuntimeGraph.from(appContext).relayRecordRepository.listRecords(limit).map {
            val metadata = JsonObject().apply {
                addProperty("localRecordId", it.id)
                addProperty("company", it.company)
                addProperty("notifyChannelId", it.notifyChannelId)
                addProperty("simSlot", it.simSlot)
                addProperty("subId", it.subId)
                addProperty("contactName", it.contactName)
                addProperty("phoneArea", it.phoneArea)
                addProperty("forwardStatus", it.forwardStatus)
                addProperty("forwardTarget", it.forwardTarget)
                addProperty("forwardMessage", it.forwardMessage)
                addProperty("forwardTime", it.forwardTime)
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
        val body = gson.toJson(mapOf("records" to records)).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${normalizedBackendBaseUrl()}/api/v1/agent/records:batch")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        executeSimpleAgentWrite(request, PrefConst.KEY_REMOTE_AGENT_LAST_PUSH_AT, "records_upload")
        getSnapshot()
        }
    }

    suspend fun startupSync() = withContext(Dispatchers.IO) {
        val snapshot = getSnapshot()
        if (!snapshot.bound) return@withContext
        scheduleBackgroundSync("startup")
    }

    fun onAppForegrounded() {
        scheduleBackgroundSync("app_foreground")
    }

    fun onAppBackgrounded() {
        scheduleBackgroundSync("app_background")
    }

    suspend fun noteLocalMutation(source: String) = withContext(Dispatchers.IO) {
        if (isApplyingRemoteConfig()) return@withContext
        val currentPending = preferenceDataSource.getString(
            PrefConst.KEY_REMOTE_AGENT_PENDING_MUTATIONS,
            "0",
        ).toIntOrNull() ?: 0
        preferenceDataSource.setString(
            PrefConst.KEY_REMOTE_AGENT_PENDING_MUTATIONS,
            (currentPending + 1).toString(),
        )
        if (preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_SYNC_STATE, "idle") == "idle") {
            setSyncState("dirty")
        }
        publishHookPrefs()
        scheduleBackgroundSync("local_mutation:$source")
    }

    fun scheduleRecordUpload(reason: String) {
        scheduleBackgroundSync("records:$reason", preferConfigPush = false)
    }

    fun scheduleMessageTriggeredSync(reason: String) {
        scheduleBackgroundSync("message:$reason", preferConfigPush = true)
    }

    private suspend fun executeSimpleAgentWrite(request: Request, timestampKey: String, stateLabel: String) {
        runCatching {
            setSyncState(stateLabel)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException(response.body.string().ifBlank { "$stateLabel failed: ${response.code}" })
                }
                preferenceDataSource.setString(timestampKey, nowEpochMillis().toString())
                preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_ERROR, "")
                setSyncState("idle")
                publishHookPrefs()
            }
        }.onFailure {
            preferenceDataSource.setString(PrefConst.KEY_REMOTE_AGENT_LAST_ERROR, it.message ?: it.javaClass.simpleName)
            setSyncState("error")
        }.getOrThrow()
    }

    private suspend fun buildConfigSnapshotPayload(): JsonObject {
        return gson.toJsonTree(buildConfigSnapshotModel()).asJsonObject
    }

    private suspend fun buildConfigSnapshotModel(): RemoteConfigPayload {
        val runtimeGraph = RuntimeGraph.from(appContext)
        val settingsRepository = runtimeGraph.settingsRepository
        val configRepository = runtimeGraph.configRepository
        return RemoteConfigPayload(
            general = settingsRepository.getGeneralSettings(),
            verification = settingsRepository.getVerificationSettings(),
            relay = settingsRepository.getRelaySettings(),
            diagnostics = settingsRepository.getDiagnosticsSettings(),
            advanced = settingsRepository.getAdvancedSnapshot(),
            specialAlerts = settingsRepository.getSpecialAlertSettings(),
            messageTypeGates = settingsRepository.getMessageTypeGates(),
            forwardTypeGates = settingsRepository.getForwardTypeGates(),
            records = settingsRepository.getRecordSettings(),
            smsBlacklist = settingsRepository.getSmsBlacklistSettings(),
            simRemarks = settingsRepository.getSimRemarkSettings(),
            forwardCommon = settingsRepository.loadForwardCommonConfig(),
            appNotifyTemplate = settingsRepository.loadAppNotifyTemplate(),
            callNotifyTemplate = settingsRepository.loadCallNotifyTemplate(),
            overview = settingsRepository.getOverviewSettings(),
            senders = configRepository.getAllSenders(),
            rules = configRepository.getAllRules(),
            smsCodeRules = configRepository.getAllSmsCodeRules(),
            appInfos = resolveInstalledAppCatalog(configRepository.getAllAppInfo()),
            notifyRoutes = runtimeGraph.database.notifyRouteRuleDao().getAll(),
            forwardFilters = runtimeGraph.database.forwardFilterRuleDao().getAll().map { it.toDomain() },
        )
    }

    private suspend fun applyRemoteConfigPayload(snapshot: JsonObject) {
        applyingRemoteConfigDepth.incrementAndGet()
        try {
            val payload = gson.fromJson(
                mergeRemoteConfigJson(
                    base = buildConfigSnapshotPayload(),
                    incoming = snapshot,
                ),
                RemoteConfigPayload::class.java,
            )
            val runtimeGraph = RuntimeGraph.from(appContext)
            val settingsRepository = runtimeGraph.settingsRepository
            val configRepository = runtimeGraph.configRepository

            settingsRepository.updateGeneralSettings(
                GeneralSettingsUpdate(
                    moduleEnabled = payload.general.moduleEnabled,
                    accordionMode = payload.general.accordionMode,
                ),
            )
            settingsRepository.updateVerificationSettings(
                VerificationSettingsUpdate(
                    verificationFeaturesEnabled = payload.verification.verificationFeaturesEnabled,
                    copyToClipboard = payload.verification.copyToClipboard,
                    showToast = payload.verification.showToast,
                    showCodeNotification = payload.verification.showCodeNotification,
                    notificationOwner = payload.verification.notificationOwner,
                    autoCancelNotification = payload.verification.autoCancelNotification,
                    notificationRetentionTime = payload.verification.notificationRetentionTime,
                    autoInputEnabled = payload.verification.autoInputEnabled,
                    autoEnterEnabled = payload.verification.autoEnterEnabled,
                    autoInputDelay = payload.verification.autoInputDelay,
                    autoInputInterval = payload.verification.autoInputInterval,
                    relayKeywords = payload.verification.relayKeywords,
                    blockSmsEnabled = payload.verification.blockSmsEnabled,
                ),
            )
            settingsRepository.updateRelaySettings(
                RelaySettingsUpdate(relayFeaturesEnabled = payload.relay.relayFeaturesEnabled),
            )
            settingsRepository.updateDiagnosticsSettings(
                DiagnosticsSettingsUpdate(
                    rootDbCatchupEnabled = payload.diagnostics.rootDbCatchupEnabled,
                    rootDbCatchupIntervalMin = payload.diagnostics.rootDbCatchupIntervalMin,
                    rootDbCatchupWriteback = payload.diagnostics.rootDbCatchupWriteback,
                    forceStopRecoveryEnabled = payload.diagnostics.forceStopRecoveryEnabled,
                    forceStopRecoveryRelaunchOnceEnabled = payload.diagnostics.forceStopRecoveryRelaunchOnceEnabled,
                    verboseLogMode = payload.diagnostics.verboseLogMode,
                    sensitiveDebugLogMode = payload.diagnostics.sensitiveDebugLogMode,
                    runtimeLogFileSizeMb = payload.diagnostics.runtimeLogFileSizeMb,
                    autoUpdateOnStart = payload.diagnostics.autoUpdateOnStart,
                    autoUpdateWifiOnly = payload.diagnostics.autoUpdateWifiOnly,
                    analyticsEnabled = payload.diagnostics.analyticsEnabled,
                ),
            )
            settingsRepository.updateAdvanced(
                AdvancedSettingsUpdate(enableSmsBlacklist = payload.advanced.enableSmsBlacklist),
            )
            settingsRepository.updateSpecialAlertSettings(
                SpecialAlertSettingsUpdate(
                    lowBatteryReminderEnabled = payload.specialAlerts.lowBatteryReminderEnabled,
                    lowBatteryThreshold = payload.specialAlerts.lowBatteryThreshold,
                    lowBatteryChannelId = payload.specialAlerts.lowBatteryChannelId,
                    fullBatteryReminderEnabled = payload.specialAlerts.fullBatteryReminderEnabled,
                    fullBatteryChannelId = payload.specialAlerts.fullBatteryChannelId,
                    callAlertLocalEnabled = payload.specialAlerts.callAlertLocalEnabled,
                    callAlertChannelId = payload.specialAlerts.callAlertChannelId,
                    smsKeywordEnabled = payload.specialAlerts.smsKeywordEnabled,
                    smsKeywordKeywords = payload.specialAlerts.smsKeywordKeywords,
                    smsKeywordNotificationEnabled = payload.specialAlerts.smsKeywordNotificationEnabled,
                    smsKeywordSoundEnabled = payload.specialAlerts.smsKeywordSoundEnabled,
                    smsKeywordVibrateEnabled = payload.specialAlerts.smsKeywordVibrateEnabled,
                    appKeywordEnabled = payload.specialAlerts.appKeywordEnabled,
                    appKeywordKeywords = payload.specialAlerts.appKeywordKeywords,
                    appKeywordNotificationEnabled = payload.specialAlerts.appKeywordNotificationEnabled,
                    appKeywordSoundEnabled = payload.specialAlerts.appKeywordSoundEnabled,
                    appKeywordVibrateEnabled = payload.specialAlerts.appKeywordVibrateEnabled,
                ),
            )
            settingsRepository.updateMessageTypeGates(
                MessageTypeGateUpdate(
                    smsCodeEnabled = payload.messageTypeGates.smsCodeEnabled,
                    smsPlainEnabled = payload.messageTypeGates.smsPlainEnabled,
                    appNotifyEnabled = payload.messageTypeGates.appNotifyEnabled,
                    callNotifyEnabled = payload.messageTypeGates.callNotifyEnabled,
                ),
            )
            settingsRepository.updateForwardTypeGates(
                ForwardTypeGateUpdate(
                    smsCodeEnabled = payload.forwardTypeGates.smsCodeEnabled,
                    smsPlainEnabled = payload.forwardTypeGates.smsPlainEnabled,
                    appNotifyEnabled = payload.forwardTypeGates.appNotifyEnabled,
                    callNotifyEnabled = payload.forwardTypeGates.callNotifyEnabled,
                    callNotifyFinalEnabled = payload.forwardTypeGates.callNotifyFinalEnabled,
                ),
            )
            settingsRepository.updateRecordSettings(
                RecordSettingsUpdate(
                    codeRecordEnabled = payload.records.codeRecordEnabled,
                    plainSmsRecordEnabled = payload.records.plainSmsRecordEnabled,
                    appNotifyRecordEnabled = payload.records.appNotifyRecordEnabled,
                    callNotifyRecordEnabled = payload.records.callNotifyRecordEnabled,
                    codeHistoryLimit = payload.records.codeHistoryLimit,
                    plainSmsHistoryLimit = payload.records.plainSmsHistoryLimit,
                    appNotifyHistoryLimit = payload.records.appNotifyHistoryLimit,
                    callNotifyHistoryLimit = payload.records.callNotifyHistoryLimit,
                ),
            )
            settingsRepository.updateSmsBlacklistSettings(
                SmsBlacklistSettingsUpdate(
                    enabled = payload.smsBlacklist.enabled,
                    deleteBlockedSms = payload.smsBlacklist.deleteBlockedSms,
                    blockIncomingSms = payload.smsBlacklist.blockIncomingSms,
                    numbers = payload.smsBlacklist.numbers,
                    prefixes = payload.smsBlacklist.prefixes,
                    regexRules = payload.smsBlacklist.regexRules,
                    contentRules = payload.smsBlacklist.contentRules,
                ),
            )
            settingsRepository.updateSimRemarkSettings(
                SimRemarkSettingsUpdate(
                    simSlot1Remark = payload.simRemarks.simSlot1Remark,
                    simSlot2Remark = payload.simRemarks.simSlot2Remark,
                ),
            )
            settingsRepository.saveForwardCommonConfig(payload.forwardCommon)
            settingsRepository.saveAppNotifyTemplate(payload.appNotifyTemplate)
            settingsRepository.saveCallNotifyTemplate(payload.callNotifyTemplate)
            settingsRepository.updateOverviewSettings(
                OverviewSettingsUpdate(
                    cardOrder = payload.overview.cardOrder,
                    enabledCardIds = payload.overview.enabledCardIds,
                    chartType = payload.overview.chartType,
                    chartWindow = payload.overview.chartWindow,
                ),
            )

            configRepository.clearAllSenders()
            payload.senders.forEach { configRepository.insertSender(it) }
            configRepository.clearAllRules()
            payload.rules.forEach { configRepository.insertRule(it) }
            configRepository.clearAllSmsCodeRules()
            configRepository.insertSmsCodeRules(payload.smsCodeRules)
            configRepository.clearAllAppInfo()
            payload.appInfos.forEach { configRepository.upsertAppInfo(it) }
            configRepository.clearAllNotifyRouteRules()
            configRepository.insertNotifyRouteRules(payload.notifyRoutes)
            configRepository.clearAllForwardFilterRules()
            payload.forwardFilters.forEach { configRepository.insertForwardFilterRule(it) }
            configRepository.checkpoint()
        } finally {
            applyingRemoteConfigDepth.decrementAndGet()
        }
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

    private fun isApplyingRemoteConfig(): Boolean = applyingRemoteConfigDepth.get() > 0

    private suspend fun pushInstalledAppCatalogIfNeeded() {
        val snapshot = getSnapshot()
        if (!snapshot.bound) return
        val currentConfigs = RuntimeGraph.from(appContext).configRepository.getAllAppInfo()
        val digest = computeAppCatalogDigest(resolveInstalledAppCatalog(currentConfigs))
        val lastDigest = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_LAST_APP_CATALOG_DIGEST, "")
        if (digest == lastDigest) return
        val pendingMutations = preferenceDataSource.getString(
            PrefConst.KEY_REMOTE_AGENT_PENDING_MUTATIONS,
            "0",
        ).toIntOrNull() ?: 0
        preferenceDataSource.setString(
            PrefConst.KEY_REMOTE_AGENT_PENDING_MUTATIONS,
            pendingMutations.coerceAtLeast(1).toString(),
        )
        publishHookPrefs()
        pushConfigSnapshot()
    }

    private fun resolveInstalledAppCatalog(existingConfigs: List<AppInfo>): List<AppInfo> {
        val packageManager = appContext.packageManager
        val existingByPackage = existingConfigs.associateBy { it.packageName }
        val installedApps = runCatching {
            packageManager.getInstalledApplications(PackageManager.MATCH_ALL)
        }.getOrDefault(emptyList<ApplicationInfo>())

        val merged = linkedMapOf<String, AppInfo>()
        installedApps.asSequence()
            .filter { it.packageName.isNotBlank() }
            .forEach { applicationInfo ->
                val packageName = applicationInfo.packageName
                val label = runCatching {
                    packageManager.getApplicationLabel(applicationInfo).toString()
                }.getOrDefault(packageName)
                val existing = existingByPackage[packageName]
                merged[packageName] = AppInfo(
                    packageName = packageName,
                    label = label,
                    blocked = existing?.blocked ?: false,
                    forwarding = existing?.forwarding ?: false,
                    forwardingConfigured = existing?.forwardingConfigured ?: false,
                    notifyTemplate = existing?.notifyTemplate.orEmpty(),
                )
            }
        existingConfigs.forEach { existing ->
            merged.putIfAbsent(existing.packageName, existing)
        }
        return merged.values.sortedBy { (it.label ?: it.packageName).lowercase() + "|" + it.packageName }
    }

    private fun computeAppCatalogDigest(appInfos: List<AppInfo>): String {
        return gson.toJson(
            appInfos.sortedBy { it.packageName }.map {
                listOf(
                    it.packageName,
                    it.label ?: "",
                    it.blocked,
                    it.forwarding,
                    it.forwardingConfigured,
                    it.notifyTemplate,
                )
            },
        )
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
                val afterHeartbeat = getSnapshot()
                if (preferConfigPush && afterHeartbeat.pendingMutations > 0) {
                    runCatching { pushConfigSnapshot() }
                } else {
                    runCatching { pullConfigSnapshot() }
                }
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

private const val HTTP_CONFLICT = 409

internal fun mergeRemoteConfigJson(
    base: JsonObject,
    incoming: JsonObject,
): JsonObject {
    val merged = base.deepCopy()
    incoming.entrySet().forEach { (key, incomingValue) ->
        val baseValue = merged.get(key)
        merged.add(key, mergeRemoteConfigElement(baseValue, incomingValue))
    }
    return merged
}

private fun mergeRemoteConfigElement(
    base: JsonElement?,
    incoming: JsonElement?,
): JsonElement {
    if (incoming == null || incoming.isJsonNull) {
        return base?.deepCopy() ?: JsonObject()
    }
    if (base != null && base.isJsonObject && incoming.isJsonObject) {
        return mergeRemoteConfigJson(base.asJsonObject, incoming.asJsonObject)
    }
    return incoming.deepCopy()
}
