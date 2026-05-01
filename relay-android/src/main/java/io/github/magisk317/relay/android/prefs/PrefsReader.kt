package io.github.magisk317.relay.android.prefs

import android.content.Context
import io.github.magisk317.relay.contract.constant.MessageType
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.contract.prefs.NoopXpRuntimeBridge
import io.github.magisk317.relay.contract.prefs.XpCapabilities
import io.github.magisk317.relay.contract.prefs.PrefReadResult
import io.github.magisk317.relay.contract.prefs.PrefsSource
import io.github.magisk317.relay.contract.prefs.XpRuntimeBridge
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import io.github.magisk317.relay.android.BuildConfig
import io.github.magisk317.smscode.domain.constant.SmsCodeConst
import java.util.concurrent.atomic.AtomicBoolean

// Phase3 complete: PrefsReader is runtime/Xposed/跨进程只读 only.
// Source-chain resolution lives in PrefsSourceChain; do not add more business getters here.
object PrefsReader {
    internal const val PREFS_NAME = "xposed_prefs"
    private data class BooleanReadTrace(val value: Boolean, val source: String)
    private data class StringReadTrace(val value: String, val source: String)
    private val runtimeBridgeLogOnce = AtomicBoolean(false)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, CachedResult<*>>()
    private const val CACHE_TTL_MS = 10_000L

    private data class CachedResult<T>(
        val value: T,
        val source: String,
        val timestamp: Long = System.currentTimeMillis(),
    )

    @Volatile
    private var runtimeBridge: XpRuntimeBridge = NoopXpRuntimeBridge

    @JvmStatic
    fun installRuntimeBridge(bridge: XpRuntimeBridge?) {
        // Runtime bridge installation stays here to keep hook-side initialization centralized.
        runtimeBridge = bridge ?: NoopXpRuntimeBridge
        runtimeBridgeLogOnce.set(false)
        logRuntimeBridgeOnce()
    }

    private fun logRuntimeBridgeOnce() {
        if (!BuildConfig.DEBUG || !runtimeBridgeLogOnce.compareAndSet(false, true)) {
            return
        }
        val capabilities = runtimeBridge.capabilities()
        safeInfo(
            "PrefsReader runtime bridge: framework=%s version=%s api=%s privilege=%s " +
                "properties=%s propRemote=%s remotePrefs=%s remoteFile=%s deopt=%s chain=%s",
            capabilities.frameworkName,
            capabilities.frameworkVersion,
            capabilities.frameworkApiVersion?.toString() ?: "unknown",
            capabilities.frameworkPrivilege?.toString() ?: "unknown",
            capabilities.frameworkProperties?.toString() ?: "unknown",
            capabilities.hasFrameworkProperty(XpCapabilities.PROP_CAP_REMOTE),
            capabilities.supportsRemotePrefs,
            capabilities.supportsRemoteFile,
            capabilities.supportsDeopt,
            "remote->default",
        )
    }

    private fun resolveSources(): List<PrefsSource> {
        return PrefsSourceChain.resolveSources(
            runtimeBridge = runtimeBridge,
            warn = ::safeWarn,
        )
    }

    private fun resolveBoolean(
        context: Context,
        key: String,
        defaultValue: Boolean,
        sources: List<PrefsSource> = resolveSources(),
    ): PrefReadResult<Boolean> {
        val cached = getFromCache<Boolean>(key)
        if (cached != null) return cached

        val result = PrefsSourceChain.resolveBoolean(
            context = context,
            key = key,
            defaultValue = defaultValue,
            sources = sources,
            logRuntimeBridgeOnce = ::logRuntimeBridgeOnce,
            warn = ::safeWarn,
        )
        putToCache(key, result)
        return result
    }

    private fun resolveString(
        context: Context,
        key: String,
        defaultValue: String,
        sources: List<PrefsSource> = resolveSources(),
    ): PrefReadResult<String> {
        val cached = getFromCache<String>(key)
        if (cached != null) return cached

        val result = PrefsSourceChain.resolveString(
            context = context,
            key = key,
            defaultValue = defaultValue,
            sources = sources,
            logRuntimeBridgeOnce = ::logRuntimeBridgeOnce,
            warn = ::safeWarn,
        )
        putToCache(key, result)
        return result
    }

