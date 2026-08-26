package io.github.magisk317.relay.data.repository

import android.content.Context
import io.github.magisk317.relay.contract.constant.DispatchStrategy
import io.github.magisk317.relay.contract.constant.MessageType
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.contract.settings.*
import io.github.magisk317.relay.android.common.utils.SensitiveLogPolicy
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.otel.MagiskOtelBootstrap
import io.github.magisk317.relay.bootstrap.RuntimeDependencies
import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.contract.model.ForwardCommonConfig
import io.github.magisk317.relay.contract.model.ForwardSilentPeriodConfig
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.android.common.utils.DeviceIdentityUtils
import io.github.magisk317.relay.android.prefs.AppPreferenceTransactions
import io.github.magisk317.relay.android.prefs.HookPreferenceMirror
import io.github.magisk317.relay.android.prefs.HookPreferenceSpecs
import io.github.magisk317.relay.engine.schedule.ForwardSilentPeriodEvaluator
import io.github.magisk317.smscode.rule.constant.SmsCodeConst
import io.github.magisk317.smscode.runtime.contract.prefs.AtomicPreferencePersistence
import io.github.magisk317.smscode.runtime.contract.prefs.PreferenceCommitResult
import io.github.magisk317.smscode.runtime.contract.prefs.PreferenceSpec
import kotlinx.coroutines.flow.Flow

