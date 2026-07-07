package io.github.magisk317.relay.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.android.data.db.entity.AppInfo
import io.github.magisk317.relay.android.data.mapper.ConfigMapper.toDomain
import io.github.magisk317.relay.android.data.mapper.ConfigMapper.toEntity
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.contract.json.RelayJson
import io.github.magisk317.relay.contract.model.LocalConfigMirrorPayload
import io.github.magisk317.relay.contract.model.LocalConfigMirror
import io.github.magisk317.relay.contract.model.LocalConfigRevision
import io.github.magisk317.relay.contract.model.LocalDirtyState
import io.github.magisk317.relay.contract.model.SnapshotAppInfo
import io.github.magisk317.relay.contract.repository.LocalConfigRepository
import io.github.magisk317.relay.contract.repository.LocalConfigMirrorRejectedException
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.contract.settings.AdvancedSettingsUpdate
import io.github.magisk317.relay.contract.settings.DiagnosticsSettingsUpdate
import io.github.magisk317.relay.contract.settings.ForwardTypeGateUpdate
import io.github.magisk317.relay.contract.settings.GeneralSettingsUpdate
import io.github.magisk317.relay.contract.settings.MessageTypeGateUpdate
import io.github.magisk317.relay.contract.settings.OverviewSettingsUpdate
import io.github.magisk317.relay.contract.settings.RecordSettingsUpdate
import io.github.magisk317.relay.contract.settings.RelaySettingsUpdate
import io.github.magisk317.relay.contract.settings.SimRemarkSettingsUpdate
import io.github.magisk317.relay.contract.settings.SmsBlacklistSettingsUpdate
import io.github.magisk317.relay.contract.settings.SpecialAlertSettingsUpdate
import io.github.magisk317.relay.contract.settings.VerificationSettingsUpdate
import io.github.magisk317.relay.data.remote.toDomain
import io.github.magisk317.relay.data.remote.toEntity
import io.github.magisk317.relay.data.remote.toSnapshot
import io.github.magisk317.relay.engine.model.ForwardFilterRule
import io.github.magisk317.relay.engine.service.AppConfigRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import java.util.concurrent.atomic.AtomicInteger

