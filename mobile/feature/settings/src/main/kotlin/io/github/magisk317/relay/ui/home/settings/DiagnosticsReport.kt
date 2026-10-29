package io.github.magisk317.relay.ui.home.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.magisk317.relay.contract.constant.RelayPrefConst
import io.github.magisk317.relay.domain.diagnostics.CallNotificationSourceDetector
import io.github.magisk317.relay.domain.diagnostics.DiagnosticsMetricsReader
import io.github.magisk317.relay.domain.diagnostics.HookProcessMatrix
import io.github.magisk317.relay.domain.diagnostics.RebootVerdict
import io.github.magisk317.relay.domain.diagnostics.ScopeRecommender
import io.github.magisk317.relay.domain.diagnostics.XposedScopeAccessHolder
import io.github.magisk317.relay.domain.diagnostics.XposedScopeState
import io.github.magisk317.relay.mobile.core.R
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationDiagnosticsStore
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.smscode.runtime.contract.diagnostics.HookInstallStatus
import io.github.magisk317.smscode.runtime.contract.diagnostics.NotifyEventSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class CapabilityChecks(
    val notificationListener: Boolean,
    val accessibility: Boolean,
    val postNotifications: Boolean,
    val batteryUnrestricted: Boolean,
)

internal data class SmsRelaySummary(
    val recordCount: Long,
    val lastSmsAtMs: Long,
    val lastSmsSimSlot: Int,
    val forward24hTotal: Long,
    val forward24hSuccess: Long,
    val forward24hFailed: Long,
    val lastForwardAtMs: Long,
    val lastForwardSuccess: Boolean?,
)

internal data class AutoInputSummary(
    val attempts24h: Long,
    val success24h: Long,
    val failed24h: Long,
    val lastAttemptAtMs: Long,
    val lastSuccess: Boolean?,
    val lastFailReason: String,
)

internal data class RemoteSummary(
    val baseConfigured: Boolean,
    val syncState: String,
    val lastHeartbeatAtMs: Long,
    val lastPullAtMs: Long,
    val lastPushAtMs: Long,
    val configRevision: String,
    val configPending: String,
)

internal data class DataSummary(
    val databaseBytes: Long,
    val rootCatchupEnabled: Boolean,
    val rootCatchupIntervalMin: String,
    val rootCatchupWriteback: Boolean,
)

internal data class DiagnosticsReport(
    val frameworkLabel: String,
    val moduleVersion: String,
    val bootStartAtMs: Long,
    val firstInstallTimeMs: Long,
    val lastUpdateTimeMs: Long,
    val verdict: RebootVerdict.Result,
    val processes: List<HookProcessMatrix.Row>,
    val scope: XposedScopeState,
    val recommendedScope: List<String>,
    val missingScope: List<String>,
    val callSources: CallNotificationSourceDetector.Report,
    val serviceConnected: Boolean,
    val lastServiceBindAtMs: Long,
    val lastHookPackage: String,
    val lastHookProcess: String,
    val lastHookAtMs: Long,
    val lastHookSource: String,
    val hookInstalls: List<HookInstallStatus>,
    val capabilities: CapabilityChecks,
    val sms: SmsRelaySummary,
    val notifyEvent: NotifyEventSnapshot,
    val autoInput: AutoInputSummary,
    val remote: RemoteSummary,
    val data: DataSummary,
)