    private fun resolveInt(
        context: Context,
        key: String,
        defaultValue: Int,
        sources: List<PrefsSource> = resolveSources(),
    ): PrefReadResult<Int> {
        val cached = getFromCache<Int>(key)
        if (cached != null) return cached

        val result = PrefsSourceChain.resolveInt(
            context = context,
            key = key,
            defaultValue = defaultValue,
            sources = sources,
            logRuntimeBridgeOnce = ::logRuntimeBridgeOnce,
            warn = ::safeWarn,
        )
        putToCache(key, result)
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getFromCache(key: String): PrefReadResult<T>? {
        val cached = cache[key] as? CachedResult<T> ?: return null
        if (System.currentTimeMillis() - cached.timestamp > CACHE_TTL_MS) {
            cache.remove(key)
            return null
        }
        return PrefReadResult(cached.value, cached.source)
    }

    private fun <T> putToCache(key: String, result: PrefReadResult<T>) {
        cache[key] = CachedResult(result.value, result.source)
    }

    private fun safeWarn(message: String, vararg args: Any?) {
        runCatching { XLog.w(message, *args) }
    }

    private fun safeWarn(message: String, error: Throwable?) {
        runCatching { XLog.w(message, error) }
    }

    private fun safeInfo(message: String, vararg args: Any?) {
        runCatching { XLog.i(message, *args) }
    }

    private fun getBooleanViaProvider(context: Context, key: String, defaultValue: Boolean): Boolean {
        return resolveBoolean(context, key, defaultValue).value
    }

    private fun readBooleanWithTrace(context: Context, key: String, defaultValue: Boolean): BooleanReadTrace {
        val result = resolveBoolean(context, key, defaultValue)
        return BooleanReadTrace(result.value, result.source)
    }

    private fun getStringViaProvider(context: Context, key: String, defaultValue: String): String {
        return resolveString(context, key, defaultValue).value
    }

    private fun readStringWithTrace(context: Context, key: String, defaultValue: String): StringReadTrace {
        val result = resolveString(context, key, defaultValue)
        return StringReadTrace(result.value, result.source)
    }

    private fun getIntViaProvider(context: Context, key: String, defaultValue: Int): Int {
        return resolveInt(context, key, defaultValue).value
    }

    fun resolveBooleanWithSourcesForTest(
        context: Context,
        key: String,
        defaultValue: Boolean,
        sources: List<PrefsSource>,
    ): PrefReadResult<Boolean> = resolveBoolean(context, key, defaultValue, sources)

    fun resolveStringWithSourcesForTest(
        context: Context,
        key: String,
        defaultValue: String,
        sources: List<PrefsSource>,
    ): PrefReadResult<String> = resolveString(context, key, defaultValue, sources)

    fun resolveIntWithSourcesForTest(
        context: Context,
        key: String,
        defaultValue: Int,
        sources: List<PrefsSource>,
    ): PrefReadResult<Int> = resolveInt(context, key, defaultValue, sources)

    @JvmStatic
    fun isEnabled(context: Context): Boolean {
        val defaultValue = true
        return getBooleanViaProvider(context, PrefConst.KEY_ENABLE, defaultValue)
    }

    @JvmStatic
    fun isVerboseLogMode(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(context, PrefConst.KEY_VERBOSE_LOG_MODE, defaultValue)
    }

    @JvmStatic
    fun isSensitiveDebugLogSupported(): Boolean = BuildConfig.DEBUG

    @JvmStatic
    fun isSensitiveDebugLogMode(context: Context): Boolean {
        if (!isSensitiveDebugLogSupported()) return false
        return getBooleanViaProvider(context, PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE, false)
    }

    @JvmStatic
    fun verificationFeaturesEnabled(context: Context): Boolean {
        return getBooleanViaProvider(context, PrefConst.KEY_VERIFICATION_FEATURES_ENABLED, true)
    }

    @JvmStatic
    fun relayFeaturesEnabled(context: Context): Boolean {
        return getBooleanViaProvider(context, PrefConst.KEY_RELAY_FEATURES_ENABLED, true)
    }

    @JvmStatic
    fun autoInputCodeEnabled(context: Context): Boolean {
        if (!verificationFeaturesEnabled(context)) return false
        val defaultValue = true
        return getBooleanViaProvider(context, PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, defaultValue)
    }

    @JvmStatic
    fun autoEnterCodeEnabled(context: Context): Boolean {
        if (!verificationFeaturesEnabled(context)) return false
        val defaultValue = false
        return getBooleanViaProvider(context, PrefConst.KEY_ENABLE_AUTO_ENTER_CODE, defaultValue)
    }

    @JvmStatic
    fun getAutoInputCodeDelay(context: Context): Long {
        val value = getStringViaProvider(
            context,
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY,
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT,
        )
        return try {
            value.toLong()
        } catch (ignored: Exception) {
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT.toLong()
        }
    }

    @JvmStatic
    fun getAutoInputCodeIntervalMs(context: Context): Long {
        val value = getStringViaProvider(
            context,
            PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL,
            PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT,
        )
        return try {
            value.toLong().coerceAtLeast(0L)
        } catch (ignored: Exception) {
            PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT.toLong()
        }
    }

    @JvmStatic
    fun shouldShowToast(context: Context): Boolean {
        if (!verificationFeaturesEnabled(context)) return false
        val defaultValue = true
        return getBooleanViaProvider(context, PrefConst.KEY_SHOW_TOAST, defaultValue)
    }

    @JvmStatic
    fun getSMSCodeKeywords(context: Context): String? {
        val primary = readStringWithTrace(
            context,
            PrefConst.KEY_SMSCODE_KEYWORDS,
            SmsCodeConst.VERIFICATION_KEYWORDS_REGEX,
        )
        if (primary.source != "default") {
            return primary.value
        }
        return getStringViaProvider(
            context,
            PrefConst.KEY_RELAY_KEYWORDS,
            SmsCodeConst.VERIFICATION_KEYWORDS_REGEX,
        )
    }

    @JvmStatic
    fun markAsReadEnabled(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(context, PrefConst.KEY_MARK_AS_READ, defaultValue)
    }

    @JvmStatic
    fun deleteSmsEnabled(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(context, PrefConst.KEY_DELETE_SMS, defaultValue)
    }

    @JvmStatic
    fun copyToClipboardEnabled(context: Context): Boolean {
        if (!verificationFeaturesEnabled(context)) return false
        val defaultValue = false
        val trace = readBooleanWithTrace(context, PrefConst.KEY_COPY_TO_CLIPBOARD, defaultValue)
        XLog.w(
            "Diag pref copy_to_clipboard: value=%s source=%s default=%s",
            trace.value,
            trace.source,
            defaultValue,
        )
        return trace.value
    }

    @JvmStatic
    fun analyticsEnabled(context: Context): Boolean {
        val defaultValue = true
        return getBooleanViaProvider(context, PrefConst.KEY_ENABLE_ANALYTICS, defaultValue)
    }

    @JvmStatic
    fun lowBatteryReminderEnabled(context: Context): Boolean {
        return getBooleanViaProvider(context, PrefConst.KEY_LOW_BATTERY_REMINDER_ENABLE, false)
    }

    @JvmStatic
    fun lowBatteryThreshold(context: Context): Int {
        return getIntViaProvider(context, PrefConst.KEY_LOW_BATTERY_THRESHOLD, PrefConst.LOW_BATTERY_THRESHOLD_DEFAULT)
            .coerceIn(1, 100)
    }

    @JvmStatic
    fun lowBatteryChannelId(context: Context): String {
        return getStringViaProvider(
            context,
            PrefConst.KEY_LOW_BATTERY_CHANNEL_ID,
            "",
        )
    }

    @JvmStatic
    fun fullBatteryReminderEnabled(context: Context): Boolean {
        return getBooleanViaProvider(context, PrefConst.KEY_FULL_BATTERY_REMINDER_ENABLE, false)
    }

    @JvmStatic
    fun fullBatteryChannelId(context: Context): String {
        return getStringViaProvider(
            context,
            PrefConst.KEY_FULL_BATTERY_CHANNEL_ID,
            "",
        )
    }

    @JvmStatic
    fun callAlertLocalEnabled(context: Context): Boolean {
        return getBooleanViaProvider(context, PrefConst.KEY_CALL_ALERT_LOCAL_ENABLED, false)
    }

    @JvmStatic
    fun callAlertForwardEnabled(context: Context): Boolean {
        return getBooleanViaProvider(context, PrefConst.KEY_CALL_ALERT_FORWARD_ENABLED, false)
    }

    @JvmStatic
    fun callAlertChannelId(context: Context): String {
        return getStringViaProvider(
            context,
            PrefConst.KEY_CALL_ALERT_CHANNEL_ID,
            "",
        )
    }

    @JvmStatic
    fun smsKeywordAlertEnabled(context: Context): Boolean {
        return getBooleanViaProvider(context, PrefConst.KEY_SMS_KEYWORD_ALERT_ENABLED, false)
    }

    @JvmStatic
    fun smsKeywordAlertKeywords(context: Context): String {
        return getStringViaProvider(context, PrefConst.KEY_SMS_KEYWORD_ALERT_KEYWORDS, "")
    }

    @JvmStatic
    fun smsKeywordAlertNotificationEnabled(context: Context): Boolean {
        return getBooleanViaProvider(context, PrefConst.KEY_SMS_KEYWORD_ALERT_NOTIFICATION, true)
    }

    @JvmStatic
    fun smsKeywordAlertSoundEnabled(context: Context): Boolean {
        return getBooleanViaProvider(context, PrefConst.KEY_SMS_KEYWORD_ALERT_SOUND, true)
    }

    @JvmStatic
    fun smsKeywordAlertVibrateEnabled(context: Context): Boolean {
        return getBooleanViaProvider(context, PrefConst.KEY_SMS_KEYWORD_ALERT_VIBRATE, true)
    }

    @JvmStatic
    fun appKeywordAlertEnabled(context: Context): Boolean {
        return getBooleanViaProvider(context, PrefConst.KEY_APP_KEYWORD_ALERT_ENABLED, false)
    }

    @JvmStatic
    fun appKeywordAlertKeywords(context: Context): String {
        return getStringViaProvider(context, PrefConst.KEY_APP_KEYWORD_ALERT_KEYWORDS, "")
    }

    @JvmStatic
    fun appKeywordAlertNotificationEnabled(context: Context): Boolean {
        return getBooleanViaProvider(context, PrefConst.KEY_APP_KEYWORD_ALERT_NOTIFICATION, true)
    }

    @JvmStatic
    fun appKeywordAlertSoundEnabled(context: Context): Boolean {
        return getBooleanViaProvider(context, PrefConst.KEY_APP_KEYWORD_ALERT_SOUND, true)
    }

    @JvmStatic
    fun appKeywordAlertVibrateEnabled(context: Context): Boolean {
        return getBooleanViaProvider(context, PrefConst.KEY_APP_KEYWORD_ALERT_VIBRATE, true)
    }

    @JvmStatic
    fun isMessageTypeEnabled(context: Context, messageType: MessageType): Boolean {
        val defaultValue = defaultMessageTypeEnabled(messageType)
        val result = resolveBoolean(context, messageType.prefKey, defaultValue)
        if (messageType == MessageType.CALL_NOTIFY && result.source == "default") {
            return callAlertForwardEnabled(context)
        }
        return result.value
    }

    @JvmStatic
    fun defaultMessageTypeEnabled(messageType: MessageType): Boolean {
        return when (messageType) {
            MessageType.SMS_CODE -> true
            MessageType.SMS_PLAIN -> true
            MessageType.APP_NOTIFY -> true
            MessageType.CALL_NOTIFY -> false
        }
    }

    @JvmStatic
    fun isMessageTypeRecordEnabled(context: Context, messageType: MessageType): Boolean {
        return when (messageType) {
            MessageType.SMS_CODE -> recordCodeSmsEnabled(context)
            MessageType.SMS_PLAIN -> recordPlainSmsEnabled(context)
            MessageType.APP_NOTIFY -> recordAppNotifyEnabled(context)
            MessageType.CALL_NOTIFY -> recordCallNotifyEnabled(context)
        }
    }

    @JvmStatic
    fun isMessageTypeSpecialAlertEnabled(context: Context, messageType: MessageType): Boolean {
        return when (messageType) {
            MessageType.SMS_CODE,
            MessageType.SMS_PLAIN,
            -> smsKeywordAlertEnabled(context)

            MessageType.APP_NOTIFY -> appKeywordAlertEnabled(context)
            MessageType.CALL_NOTIFY -> callAlertLocalEnabled(context)
        }
    }

    @JvmStatic
    fun recordSmsCodeEnabled(context: Context): Boolean {
        return recordCodeSmsEnabled(context)
    }

    @JvmStatic
    fun recordCodeSmsEnabled(context: Context): Boolean {
        val previousDefault = getBooleanViaProvider(context, PrefConst.KEY_ENABLE_CODE_RECORDS, true)
        return getBooleanViaProvider(context, PrefConst.KEY_ENABLE_CODE_RECORDS_CODE, previousDefault)
    }

    @JvmStatic
    fun recordPlainSmsEnabled(context: Context): Boolean {
        val previousDefault = getBooleanViaProvider(context, PrefConst.KEY_ENABLE_CODE_RECORDS, true)
        return getBooleanViaProvider(context, PrefConst.KEY_ENABLE_CODE_RECORDS_PLAIN_SMS, previousDefault)
    }

    @JvmStatic
    fun recordAppNotifyEnabled(context: Context): Boolean {
        val previousDefault = getBooleanViaProvider(context, PrefConst.KEY_ENABLE_CODE_RECORDS, true)
        return getBooleanViaProvider(context, PrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY, previousDefault)
    }

    @JvmStatic
    fun recordCallNotifyEnabled(context: Context): Boolean {
        val previousDefault = getBooleanViaProvider(context, PrefConst.KEY_ENABLE_CODE_RECORDS, true)
        return getBooleanViaProvider(context, PrefConst.KEY_ENABLE_CODE_RECORDS_CALL_NOTIFY, previousDefault)
    }

    @JvmStatic
    fun blockSmsEnabled(context: Context): Boolean {
        val defaultValue = false
        return resolveBoolean(context, PrefConst.KEY_BLOCK_SMS, defaultValue).value
    }

    @JvmStatic
    fun forceStopRecoveryEnabled(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(context, PrefConst.KEY_FORCE_STOP_RECOVERY, defaultValue)
    }

    @JvmStatic
    fun rootDbCatchupEnabled(context: Context): Boolean {
        return getBooleanViaProvider(context, PrefConst.KEY_ROOT_DB_CATCHUP_ENABLE, false)
    }

    @JvmStatic
    fun rootDbCatchupIntervalMin(context: Context): Long {
        val value = getStringViaProvider(context, PrefConst.KEY_ROOT_DB_CATCHUP_INTERVAL_MIN, "5")
        return value.toLongOrNull()?.coerceAtLeast(1L) ?: 5L
    }

    @JvmStatic
    fun rootDbCatchupWriteback(context: Context): Boolean {
        return getBooleanViaProvider(context, PrefConst.KEY_ROOT_DB_CATCHUP_WRITEBACK, false)
    }

    @JvmStatic
    fun showCodeNotification(context: Context): Boolean {
        if (!verificationFeaturesEnabled(context)) return false
        val defaultValue = true
        return getBooleanViaProvider(context, PrefConst.KEY_SHOW_CODE_NOTIFICATION, defaultValue)
    }

    @JvmStatic
    fun getCodeNotificationOwner(context: Context): String {
        val value = getStringViaProvider(context, PrefConst.KEY_CODE_NOTIFICATION_OWNER, "")
        return io.github.magisk317.relay.contract.constant.CodeNotificationOwner.normalize(value)
    }

    @JvmStatic
    fun autoCancelCodeNotification(context: Context): Boolean {
        if (!verificationFeaturesEnabled(context)) return false
        val defaultValue = false
        return getBooleanViaProvider(context, PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION, defaultValue)
    }

    @JvmStatic
    fun getNotificationRetentionTime(context: Context): Int {
        val value = getStringViaProvider(
            context,
            PrefConst.KEY_NOTIFICATION_RETENTION_TIME,
            PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT,
        )
        return try {
            value.toInt()
        } catch (ignored: Exception) {
            0
        }
    }

    @JvmStatic
    fun deduplicateSms(context: Context): Boolean {
        val defaultValue = true
        return getBooleanViaProvider(context, PrefConst.KEY_DEDUPLICATE_SMS, defaultValue)
    }

    @JvmStatic
    fun smsBlacklistEnabled(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(context, PrefConst.KEY_ENABLE_SMS_BLACKLIST, defaultValue)
    }

    @JvmStatic
    fun smsBlacklistNumbers(context: Context): String =
        getStringViaProvider(context, PrefConst.KEY_SMS_BLACKLIST_NUMBERS, "")

    @JvmStatic
    fun smsBlacklistPrefixes(context: Context): String =
        getStringViaProvider(context, PrefConst.KEY_SMS_BLACKLIST_PREFIXES, "")

    @JvmStatic
    fun smsBlacklistRegex(context: Context): String =
        getStringViaProvider(context, PrefConst.KEY_SMS_BLACKLIST_REGEX, "")

    @JvmStatic
    fun smsBlacklistContent(context: Context): String =
        getStringViaProvider(context, PrefConst.KEY_SMS_BLACKLIST_CONTENT, "")

    @JvmStatic
    fun smsBlacklistActionDelete(context: Context): Boolean {
        val defaultValue = true
        return getBooleanViaProvider(context, PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE, defaultValue)
    }

    @JvmStatic
    fun smsBlacklistActionBlock(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(context, PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK, defaultValue)
    }

    @JvmStatic
    fun getHistoryLimit(context: Context): Int {
        return getCodeHistoryLimit(context)
    }

    @JvmStatic
    fun getCodeHistoryLimit(context: Context): Int {
        return getHistoryLimitByKey(
            context = context,
            key = PrefConst.KEY_HISTORY_LIMIT_CODE,
        )
    }

    @JvmStatic
    fun getPlainSmsHistoryLimit(context: Context): Int {
        return getHistoryLimitByKey(
            context = context,
            key = PrefConst.KEY_HISTORY_LIMIT_PLAIN_SMS,
        )
    }

    @JvmStatic
    fun getAppNotifyHistoryLimit(context: Context): Int {
        return getHistoryLimitByKey(
            context = context,
            key = PrefConst.KEY_HISTORY_LIMIT_APP_NOTIFY,
        )
    }

    @JvmStatic
    fun getCallNotifyHistoryLimit(context: Context): Int {
        return getHistoryLimitByKey(
            context = context,
            key = PrefConst.KEY_HISTORY_LIMIT_CALL_NOTIFY,
        )
    }

    @JvmStatic
    fun getHistoryLimit(context: Context, msgType: Int, isCodeSms: Boolean): Int {
        return when (msgType) {
            SmsMsg.MSG_TYPE_APP_NOTIFY -> getAppNotifyHistoryLimit(context)
            SmsMsg.MSG_TYPE_CALL_NOTIFY -> getCallNotifyHistoryLimit(context)
            SmsMsg.MSG_TYPE_SMS -> if (isCodeSms) getCodeHistoryLimit(context) else getPlainSmsHistoryLimit(context)
            else -> getCodeHistoryLimit(context)
        }
    }

    private fun getHistoryLimitByKey(context: Context, key: String): Int {
        val previousValue = getStringViaProvider(context, PrefConst.KEY_HISTORY_LIMIT, "0")
        val value = getStringViaProvider(
            context,
            key,
            previousValue,
        )
        return try {
            value.toInt()
        } catch (ignored: Exception) {
            0
        }
    }

    @JvmStatic
    fun getIpcToken(context: Context): String {
        val trace = readStringWithTrace(context, PrefConst.KEY_IPC_TOKEN, "")
        if (trace.value.isBlank() || trace.source != "remote_libxposed") {
            XLog.w(
                "Diag pref ipc_token: blank=%s source=%s",
                trace.value.isBlank(),
                trace.source,
            )
        }
        return trace.value
    }

    @JvmStatic
    fun getSimSlotRemark(context: Context, simSlot: Int): String {
        val key = when (simSlot) {
            0 -> PrefConst.KEY_SIM_SLOT1_REMARK
            1 -> PrefConst.KEY_SIM_SLOT2_REMARK
            else -> return ""
        }
        return getStringViaProvider(context, key, "").trim()
    }
}