class LocalConfigRepositoryImpl(
    context: Context,
    private val preferenceDataSource: PreferenceDataSource,
    private val settingsRepository: SettingsPreferencesRepository,
    private val configRepository: AppConfigRepository,
    private val database: AppDatabase,
) : LocalConfigRepository {
    private val appContext = context.applicationContext ?: context
    private val mutationSources = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val applyingMirrorImportDepth = AtomicInteger(0)

    override suspend fun exportMirror(): LocalConfigMirror {
        val revision = getRevision()
        val dirtyState = getDirtyState()
        val content = RelayJson.parseElement(
            RelayJson.encode(LocalConfigMirrorPayload.serializer(), buildLocalMirrorModel()),
        ) as JsonObject
        return LocalConfigMirror(
            revision = revision,
            dirtyState = dirtyState,
            content = content,
        )
    }

    override suspend fun getRevision(): LocalConfigRevision {
        migrateLegacyStateIfNeeded()
        return LocalConfigRevision(
            preferenceDataSource.getString(PrefConst.KEY_LOCAL_CONFIG_REVISION, "0").toLongOrNull() ?: 0L,
        )
    }

    override suspend fun getDirtyState(): LocalDirtyState {
        migrateLegacyStateIfNeeded()
        val revision = getRevision()
        val pendingLocalChanges = preferenceDataSource.getString(
            PrefConst.KEY_LOCAL_CONFIG_PENDING_LOCAL_CHANGES,
            "0",
        ).toIntOrNull() ?: 0
        return LocalDirtyState(
            revision = revision,
            pendingLocalChanges = pendingLocalChanges,
            dirty = pendingLocalChanges > 0,
        )
    }

    override suspend fun noteLocalMutation(source: String): LocalDirtyState {
        if (isApplyingMirrorImport()) {
            return getDirtyState()
        }
        val nextRevision = getRevision().value + 1L
        val nextPending = getDirtyState().pendingLocalChanges + 1
        preferenceDataSource.setString(PrefConst.KEY_LOCAL_CONFIG_REVISION, nextRevision.toString())
        preferenceDataSource.setString(PrefConst.KEY_LOCAL_CONFIG_PENDING_LOCAL_CHANGES, nextPending.toString())
        mutationSources.tryEmit(source)
        return getDirtyState()
    }

    override suspend fun markMirrorSynced(revision: Long): LocalDirtyState {
        val currentRevision = getRevision().value
        preferenceDataSource.setString(
            PrefConst.KEY_LOCAL_CONFIG_REVISION,
            maxOf(currentRevision, revision).toString(),
        )
        preferenceDataSource.setString(PrefConst.KEY_LOCAL_CONFIG_PENDING_LOCAL_CHANGES, "0")
        return getDirtyState()
    }

    override suspend fun applyMirror(
        mirrorContent: JsonObject,
        revision: Long,
        source: String,
    ): LocalConfigMirror {
        val dirtyState = getDirtyState()
        when {
            dirtyState.dirty -> throw LocalConfigMirrorRejectedException("local_dirty")
            revision < dirtyState.revision.value -> throw LocalConfigMirrorRejectedException("stale_local_revision")
        }
        applyingMirrorImportDepth.incrementAndGet()
        try {
            val payload = RelayJson.decode(
                LocalConfigMirrorPayload.serializer(),
                mergeRemoteConfigJson(
                    base = exportMirror().content,
                    incoming = mirrorContent,
                ).toString(),
            )

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
                RelaySettingsUpdate(
                    relayFeaturesEnabled = payload.relay.relayFeaturesEnabled,
                    smsForwardDedupWindowSec = payload.relay.smsForwardDedupWindowSec,
                ),
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
                    runtimeLogRetentionDays = payload.diagnostics.runtimeLogRetentionDays,
                    autoUpdateOnStart = payload.diagnostics.autoUpdateOnStart,
                    autoUpdateWifiOnly = payload.diagnostics.autoUpdateWifiOnly,
                    analyticsEnabled = payload.diagnostics.analyticsEnabled,
                    keepAliveOomAdj = payload.diagnostics.keepAliveOomAdj,
                    keepAliveAntiKill = payload.diagnostics.keepAliveAntiKill,
                    keepAliveStandbyBypass = payload.diagnostics.keepAliveStandbyBypass,
                    keepAliveDozeBypass = payload.diagnostics.keepAliveDozeBypass,
                    keepAliveAccessibilityHeartbeat = payload.diagnostics.keepAliveAccessibilityHeartbeat,
                    keepAliveDedicatedService = payload.diagnostics.keepAliveDedicatedService,
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
                    chargingChangeReminderEnabled = payload.specialAlerts.chargingChangeReminderEnabled,
                    chargingChangeChannelId = payload.specialAlerts.chargingChangeChannelId,
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
                    smsBlacklistHitRecordEnabled = payload.records.smsBlacklistHitRecordEnabled,
                    smsBlacklistHitHistoryLimit = payload.records.smsBlacklistHitHistoryLimit,
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
            payload.senders.forEach { configRepository.insertSender(it.toDomain()) }
            configRepository.clearAllRules()
            payload.rules.forEach { configRepository.insertRule(it.toDomain()) }
            configRepository.clearAllSmsCodeRules()
            configRepository.insertSmsCodeRules(payload.smsCodeRules.map { it.toEntity() })

            val deviceIdStr = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_DEVICE_ID, "0")
            payload.deviceAppInfos?.let { map ->
                preferenceDataSource.setString(
                    PrefConst.KEY_REMOTE_AGENT_DEVICE_APP_INFOS,
                    RelayJson.format.encodeToString(map),
                )
            }
            configRepository.clearAllAppInfo()
            val myAppInfos = payload.deviceAppInfos?.get(deviceIdStr).orEmpty().map { it.toEntity() }
            myAppInfos.forEach { configRepository.upsertAppInfo(it) }

            configRepository.clearAllNotifyRouteRules()
            configRepository.insertNotifyRouteRules(payload.notifyRoutes.map { it.toEntity() })
            configRepository.clearAllForwardFilterRules()
            payload.forwardFilters.forEach { configRepository.insertForwardFilterRule(it.toDomain()) }
            configRepository.checkpoint()

            preferenceDataSource.setString(PrefConst.KEY_LOCAL_CONFIG_REVISION, revision.toString())
            preferenceDataSource.setString(PrefConst.KEY_LOCAL_CONFIG_PENDING_LOCAL_CHANGES, "0")
            mutationSources.tryEmit(source)
            return exportMirror()
        } finally {
            applyingMirrorImportDepth.decrementAndGet()
        }
    }

    override fun observeMutationSources(): Flow<String> = mutationSources

    private suspend fun buildLocalMirrorModel(): LocalConfigMirrorPayload {
        return LocalConfigMirrorPayload(
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
            senders = configRepository.getAllSenders().map { it.toSnapshot() },
            rules = configRepository.getAllRules().map { it.toSnapshot() },
            smsCodeRules = configRepository.getAllSmsCodeRules().map { it.toEntity().toSnapshot() },
            deviceAppInfos = run {
                val deviceIdStr = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_DEVICE_ID, "0")
                val savedDeviceAppInfosRaw = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_DEVICE_APP_INFOS, "")
                val deviceAppInfos = if (savedDeviceAppInfosRaw.isNotBlank()) {
                    runCatching {
                        RelayJson.format.decodeFromString<Map<String, List<SnapshotAppInfo>>>(savedDeviceAppInfosRaw).toMutableMap()
                    }.getOrDefault(mutableMapOf())
                } else {
                    mutableMapOf()
                }
                deviceAppInfos[deviceIdStr] = resolveInstalledAppCatalog(
                    configRepository.getAllAppInfo().map { it.toEntity() },
                ).map { it.toSnapshot() }
                deviceAppInfos
            },
            notifyRoutes = database.notifyRouteRuleDao().getAll().map { it.toSnapshot() },
            forwardFilters = database.forwardFilterRuleDao().getAll().map { it.toDomain().toSnapshot() },
        )
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

    private suspend fun migrateLegacyStateIfNeeded() {
        val currentRevision = preferenceDataSource.getString(PrefConst.KEY_LOCAL_CONFIG_REVISION, "")
        if (currentRevision.isBlank()) {
            val legacyRevision = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_LAST_REVISION, "0")
            preferenceDataSource.setString(PrefConst.KEY_LOCAL_CONFIG_REVISION, legacyRevision)
        }
        val currentPending = preferenceDataSource.getString(PrefConst.KEY_LOCAL_CONFIG_PENDING_LOCAL_CHANGES, "")
        if (currentPending.isBlank()) {
            val legacyPending = preferenceDataSource.getString(PrefConst.KEY_REMOTE_AGENT_PENDING_LOCAL_CHANGES, "0")
            preferenceDataSource.setString(PrefConst.KEY_LOCAL_CONFIG_PENDING_LOCAL_CHANGES, legacyPending)
        }
    }

    private fun isApplyingMirrorImport(): Boolean = applyingMirrorImportDepth.get() > 0
}