internal suspend fun loadDiagnosticsReport(context: Context): DiagnosticsReport = withContext(Dispatchers.IO) {
    val scope = XposedScopeAccessHolder.state.value
    val snapshot = ActivationDiagnosticsStore.snapshot(context)
    val bootStartAtMs = (System.currentTimeMillis() - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    val packageInfo = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()
    val firstInstallTimeMs = packageInfo?.firstInstallTime ?: 0L
    val lastUpdateTimeMs = packageInfo?.lastUpdateTime ?: 0L
    val moduleVersion = buildString {
        append(packageInfo?.versionName?.ifBlank { null } ?: "?")
        append(" (")
        append(packageInfo?.longVersionCode ?: 0L)
        append(")")
    }
    val processes = HookProcessMatrix.build(snapshot.hookProcesses, bootStartAtMs)
    val verdict = RebootVerdict.evaluate(
        bootStartAtMs = bootStartAtMs,
        firstInstallTimeMs = firstInstallTimeMs,
        lastUpdateTimeMs = lastUpdateTimeMs,
        systemScopeSelected = scope.hasSystemScope,
        systemProcessSeenThisBoot = HookProcessMatrix.hasSystemHeartbeatThisBoot(processes),
    )
    val installedPackages = runCatching {
        context.packageManager.getInstalledPackages(0)
            .mapNotNull { it.packageName }
            .toSet()
    }.getOrDefault(emptySet())
    val recommended = ScopeRecommender.recommend(installedPackages)
    val frameworkName = scope.frameworkName.ifBlank { snapshot.lastServiceFrameworkName }
    val frameworkVersion = scope.frameworkVersion.ifBlank { snapshot.lastServiceFrameworkVersion }
    val frameworkLabel = listOf(frameworkName, frameworkVersion)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { null }
        ?: context.getString(R.string.diagnostics_unknown)

    val metrics = DiagnosticsMetricsReader.read(context)

    val remoteBaseUrl = readPref(context, RelayPrefConst.KEY_REMOTE_AGENT_BASE_URL)
    val remote = RemoteSummary(
        baseConfigured = remoteBaseUrl.isNotBlank(),
        syncState = readPref(context, RelayPrefConst.KEY_REMOTE_AGENT_SYNC_STATE, "idle"),
        lastHeartbeatAtMs = readPref(context, RelayPrefConst.KEY_REMOTE_AGENT_LAST_HEARTBEAT_AT, "0").toLongOrNull() ?: 0L,
        lastPullAtMs = readPref(context, RelayPrefConst.KEY_REMOTE_AGENT_LAST_PULL_AT, "0").toLongOrNull() ?: 0L,
        lastPushAtMs = readPref(context, RelayPrefConst.KEY_REMOTE_AGENT_LAST_PUSH_AT, "0").toLongOrNull() ?: 0L,
        configRevision = readPref(context, RelayPrefConst.KEY_LOCAL_CONFIG_REVISION, "0"),
        configPending = readPref(context, RelayPrefConst.KEY_LOCAL_CONFIG_PENDING_LOCAL_CHANGES, "0"),
    )
    val data = DataSummary(
        databaseBytes = context.getDatabasePath(RELATIVE_DB_NAME).length(),
        rootCatchupEnabled = readPrefBool(context, RelayPrefConst.KEY_ROOT_DB_CATCHUP_ENABLE),
        rootCatchupIntervalMin = readPref(context, RelayPrefConst.KEY_ROOT_DB_CATCHUP_INTERVAL_MIN),
        rootCatchupWriteback = readPrefBool(context, RelayPrefConst.KEY_ROOT_DB_CATCHUP_WRITEBACK),
    )

    return@withContext DiagnosticsReport(
        frameworkLabel = frameworkLabel,
        moduleVersion = moduleVersion,
        bootStartAtMs = bootStartAtMs,
        firstInstallTimeMs = firstInstallTimeMs,
        lastUpdateTimeMs = lastUpdateTimeMs,
        verdict = verdict,
        processes = processes,
        scope = scope,
        recommendedScope = recommended,
        missingScope = ScopeRecommender.missing(scope.packages, recommended),
        callSources = CallNotificationSourceDetector.detect(context, snapshot.lastCallNotifySource),
        serviceConnected = scope.connected,
        lastServiceBindAtMs = snapshot.lastServiceBindAtMs,
        lastHookPackage = snapshot.lastHookPackage,
        lastHookProcess = snapshot.lastHookProcess,
        lastHookAtMs = snapshot.lastHookAtMs,
        lastHookSource = snapshot.lastHookSource,
        hookInstalls = snapshot.hookInstalls,
        capabilities = readCapabilities(context),
        sms = SmsRelaySummary(
            recordCount = metrics.recordCount,
            lastSmsAtMs = metrics.lastSmsAtMs,
            lastSmsSimSlot = metrics.lastSmsSimSlot,
            forward24hTotal = metrics.forward24hTotal,
            forward24hSuccess = metrics.forward24hSuccess,
            forward24hFailed = metrics.forward24hFailed,
            lastForwardAtMs = metrics.lastForwardAtMs,
            lastForwardSuccess = metrics.lastForwardSuccess,
        ),
        notifyEvent = snapshot.lastNotifyEvent,
        autoInput = AutoInputSummary(
            attempts24h = metrics.autoAttempts24h,
            success24h = metrics.autoSuccess24h,
            failed24h = metrics.autoFailed24h,
            lastAttemptAtMs = metrics.lastAutoAttemptAtMs,
            lastSuccess = metrics.lastAutoSuccess,
            lastFailReason = metrics.lastAutoFailReason,
        ),
        remote = remote,
        data = data,
    )
}

private fun readCapabilities(context: Context): CapabilityChecks = CapabilityChecks(
    notificationListener = NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName),
    accessibility = isAccessibilityServiceEnabled(context),
    postNotifications = isPostNotificationsGranted(context),
    batteryUnrestricted = runCatching {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }.getOrDefault(false),
)

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val enabled = runCatching {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
    }.getOrNull().orEmpty()
    if (enabled.isBlank()) return false
    return enabled.split(':').any { entry ->
        entry.startsWith("${context.packageName}/") &&
            entry.contains(AUTO_INPUT_SERVICE_CLASS)
    }
}

private fun isPostNotificationsGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

private suspend fun readPref(
    context: Context,
    key: String,
    defaultValue: String = "",
): String = runCatching {
    AppPreferencesDataStore.getString(context, key, defaultValue)
}.getOrDefault(defaultValue)

private suspend fun readPrefBool(
    context: Context,
    key: String,
    defaultValue: Boolean = false,
): Boolean = runCatching {
    AppPreferencesDataStore.getBoolean(context, key, defaultValue)
}.getOrDefault(defaultValue)

private const val RELATIVE_DB_NAME = "relay_room.db"
private const val AUTO_INPUT_SERVICE_CLASS = "AutoInputAccessibilityService"