class SettingsRepository(
    context: Context,
    private val preferenceDataSource: PreferenceDataSource,
) : SettingsPreferencesRepository {
    private companion object {
        const val UI_KIT_STYLE_EXPRESSIVE = 0
    }

    private val appContext = context.applicationContext ?: context

    override suspend fun getGeneralSettings(): GeneralSettingsSnapshot {
        return GeneralSettingsSnapshot(
            moduleEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_ENABLE, true),
            accordionMode = preferenceDataSource.getBoolean(PrefConst.KEY_SETTINGS_ACCORDION_MODE, true),
        )
    }

    override suspend fun updateGeneralSettings(update: GeneralSettingsUpdate): GeneralSettingsSnapshot {
        update.moduleEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_ENABLE, it) }
        update.accordionMode?.let { preferenceDataSource.setBoolean(PrefConst.KEY_SETTINGS_ACCORDION_MODE, it) }
        publishHookPrefsAndNoteLocalMutation("settings.general")
        return getGeneralSettings()
    }

    override suspend fun getVerificationSettings(): VerificationSettingsSnapshot {
        return VerificationSettingsSnapshot(
            verificationFeaturesEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_VERIFICATION_FEATURES_ENABLED, true),
            copyToClipboard = preferenceDataSource.getBoolean(PrefConst.KEY_COPY_TO_CLIPBOARD, false),
            showToast = preferenceDataSource.getBoolean(PrefConst.KEY_SHOW_TOAST, true),
            showCodeNotification = preferenceDataSource.getBoolean(PrefConst.KEY_SHOW_CODE_NOTIFICATION, true),
            autoCancelNotification = preferenceDataSource.getBoolean(PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION, false),
            notificationRetentionTime = preferenceDataSource.getString(
                PrefConst.KEY_NOTIFICATION_RETENTION_TIME,
                PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT,
            ),
            autoInputEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, true),
            autoEnterEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_ENABLE_AUTO_ENTER_CODE, false),
            autoInputDelay = loadAutoInputDelayMs(),
            autoInputInterval = preferenceDataSource.getString(
                PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL,
                PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT,
            ),
            relayKeywords = preferenceDataSource.getString(
                PrefConst.KEY_SMSCODE_KEYWORDS,
                SmsCodeConst.VERIFICATION_KEYWORDS_REGEX,
            ),
            blockSmsEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_BLOCK_SMS, false),
        )
    }

    override suspend fun updateVerificationSettings(update: VerificationSettingsUpdate): VerificationSettingsSnapshot {
        val result = AppPreferenceTransactions.commit(
            context = appContext,
            persistence = AtomicPreferencePersistence(preferenceDataSource::persist),
        ) {
            update.verificationFeaturesEnabled?.let { set(VerificationPreferenceSpecs.featuresEnabled, it) }
            update.copyToClipboard?.let { set(VerificationPreferenceSpecs.copyToClipboard, it) }
            update.showToast?.let { set(VerificationPreferenceSpecs.showToast, it) }
            update.showCodeNotification?.let { set(VerificationPreferenceSpecs.showCodeNotification, it) }
            update.autoCancelNotification?.let { set(VerificationPreferenceSpecs.autoCancelNotification, it) }
            update.notificationRetentionTime?.let { set(VerificationPreferenceSpecs.notificationRetentionTime, it) }
            update.autoInputEnabled?.let { set(HookPreferenceSpecs.autoInputEnabled, it) }
            update.autoEnterEnabled?.let { set(HookPreferenceSpecs.autoEnterEnabled, it) }
            update.autoInputDelay?.let { set(HookPreferenceSpecs.autoInputDelay, it) }
            update.autoInputInterval?.let { set(HookPreferenceSpecs.autoInputInterval, it) }
            update.relayKeywords?.let { set(VerificationPreferenceSpecs.relayKeywords, it) }
            update.blockSmsEnabled?.let { set(VerificationPreferenceSpecs.blockSms, it) }
        }
        handleVerificationCommitResult(result)
        return getVerificationSettings()
    }

    override suspend fun getRelaySettings(): RelaySettingsSnapshot {
        return RelaySettingsSnapshot(
            relayFeaturesEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_RELAY_FEATURES_ENABLED, true),
            smsForwardDedupWindowSec = preferenceDataSource.getString(
                PrefConst.KEY_SMS_FORWARD_DEDUP_WINDOW_SEC,
                PrefConst.SMS_FORWARD_DEDUP_WINDOW_SEC_DEFAULT.toString(),
            ).toIntOrNull()?.coerceIn(
                PrefConst.SMS_FORWARD_DEDUP_WINDOW_SEC_MIN,
                PrefConst.SMS_FORWARD_DEDUP_WINDOW_SEC_MAX,
            ) ?: PrefConst.SMS_FORWARD_DEDUP_WINDOW_SEC_DEFAULT,
        )
    }

    override suspend fun updateRelaySettings(update: RelaySettingsUpdate): RelaySettingsSnapshot {
        update.relayFeaturesEnabled?.let {
            preferenceDataSource.setBoolean(PrefConst.KEY_RELAY_FEATURES_ENABLED, it)
        }
        update.smsForwardDedupWindowSec?.let {
            preferenceDataSource.setString(
                PrefConst.KEY_SMS_FORWARD_DEDUP_WINDOW_SEC,
                it.coerceIn(
                    PrefConst.SMS_FORWARD_DEDUP_WINDOW_SEC_MIN,
                    PrefConst.SMS_FORWARD_DEDUP_WINDOW_SEC_MAX,
                ).toString(),
            )
        }
        publishHookPrefsAndNoteLocalMutation("settings.relay")
        return getRelaySettings()
    }

    override suspend fun getDiagnosticsSettings(): DiagnosticsSettingsSnapshot {
        return DiagnosticsSettingsSnapshot(
            rootDbCatchupEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_ROOT_DB_CATCHUP_ENABLE, false),
            rootDbCatchupIntervalMin = preferenceDataSource.getString(PrefConst.KEY_ROOT_DB_CATCHUP_INTERVAL_MIN, "5"),
            rootDbCatchupWriteback = preferenceDataSource.getBoolean(PrefConst.KEY_ROOT_DB_CATCHUP_WRITEBACK, false),
            forceStopRecoveryEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_FORCE_STOP_RECOVERY, false),
            forceStopRecoveryRelaunchOnceEnabled = preferenceDataSource.getBoolean(
                PrefConst.KEY_FORCE_STOP_RECOVERY_RELAUNCH_ONCE,
                false,
            ),
            verboseLogMode = preferenceDataSource.getBoolean(PrefConst.KEY_VERBOSE_LOG_MODE, false),
            sensitiveDebugLogMode = preferenceDataSource.getBoolean(PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE, false),
            runtimeLogRetentionDays = preferenceDataSource.getInt(
                PrefConst.KEY_RUNTIME_LOG_RETENTION_DAYS,
                PrefConst.RUNTIME_LOG_RETENTION_DAYS_DEFAULT,
            ),
            autoUpdateOnStart = preferenceDataSource.getBoolean(PrefConst.KEY_AUTO_UPDATE_ON_START, true),
            autoUpdateWifiOnly = preferenceDataSource.getBoolean(PrefConst.KEY_AUTO_UPDATE_WIFI_ONLY, true),
            analyticsEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_ENABLE_ANALYTICS, true),
            keepAliveOomAdj = preferenceDataSource.getBoolean(PrefConst.KEY_KEEPALIVE_OOM_ADJ, false),
            keepAliveAntiKill = preferenceDataSource.getBoolean(PrefConst.KEY_KEEPALIVE_ANTI_KILL, false),
            keepAliveStandbyBypass = preferenceDataSource.getBoolean(PrefConst.KEY_KEEPALIVE_STANDBY_BYPASS, false),
            keepAliveDozeBypass = preferenceDataSource.getBoolean(PrefConst.KEY_KEEPALIVE_DOZE_BYPASS, false),
            keepAliveAccessibilityHeartbeat = preferenceDataSource.getBoolean(PrefConst.KEY_KEEPALIVE_ACCESSIBILITY_HEARTBEAT, false),
            keepAliveDedicatedService = preferenceDataSource.getBoolean(PrefConst.KEY_KEEPALIVE_DEDICATED_SERVICE, false),
        )
    }

    override suspend fun updateDiagnosticsSettings(update: DiagnosticsSettingsUpdate): DiagnosticsSettingsSnapshot {
        update.rootDbCatchupEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_ROOT_DB_CATCHUP_ENABLE, it) }
        update.rootDbCatchupIntervalMin?.let { preferenceDataSource.setString(PrefConst.KEY_ROOT_DB_CATCHUP_INTERVAL_MIN, it) }
        update.rootDbCatchupWriteback?.let { preferenceDataSource.setBoolean(PrefConst.KEY_ROOT_DB_CATCHUP_WRITEBACK, it) }
        update.forceStopRecoveryEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_FORCE_STOP_RECOVERY, it) }
        update.forceStopRecoveryRelaunchOnceEnabled?.let {
            preferenceDataSource.setBoolean(PrefConst.KEY_FORCE_STOP_RECOVERY_RELAUNCH_ONCE, it)
        }
        update.verboseLogMode?.let { preferenceDataSource.setBoolean(PrefConst.KEY_VERBOSE_LOG_MODE, it) }
        update.sensitiveDebugLogMode?.let {
            preferenceDataSource.setBoolean(PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE, it)
            SensitiveLogPolicy.setEnabled(it)
        }
        update.runtimeLogRetentionDays?.let {
            preferenceDataSource.setInt(
                PrefConst.KEY_RUNTIME_LOG_RETENTION_DAYS,
                it.coerceAtLeast(PrefConst.RUNTIME_LOG_RETENTION_DAYS_MIN),
            )
        }
        update.autoUpdateOnStart?.let { preferenceDataSource.setBoolean(PrefConst.KEY_AUTO_UPDATE_ON_START, it) }
        update.autoUpdateWifiOnly?.let { preferenceDataSource.setBoolean(PrefConst.KEY_AUTO_UPDATE_WIFI_ONLY, it) }
        update.analyticsEnabled?.let {
            preferenceDataSource.setBoolean(PrefConst.KEY_ENABLE_ANALYTICS, it)
            MagiskOtelBootstrap.install(appContext)
        }
        update.keepAliveOomAdj?.let { preferenceDataSource.setBoolean(PrefConst.KEY_KEEPALIVE_OOM_ADJ, it) }
        update.keepAliveAntiKill?.let { preferenceDataSource.setBoolean(PrefConst.KEY_KEEPALIVE_ANTI_KILL, it) }
        update.keepAliveStandbyBypass?.let { preferenceDataSource.setBoolean(PrefConst.KEY_KEEPALIVE_STANDBY_BYPASS, it) }
        update.keepAliveDozeBypass?.let { preferenceDataSource.setBoolean(PrefConst.KEY_KEEPALIVE_DOZE_BYPASS, it) }
        update.keepAliveAccessibilityHeartbeat?.let { preferenceDataSource.setBoolean(PrefConst.KEY_KEEPALIVE_ACCESSIBILITY_HEARTBEAT, it) }
        update.keepAliveDedicatedService?.let { preferenceDataSource.setBoolean(PrefConst.KEY_KEEPALIVE_DEDICATED_SERVICE, it) }
        publishHookPrefsAndNoteLocalMutation("settings.diagnostics")
        return getDiagnosticsSettings()
    }

    override suspend fun getAdvancedSnapshot(): AdvancedSettingsSnapshot {
        return AdvancedSettingsSnapshot(
            enableSmsBlacklist = preferenceDataSource.getBoolean(PrefConst.KEY_ENABLE_SMS_BLACKLIST, false),
        )
    }

    override suspend fun updateAdvanced(update: AdvancedSettingsUpdate): AdvancedSettingsSnapshot {
        update.enableSmsBlacklist?.let { preferenceDataSource.setBoolean(PrefConst.KEY_ENABLE_SMS_BLACKLIST, it) }
        publishHookPrefsAndNoteLocalMutation("settings.advanced")
        return getAdvancedSnapshot()
    }

    override suspend fun getSpecialAlertSettings(): SpecialAlertSettingsSnapshot {
        return SpecialAlertSettingsSnapshot(
            lowBatteryReminderEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_LOW_BATTERY_REMINDER_ENABLE, false),
            lowBatteryThreshold = preferenceDataSource.getInt(PrefConst.KEY_LOW_BATTERY_THRESHOLD, PrefConst.LOW_BATTERY_THRESHOLD_DEFAULT),
            lowBatteryChannelId = preferenceDataSource.getString(PrefConst.KEY_LOW_BATTERY_CHANNEL_ID, ""),
            fullBatteryReminderEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_FULL_BATTERY_REMINDER_ENABLE, false),
            fullBatteryChannelId = preferenceDataSource.getString(PrefConst.KEY_FULL_BATTERY_CHANNEL_ID, ""),
            chargingChangeReminderEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_CHARGING_CHANGE_REMINDER_ENABLE, false),
            chargingChangeChannelId = preferenceDataSource.getString(PrefConst.KEY_CHARGING_CHANGE_CHANNEL_ID, ""),
            callAlertLocalEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_CALL_ALERT_LOCAL_ENABLED, false),
            callAlertChannelId = preferenceDataSource.getString(PrefConst.KEY_CALL_ALERT_CHANNEL_ID, ""),
            smsKeywordEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_SMS_KEYWORD_ALERT_ENABLED, false),
            smsKeywordKeywords = preferenceDataSource.getString(PrefConst.KEY_SMS_KEYWORD_ALERT_KEYWORDS, ""),
            smsKeywordNotificationEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_SMS_KEYWORD_ALERT_NOTIFICATION, true),
            smsKeywordSoundEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_SMS_KEYWORD_ALERT_SOUND, true),
            smsKeywordVibrateEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_SMS_KEYWORD_ALERT_VIBRATE, true),
            appKeywordEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_APP_KEYWORD_ALERT_ENABLED, false),
            appKeywordKeywords = preferenceDataSource.getString(PrefConst.KEY_APP_KEYWORD_ALERT_KEYWORDS, ""),
            appKeywordNotificationEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_APP_KEYWORD_ALERT_NOTIFICATION, true),
            appKeywordSoundEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_APP_KEYWORD_ALERT_SOUND, true),
            appKeywordVibrateEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_APP_KEYWORD_ALERT_VIBRATE, true),
        )
    }

    override suspend fun updateSpecialAlertSettings(update: SpecialAlertSettingsUpdate): SpecialAlertSettingsSnapshot {
        update.lowBatteryReminderEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_LOW_BATTERY_REMINDER_ENABLE, it) }
        update.lowBatteryThreshold?.let { preferenceDataSource.setInt(PrefConst.KEY_LOW_BATTERY_THRESHOLD, it.coerceIn(1, 100)) }
        update.lowBatteryChannelId?.let { preferenceDataSource.setString(PrefConst.KEY_LOW_BATTERY_CHANNEL_ID, it) }
        update.fullBatteryReminderEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_FULL_BATTERY_REMINDER_ENABLE, it) }
        update.fullBatteryChannelId?.let { preferenceDataSource.setString(PrefConst.KEY_FULL_BATTERY_CHANNEL_ID, it) }
        update.chargingChangeReminderEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_CHARGING_CHANGE_REMINDER_ENABLE, it) }
        update.chargingChangeChannelId?.let { preferenceDataSource.setString(PrefConst.KEY_CHARGING_CHANGE_CHANNEL_ID, it) }
        update.callAlertLocalEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_CALL_ALERT_LOCAL_ENABLED, it) }
        update.callAlertChannelId?.let { preferenceDataSource.setString(PrefConst.KEY_CALL_ALERT_CHANNEL_ID, it) }
        update.smsKeywordEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_SMS_KEYWORD_ALERT_ENABLED, it) }
        update.smsKeywordKeywords?.let { preferenceDataSource.setString(PrefConst.KEY_SMS_KEYWORD_ALERT_KEYWORDS, it) }
        update.smsKeywordNotificationEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_SMS_KEYWORD_ALERT_NOTIFICATION, it) }
        update.smsKeywordSoundEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_SMS_KEYWORD_ALERT_SOUND, it) }
        update.smsKeywordVibrateEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_SMS_KEYWORD_ALERT_VIBRATE, it) }
        update.appKeywordEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_APP_KEYWORD_ALERT_ENABLED, it) }
        update.appKeywordKeywords?.let { preferenceDataSource.setString(PrefConst.KEY_APP_KEYWORD_ALERT_KEYWORDS, it) }
        update.appKeywordNotificationEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_APP_KEYWORD_ALERT_NOTIFICATION, it) }
        update.appKeywordSoundEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_APP_KEYWORD_ALERT_SOUND, it) }
        update.appKeywordVibrateEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_APP_KEYWORD_ALERT_VIBRATE, it) }
        publishHookPrefsAndNoteLocalMutation("settings.special_alerts")
        return getSpecialAlertSettings()
    }

    override suspend fun getMessageTypeGates(): MessageTypeGateSnapshot {
        return MessageTypeGateSnapshot(
            smsCodeEnabled = preferenceDataSource.getBoolean(
                PrefConst.KEY_MSG_TYPE_SMS_CODE_ENABLED,
                defaultMessageTypeEnabled(MessageType.SMS_CODE),
            ),
            smsPlainEnabled = preferenceDataSource.getBoolean(
                PrefConst.KEY_MSG_TYPE_SMS_PLAIN_ENABLED,
                defaultMessageTypeEnabled(MessageType.SMS_PLAIN),
            ),
            appNotifyEnabled = preferenceDataSource.getBoolean(
                PrefConst.KEY_MSG_TYPE_APP_NOTIFY_ENABLED,
                defaultMessageTypeEnabled(MessageType.APP_NOTIFY),
            ),
            callNotifyEnabled = preferenceDataSource.getBoolean(
                PrefConst.KEY_MSG_TYPE_CALL_NOTIFY_ENABLED,
                defaultMessageTypeEnabled(MessageType.CALL_NOTIFY),
            ),
        )
    }

    override suspend fun updateMessageTypeGates(update: MessageTypeGateUpdate): MessageTypeGateSnapshot {
        update.smsCodeEnabled?.let {
            preferenceDataSource.setBoolean(PrefConst.KEY_MSG_TYPE_SMS_CODE_ENABLED, it)
        }
        update.smsPlainEnabled?.let {
            preferenceDataSource.setBoolean(PrefConst.KEY_MSG_TYPE_SMS_PLAIN_ENABLED, it)
        }
        update.appNotifyEnabled?.let {
            preferenceDataSource.setBoolean(PrefConst.KEY_MSG_TYPE_APP_NOTIFY_ENABLED, it)
        }
        update.callNotifyEnabled?.let {
            preferenceDataSource.setBoolean(PrefConst.KEY_MSG_TYPE_CALL_NOTIFY_ENABLED, it)
        }
        publishHookPrefsAndNoteLocalMutation("settings.message_type_gates")
        return getMessageTypeGates()
    }

    override suspend fun getForwardTypeGates(): ForwardTypeGateSnapshot {
        return ForwardTypeGateSnapshot(
            smsCodeEnabled = preferenceDataSource.getBoolean(
                PrefConst.KEY_FORWARD_SMS_CODE_ENABLED,
                defaultMessageTypeEnabled(MessageType.SMS_CODE),
            ),
            smsPlainEnabled = preferenceDataSource.getBoolean(
                PrefConst.KEY_FORWARD_SMS_PLAIN_ENABLED,
                defaultMessageTypeEnabled(MessageType.SMS_PLAIN),
            ),
            appNotifyEnabled = preferenceDataSource.getBoolean(
                PrefConst.KEY_FORWARD_APP_NOTIFY_ENABLED,
                defaultMessageTypeEnabled(MessageType.APP_NOTIFY),
            ),
            callNotifyEnabled = preferenceDataSource.getBoolean(
                PrefConst.KEY_FORWARD_CALL_NOTIFY_ENABLED,
                defaultMessageTypeEnabled(MessageType.CALL_NOTIFY),
            ),
            callNotifyFinalEnabled = preferenceDataSource.getBoolean(
                PrefConst.KEY_FORWARD_CALL_NOTIFY_FINAL_ENABLED,
                false,
            ),
        )
    }

    override suspend fun updateForwardTypeGates(update: ForwardTypeGateUpdate): ForwardTypeGateSnapshot {
        update.smsCodeEnabled?.let {
            preferenceDataSource.setBoolean(PrefConst.KEY_FORWARD_SMS_CODE_ENABLED, it)
        }
        update.smsPlainEnabled?.let {
            preferenceDataSource.setBoolean(PrefConst.KEY_FORWARD_SMS_PLAIN_ENABLED, it)
        }
        update.appNotifyEnabled?.let {
            preferenceDataSource.setBoolean(PrefConst.KEY_FORWARD_APP_NOTIFY_ENABLED, it)
        }
        update.callNotifyEnabled?.let {
            preferenceDataSource.setBoolean(PrefConst.KEY_FORWARD_CALL_NOTIFY_ENABLED, it)
        }
        update.callNotifyFinalEnabled?.let {
            preferenceDataSource.setBoolean(PrefConst.KEY_FORWARD_CALL_NOTIFY_FINAL_ENABLED, it)
        }
        publishHookPrefsAndNoteLocalMutation("settings.forward_type_gates")
        return getForwardTypeGates()
    }

    private fun defaultMessageTypeEnabled(messageType: MessageType): Boolean {
        return when (messageType) {
            MessageType.SMS_CODE -> true
            MessageType.SMS_PLAIN -> true
            MessageType.APP_NOTIFY -> true
            MessageType.CALL_NOTIFY -> false
        }
    }

    override suspend fun clearBatteryReminderRuntimeFlags(
        clearLowBatteryBelow: Boolean,
        clearFullBatteryAbove: Boolean,
        clearChargingState: Boolean,
    ) {
        if (clearLowBatteryBelow) {
            preferenceDataSource.setBoolean(PrefConst.KEY_INTERNAL_LOW_BATTERY_BELOW, false)
        }
        if (clearFullBatteryAbove) {
            preferenceDataSource.setBoolean(PrefConst.KEY_INTERNAL_FULL_BATTERY_ABOVE, false)
        }
        if (clearChargingState) {
            preferenceDataSource.setInt(PrefConst.KEY_INTERNAL_CHARGING_STATE, -1)
        }
        publishHookPrefsOnly()
    }

    override suspend fun getRecordSettings(): RecordSettingsSnapshot {
        val previousLimit = preferenceDataSource.getString(PrefConst.KEY_HISTORY_LIMIT, "0")
        val previousRecordEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_ENABLE_CODE_RECORDS, true)
        return RecordSettingsSnapshot(
            previousRecordEnabled = previousRecordEnabled,
            codeRecordEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_ENABLE_CODE_RECORDS_CODE, previousRecordEnabled),
            plainSmsRecordEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_ENABLE_CODE_RECORDS_PLAIN_SMS, previousRecordEnabled),
            appNotifyRecordEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY, previousRecordEnabled),
            callNotifyRecordEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_ENABLE_CODE_RECORDS_CALL_NOTIFY, previousRecordEnabled),
            codeHistoryLimit = preferenceDataSource.getString(PrefConst.KEY_HISTORY_LIMIT_CODE, previousLimit),
            plainSmsHistoryLimit = preferenceDataSource.getString(PrefConst.KEY_HISTORY_LIMIT_PLAIN_SMS, previousLimit),
            appNotifyHistoryLimit = preferenceDataSource.getString(PrefConst.KEY_HISTORY_LIMIT_APP_NOTIFY, previousLimit),
            callNotifyHistoryLimit = preferenceDataSource.getString(PrefConst.KEY_HISTORY_LIMIT_CALL_NOTIFY, "20"),
            smsBlacklistHitRecordEnabled = preferenceDataSource.getBoolean(
                PrefConst.KEY_ENABLE_SMS_BLACKLIST_HIT_RECORDS,
                true,
            ),
            smsBlacklistHitHistoryLimit = preferenceDataSource.getString(
                PrefConst.KEY_HISTORY_LIMIT_SMS_BLACKLIST_HIT,
                PrefConst.SMS_BLACKLIST_HIT_HISTORY_LIMIT_DEFAULT,
            ),
        )
    }

    override suspend fun updateRecordSettings(update: RecordSettingsUpdate): RecordSettingsSnapshot {
        update.codeRecordEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_ENABLE_CODE_RECORDS_CODE, it) }
        update.plainSmsRecordEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_ENABLE_CODE_RECORDS_PLAIN_SMS, it) }
        update.appNotifyRecordEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY, it) }
        update.callNotifyRecordEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_ENABLE_CODE_RECORDS_CALL_NOTIFY, it) }
        update.codeHistoryLimit?.let { preferenceDataSource.setString(PrefConst.KEY_HISTORY_LIMIT_CODE, it) }
        update.plainSmsHistoryLimit?.let { preferenceDataSource.setString(PrefConst.KEY_HISTORY_LIMIT_PLAIN_SMS, it) }
        update.appNotifyHistoryLimit?.let { preferenceDataSource.setString(PrefConst.KEY_HISTORY_LIMIT_APP_NOTIFY, it) }
        update.callNotifyHistoryLimit?.let { preferenceDataSource.setString(PrefConst.KEY_HISTORY_LIMIT_CALL_NOTIFY, it) }
        update.smsBlacklistHitRecordEnabled?.let {
            preferenceDataSource.setBoolean(PrefConst.KEY_ENABLE_SMS_BLACKLIST_HIT_RECORDS, it)
        }
        update.smsBlacklistHitHistoryLimit?.let {
            preferenceDataSource.setString(PrefConst.KEY_HISTORY_LIMIT_SMS_BLACKLIST_HIT, it)
        }
        publishHookPrefsAndNoteLocalMutation("settings.records")
        return getRecordSettings()
    }

    override suspend fun getSmsBlacklistSettings(): SmsBlacklistSettingsSnapshot {
        return SmsBlacklistSettingsSnapshot(
            enabled = preferenceDataSource.getBoolean(PrefConst.KEY_ENABLE_SMS_BLACKLIST, false),
            deleteBlockedSms = preferenceDataSource.getBoolean(PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE, true),
            blockIncomingSms = preferenceDataSource.getBoolean(PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK, false),
            numbers = preferenceDataSource.getString(PrefConst.KEY_SMS_BLACKLIST_NUMBERS, ""),
            prefixes = preferenceDataSource.getString(PrefConst.KEY_SMS_BLACKLIST_PREFIXES, ""),
            regexRules = preferenceDataSource.getString(PrefConst.KEY_SMS_BLACKLIST_REGEX, ""),
            contentRules = preferenceDataSource.getString(PrefConst.KEY_SMS_BLACKLIST_CONTENT, ""),
        )
    }

    override suspend fun updateSmsBlacklistSettings(update: SmsBlacklistSettingsUpdate): SmsBlacklistSettingsSnapshot {
        update.enabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_ENABLE_SMS_BLACKLIST, it) }
        update.deleteBlockedSms?.let { preferenceDataSource.setBoolean(PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE, it) }
        update.blockIncomingSms?.let { preferenceDataSource.setBoolean(PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK, it) }
        update.numbers?.let { preferenceDataSource.setString(PrefConst.KEY_SMS_BLACKLIST_NUMBERS, it) }
        update.prefixes?.let { preferenceDataSource.setString(PrefConst.KEY_SMS_BLACKLIST_PREFIXES, it) }
        update.regexRules?.let { preferenceDataSource.setString(PrefConst.KEY_SMS_BLACKLIST_REGEX, it) }
        update.contentRules?.let { preferenceDataSource.setString(PrefConst.KEY_SMS_BLACKLIST_CONTENT, it) }
        publishHookPrefsAndNoteLocalMutation("settings.sms_blacklist")
        return getSmsBlacklistSettings()
    }

    override suspend fun getSimRemarkSettings(): SimRemarkSettingsSnapshot {
        return SimRemarkSettingsSnapshot(
            simSlot1Remark = preferenceDataSource.getString(PrefConst.KEY_SIM_SLOT1_REMARK, ""),
            simSlot2Remark = preferenceDataSource.getString(PrefConst.KEY_SIM_SLOT2_REMARK, ""),
        )
    }

    override suspend fun updateSimRemarkSettings(update: SimRemarkSettingsUpdate): SimRemarkSettingsSnapshot {
        update.simSlot1Remark?.let { preferenceDataSource.setString(PrefConst.KEY_SIM_SLOT1_REMARK, it) }
        update.simSlot2Remark?.let { preferenceDataSource.setString(PrefConst.KEY_SIM_SLOT2_REMARK, it) }
        publishHookPrefsAndNoteLocalMutation("settings.sim_remarks")
        return getSimRemarkSettings()
    }

    override suspend fun loadForwardCommonConfig(): ForwardCommonConfig {
        val defaultDeviceName = DeviceIdentityUtils.resolveDefaultDeviceName()
        val configuredName = preferenceDataSource.getString(
            PrefConst.KEY_FORWARD_COMMON_DEVICE_NAME,
            defaultDeviceName,
        ).trim()
        val configuredTemplate = preferenceDataSource.getString(
            PrefConst.KEY_FORWARD_COMMON_TEMPLATE,
            "",
        )
        val includeTime = preferenceDataSource.getBoolean(
            PrefConst.KEY_FORWARD_COMMON_INCLUDE_TIME,
            false,
        )
        val includeSender = preferenceDataSource.getBoolean(
            PrefConst.KEY_FORWARD_COMMON_INCLUDE_SENDER,
            false,
        )
        val includeDeviceName = preferenceDataSource.getBoolean(
            PrefConst.KEY_FORWARD_COMMON_INCLUDE_DEVICE_NAME,
            true,
        )
        val dispatchStrategy = preferenceDataSource.getInt(
            PrefConst.KEY_FORWARD_COMMON_DISPATCH_STRATEGY,
            DispatchStrategy.BROADCAST_ALL,
        )
        val silentPeriod = ForwardSilentPeriodEvaluator.sanitize(
            ForwardSilentPeriodConfig(
                enabled = preferenceDataSource.getBoolean(
                    PrefConst.KEY_FORWARD_SILENT_PERIOD_ENABLED,
                    false,
                ),
                start = preferenceDataSource.getString(
                    PrefConst.KEY_FORWARD_SILENT_PERIOD_START,
                    ForwardSilentPeriodConfig.DEFAULT_START,
                ),
                end = preferenceDataSource.getString(
                    PrefConst.KEY_FORWARD_SILENT_PERIOD_END,
                    ForwardSilentPeriodConfig.DEFAULT_END,
                ),
                weekdays = decodeSilentPeriodWeekdays(
                    preferenceDataSource.getString(
                        PrefConst.KEY_FORWARD_SILENT_PERIOD_WEEKDAYS,
                        encodeSilentPeriodWeekdays(ForwardSilentPeriodConfig.ALL_WEEKDAYS),
                    ),
                ),
            ),
        )
        return ForwardCommonConfig(
            deviceName = configuredName.ifBlank { defaultDeviceName },
            messageTemplate = configuredTemplate,
            includeTime = includeTime,
            includeSender = includeSender,
            includeDeviceName = includeDeviceName,
            dispatchStrategy = normalizeDispatchStrategy(dispatchStrategy),
            silentPeriod = silentPeriod,
        )
    }

    override suspend fun saveForwardCommonConfig(config: ForwardCommonConfig) {
        val silentPeriod = ForwardSilentPeriodEvaluator.sanitize(config.silentPeriod)
        preferenceDataSource.setString(
            PrefConst.KEY_FORWARD_COMMON_DEVICE_NAME,
            config.deviceName.trim(),
        )
        preferenceDataSource.setString(
            PrefConst.KEY_FORWARD_COMMON_TEMPLATE,
            config.messageTemplate,
        )
        preferenceDataSource.setBoolean(
            PrefConst.KEY_FORWARD_COMMON_INCLUDE_TIME,
            config.includeTime,
        )
        preferenceDataSource.setBoolean(
            PrefConst.KEY_FORWARD_COMMON_INCLUDE_SENDER,
            config.includeSender,
        )
        preferenceDataSource.setBoolean(
            PrefConst.KEY_FORWARD_COMMON_INCLUDE_DEVICE_NAME,
            config.includeDeviceName,
        )
        preferenceDataSource.setInt(
            PrefConst.KEY_FORWARD_COMMON_DISPATCH_STRATEGY,
            normalizeDispatchStrategy(config.dispatchStrategy),
        )
        preferenceDataSource.setBoolean(
            PrefConst.KEY_FORWARD_SILENT_PERIOD_ENABLED,
            silentPeriod.enabled,
        )
        preferenceDataSource.setString(
            PrefConst.KEY_FORWARD_SILENT_PERIOD_START,
            silentPeriod.start,
        )
        preferenceDataSource.setString(
            PrefConst.KEY_FORWARD_SILENT_PERIOD_END,
            silentPeriod.end,
        )
        preferenceDataSource.setString(
            PrefConst.KEY_FORWARD_SILENT_PERIOD_WEEKDAYS,
            encodeSilentPeriodWeekdays(silentPeriod.weekdays),
        )
        publishHookPrefsAndNoteLocalMutation("settings.forward_common")
    }

    private fun normalizeDispatchStrategy(strategy: Int): Int {
        return when (strategy) {
            DispatchStrategy.PRIMARY_ONLY,
            DispatchStrategy.BROADCAST_ALL,
            DispatchStrategy.FAILOVER,
            -> strategy
            else -> DispatchStrategy.BROADCAST_ALL
        }
    }

    private fun decodeSilentPeriodWeekdays(rawValue: String): List<Int> {
        return rawValue
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in ForwardSilentPeriodConfig.ALL_WEEKDAYS }
            .distinct()
            .sorted()
            .ifEmpty { ForwardSilentPeriodConfig.ALL_WEEKDAYS }
    }

    private fun encodeSilentPeriodWeekdays(weekdays: List<Int>): String {
        return weekdays
            .filter { it in ForwardSilentPeriodConfig.ALL_WEEKDAYS }
            .distinct()
            .sorted()
            .ifEmpty { ForwardSilentPeriodConfig.ALL_WEEKDAYS }
            .joinToString(",")
    }

    override suspend fun loadAppNotifyTemplate(): String {
        return preferenceDataSource.getString(PrefConst.KEY_FORWARD_APP_NOTIFY_TEMPLATE, "")
    }

    override suspend fun saveAppNotifyTemplate(template: String) {
        preferenceDataSource.setString(PrefConst.KEY_FORWARD_APP_NOTIFY_TEMPLATE, template)
        publishHookPrefsAndNoteLocalMutation("settings.app_notify_template")
    }

    override suspend fun loadCallNotifyTemplate(): String {
        return preferenceDataSource.getString(PrefConst.KEY_FORWARD_CALL_NOTIFY_TEMPLATE, "")
    }

    override suspend fun saveCallNotifyTemplate(template: String) {
        preferenceDataSource.setString(PrefConst.KEY_FORWARD_CALL_NOTIFY_TEMPLATE, template)
        publishHookPrefsAndNoteLocalMutation("settings.call_notify_template")
    }

    override suspend fun getUserSettingsSnapshot(): UserSettingsSnapshot {
        val general = getGeneralSettings()
        val verification = getVerificationSettings()
        val relay = getRelaySettings()
        val diagnostics = getDiagnosticsSettings()
        val advanced = getAdvancedSnapshot()
        return UserSettingsSnapshot(
            moduleEnabled = general.moduleEnabled,
            verificationFeaturesEnabled = verification.verificationFeaturesEnabled,
            relayFeaturesEnabled = relay.relayFeaturesEnabled,
            copyToClipboard = verification.copyToClipboard,
            showToast = verification.showToast,
            showCodeNotification = verification.showCodeNotification,
            blockSmsEnabled = verification.blockSmsEnabled,
            enableAutoInputCode = verification.autoInputEnabled,
            enableAutoEnterCode = verification.autoEnterEnabled,
            verboseLogMode = diagnostics.verboseLogMode,
            smsBlacklistEnabled = advanced.enableSmsBlacklist,
            forceStopRecoveryEnabled = diagnostics.forceStopRecoveryEnabled,
        )
    }

    override suspend fun updateUserSettings(update: UserSettingsUpdate): UserSettingsSnapshot {
        update.moduleEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_ENABLE, it) }
        update.verificationFeaturesEnabled?.let {
            preferenceDataSource.setBoolean(PrefConst.KEY_VERIFICATION_FEATURES_ENABLED, it)
        }
        update.relayFeaturesEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_RELAY_FEATURES_ENABLED, it) }
        update.copyToClipboard?.let { preferenceDataSource.setBoolean(PrefConst.KEY_COPY_TO_CLIPBOARD, it) }
        update.showToast?.let { preferenceDataSource.setBoolean(PrefConst.KEY_SHOW_TOAST, it) }
        update.showCodeNotification?.let { preferenceDataSource.setBoolean(PrefConst.KEY_SHOW_CODE_NOTIFICATION, it) }
        update.blockSmsEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_BLOCK_SMS, it) }
        update.enableAutoInputCode?.let { preferenceDataSource.setBoolean(PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, it) }
        update.enableAutoEnterCode?.let { preferenceDataSource.setBoolean(PrefConst.KEY_ENABLE_AUTO_ENTER_CODE, it) }
        update.verboseLogMode?.let { preferenceDataSource.setBoolean(PrefConst.KEY_VERBOSE_LOG_MODE, it) }
        update.smsBlacklistEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_ENABLE_SMS_BLACKLIST, it) }
        update.forceStopRecoveryEnabled?.let { preferenceDataSource.setBoolean(PrefConst.KEY_FORCE_STOP_RECOVERY, it) }
        publishHookPrefsAndNoteLocalMutation("settings.user_settings")
        return getUserSettingsSnapshot()
    }

    override suspend fun getOverviewSettings(): OverviewSettingsSnapshot {
        return OverviewSettingsSnapshot(
            cardOrder = preferenceDataSource.getString(PrefConst.KEY_HOME_CARD_ORDER, ""),
            enabledCardIds = preferenceDataSource.getString(PrefConst.KEY_HOME_CARD_ENABLED, ""),
            chartType = preferenceDataSource.getString(PrefConst.KEY_HOME_CHART_TYPE, ""),
            chartWindow = preferenceDataSource.getString(PrefConst.KEY_HOME_CHART_WINDOW, ""),
        )
    }

    override suspend fun updateOverviewSettings(update: OverviewSettingsUpdate): OverviewSettingsSnapshot {
        update.cardOrder?.let { preferenceDataSource.setString(PrefConst.KEY_HOME_CARD_ORDER, it) }
        update.enabledCardIds?.let { preferenceDataSource.setString(PrefConst.KEY_HOME_CARD_ENABLED, it) }
        update.chartType?.let { preferenceDataSource.setString(PrefConst.KEY_HOME_CHART_TYPE, it) }
        update.chartWindow?.let { preferenceDataSource.setString(PrefConst.KEY_HOME_CHART_WINDOW, it) }
        publishHookPrefsAndNoteLocalMutation("settings.overview")
        return getOverviewSettings()
    }

    override suspend fun getAutoUpdateSettings(): AutoUpdateSettingsSnapshot {
        return AutoUpdateSettingsSnapshot(
            enabled = preferenceDataSource.getBoolean(PrefConst.KEY_AUTO_UPDATE_ON_START, true),
            wifiOnly = preferenceDataSource.getBoolean(PrefConst.KEY_AUTO_UPDATE_WIFI_ONLY, false),
            ignoredGithubVersion = preferenceDataSource.getString(PrefConst.KEY_GITHUB_IGNORED_VERSION, ""),
        )
    }

    override suspend fun setIgnoredGithubVersion(versionName: String) {
        preferenceDataSource.setString(PrefConst.KEY_GITHUB_IGNORED_VERSION, versionName)
        publishHookPrefsOnly()
    }

    override suspend fun getThemeMode(): Int {
        return preferenceDataSource.getInt(PrefConst.KEY_CHOOSE_THEME, 0)
    }

    override suspend fun setThemeMode(mode: Int) {
        preferenceDataSource.setInt(PrefConst.KEY_CHOOSE_THEME, mode)
        publishHookPrefsAndScheduleAutoBackup("settings.theme")
    }

    override suspend fun getUiKitStyle(): Int {
        return UI_KIT_STYLE_EXPRESSIVE
    }

    override suspend fun setUiKitStyle(style: Int) {
        preferenceDataSource.setInt(PrefConst.KEY_UI_KIT_STYLE, UI_KIT_STYLE_EXPRESSIVE)
        publishHookPrefsAndScheduleAutoBackup("settings.ui_kit_style")
    }

    override suspend fun getLanguageTag(): String {
        return preferenceDataSource.getString(PrefConst.KEY_LANGUAGE, "")
    }

    override suspend fun setLanguageTag(languageTag: String) {
        preferenceDataSource.setString(PrefConst.KEY_LANGUAGE, languageTag)
        publishHookPrefsAndScheduleAutoBackup("settings.language")
    }

    override suspend fun isPrivacyPolicyAccepted(): Boolean {
        return preferenceDataSource.getBoolean(PrefConst.KEY_PRIVACY_POLICY_ACCEPTED, false)
    }

    override suspend fun setPrivacyPolicyAccepted(accepted: Boolean) {
        preferenceDataSource.setBoolean(PrefConst.KEY_PRIVACY_POLICY_ACCEPTED, accepted)
        publishHookPrefsOnly()
    }

    private suspend fun publishHookPrefsAndNoteLocalMutation(source: String) {
        HookPreferenceMirror.publish(appContext)
        noteLocalMutationAndScheduleAutoBackup(source)
    }

    private suspend fun noteLocalMutationAndScheduleAutoBackup(source: String) {
        runCatching { RuntimeDependencies.get().localConfigRepository.noteLocalMutation(source) }
            .onFailure { XLog.e("noteLocalMutation failed: %s", it.message ?: it.javaClass.simpleName) }
        runCatching { RuntimeDependencies.get().autoBackupTrigger.scheduleAutoBackup(source) }
            .onFailure { XLog.e("scheduleAutoBackup failed: %s", it.message ?: it.javaClass.simpleName) }
    }

    private suspend fun handleVerificationCommitResult(result: PreferenceCommitResult) {
        when (result) {
            is PreferenceCommitResult.Persisted -> {
                result.postCommitFailures.forEach { failure ->
                    XLog.e(
                        "Verification preference post-commit hook failed: hook=%s error=%s",
                        failure.hookName,
                        failure.error.message ?: failure.error.javaClass.simpleName,
                    )
                }
                noteLocalMutationAndScheduleAutoBackup("settings.verification")
            }
            is PreferenceCommitResult.NoChanges -> Unit
            is PreferenceCommitResult.NotPersisted -> {
                throw IllegalStateException("Verification preferences were not persisted", result.error)
            }
        }
    }

    private suspend fun publishHookPrefsAndScheduleAutoBackup(source: String) {
        HookPreferenceMirror.publish(appContext)
        runCatching { RuntimeDependencies.get().autoBackupTrigger.scheduleAutoBackup(source) }
            .onFailure { XLog.e("scheduleAutoBackup failed: %s", it.message ?: it.javaClass.simpleName) }
    }

    private suspend fun publishHookPrefsOnly() {
        HookPreferenceMirror.publish(appContext)
    }

    private suspend fun loadAutoInputDelayMs(): String {
        val milliseconds = preferenceDataSource.getString(PrefConst.KEY_AUTO_INPUT_CODE_DELAY, "")
        if (milliseconds.isNotEmpty()) return milliseconds

        val legacySeconds = preferenceDataSource.getString(
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY_LEGACY,
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT,
        ).toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        return (legacySeconds * 1000L).toString()
    }
}

private object VerificationPreferenceSpecs {
    val featuresEnabled = PreferenceSpec.boolean(PrefConst.KEY_VERIFICATION_FEATURES_ENABLED, true)
    val copyToClipboard = PreferenceSpec.boolean(PrefConst.KEY_COPY_TO_CLIPBOARD, false)
    val showToast = PreferenceSpec.boolean(PrefConst.KEY_SHOW_TOAST, true)
    val showCodeNotification = PreferenceSpec.boolean(PrefConst.KEY_SHOW_CODE_NOTIFICATION, true)
    val autoCancelNotification = PreferenceSpec.boolean(PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION, false)
    val notificationRetentionTime = PreferenceSpec.string(
        PrefConst.KEY_NOTIFICATION_RETENTION_TIME,
        PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT,
    )
    val relayKeywords = PreferenceSpec.string(PrefConst.KEY_SMSCODE_KEYWORDS, SmsCodeConst.VERIFICATION_KEYWORDS_REGEX)
    val blockSms = PreferenceSpec.boolean(PrefConst.KEY_BLOCK_SMS, false)
}
