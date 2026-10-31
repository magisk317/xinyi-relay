package io.github.magisk317.relay.ui.home.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.domain.diagnostics.HookProcessMatrix
import io.github.magisk317.relay.domain.diagnostics.RebootVerdict
import io.github.magisk317.relay.domain.diagnostics.ScopeRequestOutcome
import io.github.magisk317.relay.domain.diagnostics.XposedScopeAccessHolder
import io.github.magisk317.relay.domain.diagnostics.XposedScopeState
import io.github.magisk317.relay.mobile.core.R
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.common.showLatestSnackbar
import io.github.magisk317.uikit.preference.SectionCard
import io.github.magisk317.uikit.surface.AppPrimaryButton
import io.github.magisk317.uikit.surface.SectionColumn
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runtime diagnostics for the Xposed side.
 *
 * The page answers three questions that the overview status card cannot:
 *  - which processes the framework injected this boot (and with which module build),
 *  - whether the module was installed/updated after boot (system_server cannot hot-load it),
 *  - which package actually publishes call notifications on this ROM (it is not always the
 *    dialer app, and it never has to be in the LSPosed scope).
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbarHostState = remember { AppSnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val scopeState by XposedScopeAccessHolder.state.collectAsStateWithLifecycle()
    var refreshTick by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf<DiagnosticsReport?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTick) {
        XposedScopeAccessHolder.current()?.refresh()
        report = withContext(Dispatchers.IO) { loadDiagnosticsReport(context) }
    }

    fun showMessage(message: String) {
        coroutineScope.launch { snackbarHostState.showLatestSnackbar(message) }
    }

    fun requestMissingScope() {
        val wanted = report?.missingScope.orEmpty()
        if (wanted.isEmpty()) {
            showMessage(context.getString(R.string.diagnostics_scope_no_missing))
            return
        }
        val access = XposedScopeAccessHolder.current()
        if (access == null) {
            showMessage(context.getString(R.string.diagnostics_scope_unavailable))
            return
        }
        busy = true
        access.requestMissing(wanted) { outcome ->
            busy = false
            val message = when (outcome) {
                is ScopeRequestOutcome.Approved -> context.getString(R.string.diagnostics_scope_approved)
                is ScopeRequestOutcome.Rejected ->
                    context.getString(R.string.diagnostics_scope_rejected, outcome.message)
                is ScopeRequestOutcome.Failed ->
                    context.getString(R.string.diagnostics_scope_failed, outcome.message)
                is ScopeRequestOutcome.Unavailable ->
                    context.getString(R.string.diagnostics_scope_unavailable)
            }
            showMessage(message)
            refreshTick++
        }
    }

    fun copyReport() {
        val current = report ?: return
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(
            ClipData.newPlainText("xinyi-diagnostics", buildDiagnosticsText(context, current, scopeState)),
        )
        showMessage(context.getString(R.string.diagnostics_copy_done))
    }

    val body: @Composable (PaddingValues) -> Unit = { padding ->
        SectionColumn(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val current = report
            if (current == null) {
                AppText(text = stringResource(id = R.string.diagnostics_loading), role = AppTextRole.Body)
            } else {
                RuntimeSection(current)
                HookInstallSection(current)
                PermissionsSection(current)
                ProcessSection(current)
                ScopeSection(
                    report = current,
                    scope = scopeState,
                    busy = busy,
                    onRequestScope = ::requestMissingScope,
                    onRefresh = { refreshTick++ },
                )
                SmsSection(current)
                NotifySection(current)
                AutoInputSection(current)
                RemoteSection(current)
                DataSection(current)
                CallSourceSection(current)
                AppPrimaryButton(
                    text = stringResource(id = R.string.diagnostics_copy),
                    onClick = ::copyReport,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> SettingsSubpageMiuix(
            title = stringResource(id = R.string.diagnostics_title),
            onBack = onBack,
            snackbarHostState = snackbarHostState,
            body = body,
        )

        UiKitStyle.Expressive -> SettingsSubpageMaterial(
            title = stringResource(id = R.string.diagnostics_title),
            onBack = onBack,
            snackbarHostState = snackbarHostState,
            body = body,
        )
    }
}

@Composable
private fun RuntimeSection(report: DiagnosticsReport) {
    SectionCard(
        title = stringResource(id = R.string.diagnostics_section_runtime),
        accordionMode = false,
        sectionExpanded = true,
        onExpandedChange = {},
    ) {
        DiagnosticRow(stringResource(id = R.string.diagnostics_framework), report.frameworkLabel)
        DiagnosticRow(stringResource(id = R.string.diagnostics_module_version), report.moduleVersion)
        DiagnosticRow(stringResource(id = R.string.diagnostics_boot_time), formatDiagnosticTime(report.bootStartAtMs))
        DiagnosticRow(stringResource(id = R.string.diagnostics_install_time), formatDiagnosticTime(report.firstInstallTimeMs))
        DiagnosticRow(stringResource(id = R.string.diagnostics_update_time), formatDiagnosticTime(report.lastUpdateTimeMs))
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_service_label),
            value = stringResource(
                id = R.string.diagnostics_service_value,
                stringResource(
                    id = if (report.serviceConnected) {
                        R.string.diagnostics_service_connected
                    } else {
                        R.string.diagnostics_service_disconnected
                    },
                ),
                formatDiagnosticTime(report.lastServiceBindAtMs),
            ),
        )
        if (report.lastHookAtMs > 0L) {
            DiagnosticRow(
                label = stringResource(id = R.string.diagnostics_last_hook),
                value = stringResource(
                    id = R.string.diagnostics_last_hook_value,
                    report.lastHookPackage.ifBlank { "—" },
                    report.lastHookProcess.ifBlank { "—" },
                    formatDiagnosticTime(report.lastHookAtMs),
                    report.lastHookSource.ifBlank { "—" },
                ),
            )
        }
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_verdict),
            value = stringResource(id = verdictTextRes(report.verdict.reason)),
        )
    }
}

@Composable
private fun ProcessSection(report: DiagnosticsReport) {
    SectionCard(
        title = stringResource(id = R.string.diagnostics_section_processes),
        accordionMode = false,
        sectionExpanded = true,
        onExpandedChange = {},
    ) {
        if (report.processes.isEmpty()) {
            DiagnosticRow(
                label = stringResource(id = R.string.diagnostics_process_other),
                value = stringResource(id = R.string.diagnostics_processes_empty),
            )
        } else {
            report.processes.forEach { row ->
                val stale = if (row.thisBoot) {
                    null
                } else {
                    stringResource(id = R.string.diagnostics_process_stale)
                }
                val version = stringResource(id = R.string.diagnostics_process_version, row.moduleVersion)
                DiagnosticRow(
                    label = processLabel(row.packageName) + " · " + row.processName,
                    value = listOfNotNull(stale, formatDiagnosticTime(row.lastSeenAtMs), version)
                        .joinToString(" · "),
                )
            }
            if (HookProcessMatrix.systemRow(report.processes) == null) {
                DiagnosticRow(
                    label = stringResource(id = R.string.diagnostics_process_system),
                    value = stringResource(id = R.string.diagnostics_process_not_reported),
                )
            }
        }
    }
}

@Composable
private fun ScopeSection(
    report: DiagnosticsReport,
    scope: XposedScopeState,
    busy: Boolean,
    onRequestScope: () -> Unit,
    onRefresh: () -> Unit,
) {
    SectionCard(
        title = stringResource(id = R.string.diagnostics_section_scope),
        accordionMode = false,
        sectionExpanded = true,
        onExpandedChange = {},
    ) {
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_scope_live),
            value = if (scope.connected) {
                scope.packages.ifEmpty { listOf(stringResource(id = R.string.diagnostics_scope_none)) }
                    .joinToString(", ")
            } else {
                stringResource(id = R.string.diagnostics_scope_unknown)
            },
        )
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_scope_recommended),
            value = report.recommendedScope.joinToString(", "),
        )
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_scope_missing),
            value = report.missingScope.ifEmpty { listOf(stringResource(id = R.string.diagnostics_scope_none)) }
                .joinToString(", "),
        )
        AppPrimaryButton(
            text = stringResource(id = R.string.diagnostics_scope_request),
            onClick = onRequestScope,
            enabled = !busy && scope.connected && report.missingScope.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
        AppPrimaryButton(
            text = stringResource(id = R.string.diagnostics_refresh),
            onClick = onRefresh,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CallSourceSection(report: DiagnosticsReport) {
    val sources = report.callSources
    SectionCard(
        title = stringResource(id = R.string.diagnostics_section_call),
        accordionMode = false,
        sectionExpanded = true,
        onExpandedChange = {},
    ) {
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_call_default_dialer),
            value = sources.defaultDialerPackage.ifBlank { stringResource(id = R.string.diagnostics_unknown) },
        )
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_call_dial_intent),
            value = sources.dialIntentPackage.ifBlank { stringResource(id = R.string.diagnostics_unknown) },
        )
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_call_incall),
            value = sources.incallServicePackages.ifEmpty {
                listOf(stringResource(id = R.string.diagnostics_scope_none))
            }.joinToString(", "),
        )
        sources.knownSources.forEach { source ->
            DiagnosticRow(
                label = source.packageName,
                value = stringResource(
                    id = if (source.matched) {
                        R.string.diagnostics_call_matched
                    } else {
                        R.string.diagnostics_call_unmatched
                    },
                ),
            )
        }
        val last = sources.lastObserved
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_call_last_seen),
            value = if (last.packageName.isBlank()) {
                stringResource(id = R.string.diagnostics_call_last_seen_none)
            } else {
                stringResource(
                    id = R.string.diagnostics_call_last_seen_value,
                    last.packageName,
                    last.channelId.ifBlank { stringResource(id = R.string.diagnostics_unknown) },
                )
            },
        )
    }
}

private const val VALUE_COLUMN_WEIGHT = 1.4f

@Composable
private fun HookInstallSection(report: DiagnosticsReport) {
    SectionCard(
        title = stringResource(id = R.string.diagnostics_section_hooks),
        accordionMode = false,
        sectionExpanded = true,
        onExpandedChange = {},
    ) {
        if (report.hookInstalls.isEmpty()) {
            DiagnosticRow(
                label = stringResource(id = R.string.diagnostics_hook_nms),
                value = stringResource(id = R.string.diagnostics_hooks_empty),
            )
        } else {
            report.hookInstalls.sortedBy { it.hook }.forEach { status ->
                DiagnosticRow(
                    label = stringResource(id = hookLabelRes(status.hook)),
                    value = stringResource(
                        id = R.string.diagnostics_hook_value,
                        stringResource(id = hookStatusRes(status.status)),
                        status.detail.ifBlank { "—" },
                        formatDiagnosticTime(status.lastSeenAtMs),
                    ),
                )
            }
        }
    }
}

@Composable
private fun PermissionsSection(report: DiagnosticsReport) {
    SectionCard(
        title = stringResource(id = R.string.diagnostics_section_permissions),
        accordionMode = false,
        sectionExpanded = true,
        onExpandedChange = {},
    ) {
        val capabilities = report.capabilities
        PermissionRow(
            label = stringResource(id = R.string.diagnostics_perm_nls),
            enabled = capabilities.notificationListener,
            grantedText = false,
        )
        PermissionRow(
            label = stringResource(id = R.string.diagnostics_perm_a11y),
            enabled = capabilities.accessibility,
            grantedText = false,
        )
        PermissionRow(
            label = stringResource(id = R.string.diagnostics_perm_post_notifications),
            enabled = capabilities.postNotifications,
            grantedText = true,
        )
        PermissionRow(
            label = stringResource(id = R.string.diagnostics_perm_battery),
            enabled = capabilities.batteryUnrestricted,
            grantedText = false,
        )
    }
}

@Composable
private fun PermissionRow(label: String, enabled: Boolean, grantedText: Boolean) {
    DiagnosticRow(
        label = label,
        value = stringResource(
            id = when {
                grantedText && enabled -> R.string.diagnostics_granted
                grantedText -> R.string.diagnostics_denied
                enabled -> R.string.diagnostics_state_enabled
                else -> R.string.diagnostics_state_disabled
            },
        ),
    )
}

@Composable
private fun SmsSection(report: DiagnosticsReport) {
    val sms = report.sms
    SectionCard(
        title = stringResource(id = R.string.diagnostics_section_sms),
        accordionMode = false,
        sectionExpanded = true,
        onExpandedChange = {},
    ) {
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_sms_records),
            value = sms.recordCount.toString(),
        )
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_sms_last),
            value = if (sms.lastSmsAtMs <= 0L) {
                "—"
            } else {
                stringResource(
                    id = R.string.diagnostics_sms_last_value,
                    formatDiagnosticTime(sms.lastSmsAtMs),
                    sms.lastSmsSimSlot,
                )
            },
        )
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_forward_24h),
            value = stringResource(
                id = R.string.diagnostics_forward_24h_value,
                sms.forward24hTotal,
                sms.forward24hSuccess,
                sms.forward24hFailed,
            ),
        )
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_last_forward),
            value = if (sms.lastForwardAtMs <= 0L) {
                "—"
            } else {
                stringResource(
                    id = R.string.diagnostics_last_forward_value,
                    formatDiagnosticTime(sms.lastForwardAtMs),
                    stringResource(id = resultTextRes(sms.lastForwardSuccess)),
                )
            },
        )
    }
}

@Composable
private fun NotifySection(report: DiagnosticsReport) {
    val event = report.notifyEvent
    SectionCard(
        title = stringResource(id = R.string.diagnostics_section_notify),
        accordionMode = false,
        sectionExpanded = true,
        onExpandedChange = {},
    ) {
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_notify_last),
            value = if (event.packageName.isBlank()) {
                stringResource(id = R.string.diagnostics_notify_none)
            } else {
                val summary = stringResource(
                    id = R.string.diagnostics_notify_value,
                    sourceLabel(event.source),
                    event.packageName,
                    event.channelId.ifBlank { "—" },
                    event.skipReason.ifBlank { stringResource(id = R.string.diagnostics_result_accepted) },
                )
                if (event.lastSeenAtMs > 0L) {
                    summary + " · " + formatDiagnosticTime(event.lastSeenAtMs)
                } else {
                    summary
                }
            },
        )
    }
}

@Composable
private fun AutoInputSection(report: DiagnosticsReport) {
    val auto = report.autoInput
    SectionCard(
        title = stringResource(id = R.string.diagnostics_section_autoinput),
        accordionMode = false,
        sectionExpanded = true,
        onExpandedChange = {},
    ) {
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_auto_24h),
            value = stringResource(
                id = R.string.diagnostics_auto_24h_value,
                auto.attempts24h,
                auto.success24h,
                auto.failed24h,
            ),
        )
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_auto_last),
            value = if (auto.lastAttemptAtMs <= 0L) {
                "—"
            } else {
                val result = when (auto.lastSuccess) {
                    true -> stringResource(id = R.string.diagnostics_result_success)
                    false -> listOf(
                        stringResource(id = R.string.diagnostics_result_failure),
                        auto.lastFailReason,
                    ).filter { it.isNotBlank() }.joinToString(": ")
                    null -> stringResource(id = R.string.diagnostics_unknown)
                }
                stringResource(
                    id = R.string.diagnostics_auto_last_value,
                    formatDiagnosticTime(auto.lastAttemptAtMs),
                    result,
                )
            },
        )
    }
}

@Composable
private fun RemoteSection(report: DiagnosticsReport) {
    val remote = report.remote
    SectionCard(
        title = stringResource(id = R.string.diagnostics_section_remote),
        accordionMode = false,
        sectionExpanded = true,
        onExpandedChange = {},
    ) {
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_remote_base),
            value = stringResource(
                id = if (remote.baseConfigured) {
                    R.string.diagnostics_remote_base_configured
                } else {
                    R.string.diagnostics_remote_base_missing
                },
            ),
        )
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_remote_state),
            value = remote.syncState.ifBlank { stringResource(id = R.string.diagnostics_unknown) },
        )
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_remote_heartbeat),
            value = formatDiagnosticTime(remote.lastHeartbeatAtMs),
        )
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_remote_pull),
            value = formatDiagnosticTime(remote.lastPullAtMs),
        )
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_remote_push),
            value = formatDiagnosticTime(remote.lastPushAtMs),
        )
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_remote_revision),
            value = stringResource(
                id = R.string.diagnostics_remote_revision_value,
                remote.configRevision.ifBlank { "0" },
                remote.configPending.ifBlank { "0" },
            ),
        )
    }
}

@Composable
private fun DataSection(report: DiagnosticsReport) {
    val data = report.data
    SectionCard(
        title = stringResource(id = R.string.diagnostics_section_data),
        accordionMode = false,
        sectionExpanded = true,
        onExpandedChange = {},
    ) {
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_data_db),
            value = formatDiagnosticBytes(data.databaseBytes),
        )
        DiagnosticRow(
            label = stringResource(id = R.string.diagnostics_data_root),
            value = if (data.rootCatchupEnabled) {
                stringResource(
                    id = R.string.diagnostics_data_root_on,
                    data.rootCatchupIntervalMin.ifBlank { "—" },
                    stringResource(
                        id = if (data.rootCatchupWriteback) {
                            R.string.diagnostics_yes
                        } else {
                            R.string.diagnostics_no
                        },
                    ),
                )
            } else {
                stringResource(id = R.string.diagnostics_data_root_off)
            },
        )
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppText(
            text = label,
            modifier = Modifier.weight(1f),
            role = AppTextRole.Body,
        )
        AppText(
            text = value,
            modifier = Modifier.weight(VALUE_COLUMN_WEIGHT),
            role = AppTextRole.BodySmall,
        )
    }
}

@StringRes
private fun hookLabelRes(hook: String): Int = when (hook) {
    "sms_handler" -> R.string.diagnostics_hook_sms_handler
    "sms_forward" -> R.string.diagnostics_hook_sms_forward
    "sms_provider" -> R.string.diagnostics_hook_sms_provider
    "mms" -> R.string.diagnostics_hook_mms
    "nms" -> R.string.diagnostics_hook_nms
    "keepalive" -> R.string.diagnostics_hook_keepalive
    else -> R.string.diagnostics_process_other
}

@StringRes
private fun hookStatusRes(status: String): Int = when (status) {
    "installed" -> R.string.diagnostics_hook_status_installed
    "class_missing" -> R.string.diagnostics_hook_status_class_missing
    "method_missing" -> R.string.diagnostics_hook_status_method_missing
    "no_entrypoints" -> R.string.diagnostics_hook_status_no_entrypoints
    "error" -> R.string.diagnostics_hook_status_error
    else -> R.string.diagnostics_unknown
}

@Composable
private fun sourceLabel(source: String): String = when (source) {
    "nms" -> stringResource(id = R.string.diagnostics_source_nms)
    "nls" -> stringResource(id = R.string.diagnostics_source_nls)
    else -> source.ifBlank { "—" }
}

@StringRes
private fun resultTextRes(success: Boolean?): Int = when (success) {
    true -> R.string.diagnostics_result_success
    false -> R.string.diagnostics_result_failure
    null -> R.string.diagnostics_unknown
}

private fun formatDiagnosticBytes(bytes: Long): String = when {
    bytes <= 0L -> "—"
    bytes >= BYTES_PER_MB -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / BYTES_PER_MB)
    else -> String.format(Locale.US, "%d KB", bytes / BYTES_PER_KB)
}

private const val BYTES_PER_KB = 1024L
private const val BYTES_PER_MB = BYTES_PER_KB * 1024L

@StringRes
private fun verdictTextRes(reason: RebootVerdict.Reason): Int = when (reason) {
    RebootVerdict.Reason.OK -> R.string.diagnostics_verdict_ok
    RebootVerdict.Reason.SYSTEM_SCOPE_MISSING -> R.string.diagnostics_verdict_scope_missing
    RebootVerdict.Reason.INSTALLED_AFTER_BOOT -> R.string.diagnostics_verdict_installed_after_boot
    RebootVerdict.Reason.SYSTEM_NOT_LOADED -> R.string.diagnostics_verdict_system_not_loaded
}

@Composable
private fun processLabel(packageName: String): String = when (packageName) {
    "android", "system" -> stringResource(id = R.string.diagnostics_process_system)
    "com.android.phone", "com.xiaomi.phone" -> stringResource(id = R.string.diagnostics_process_phone)
    "com.android.providers.telephony" -> stringResource(id = R.string.diagnostics_process_provider)
    "com.android.mms" -> stringResource(id = R.string.diagnostics_process_mms)
    else -> stringResource(id = R.string.diagnostics_process_other)
}

private fun formatDiagnosticTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return "—"
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))
}

private fun buildDiagnosticsText(
    context: Context,
    report: DiagnosticsReport,
    scope: XposedScopeState,
): String = buildString {
    appendLine(context.getString(R.string.diagnostics_title))
    appendLine("framework=${report.frameworkLabel}")
    appendLine("module=${report.moduleVersion}")
    appendLine("boot=${formatDiagnosticTime(report.bootStartAtMs)}")
    appendLine("installed=${formatDiagnosticTime(report.firstInstallTimeMs)}")
    appendLine("updated=${formatDiagnosticTime(report.lastUpdateTimeMs)}")
    appendLine("verdict=${context.getString(verdictTextRes(report.verdict.reason))}")
    appendLine("scope.connected=${scope.connected}")
    appendLine("scope.granted=${scope.packages.joinToString(",")}")
    appendLine("scope.recommended=${report.recommendedScope.joinToString(",")}")
    appendLine("scope.missing=${report.missingScope.joinToString(",")}")
    report.processes.forEach { row ->
        appendLine(
            "process=${row.packageName}/${row.processName}" +
                " thisBoot=${row.thisBoot}" +
                " version=${row.moduleVersion}" +
                " seen=${formatDiagnosticTime(row.lastSeenAtMs)}",
        )
    }
    val sources = report.callSources
    appendLine("call.defaultDialer=${sources.defaultDialerPackage}")
    appendLine("call.dialIntent=${sources.dialIntentPackage}")
    appendLine("call.incall=${sources.incallServicePackages.joinToString(",")}")
    sources.knownSources.forEach { source ->
        appendLine("call.source=${source.packageName} matched=${source.matched}")
    }
    appendLine(
        "call.lastObserved=${sources.lastObserved.packageName}" +
            " channel=${sources.lastObserved.channelId}" +
            " category=${sources.lastObserved.category}" +
            " at=${formatDiagnosticTime(sources.lastObserved.lastSeenAtMs)}",
    )
    appendLine("service.connected=${report.serviceConnected}")
    appendLine("service.lastBind=${formatDiagnosticTime(report.lastServiceBindAtMs)}")
    appendLine(
        "hook.last=${report.lastHookPackage}/${report.lastHookProcess}" +
            " at=${formatDiagnosticTime(report.lastHookAtMs)}" +
            " source=${report.lastHookSource}",
    )
    report.hookInstalls.forEach { status ->
        appendLine(
            "hook.install=${status.hook} status=${status.status}" +
                " detail=${status.detail}" +
                " at=${formatDiagnosticTime(status.lastSeenAtMs)}",
        )
    }
    appendLine(
        "capabilities.nls=${report.capabilities.notificationListener}" +
            " a11y=${report.capabilities.accessibility}" +
            " postNotifications=${report.capabilities.postNotifications}" +
            " battery=${report.capabilities.batteryUnrestricted}",
    )
    appendLine(
        "sms.records=${report.sms.recordCount}" +
            " last=${formatDiagnosticTime(report.sms.lastSmsAtMs)}" +
            " sim=${report.sms.lastSmsSimSlot}",
    )
    appendLine(
        "forward.24h=${report.sms.forward24hTotal}/${report.sms.forward24hSuccess}/${report.sms.forward24hFailed}" +
            " last=${formatDiagnosticTime(report.sms.lastForwardAtMs)}" +
            " success=${report.sms.lastForwardSuccess}",
    )
    appendLine(
        "notify.source=${report.notifyEvent.source}" +
            " pkg=${report.notifyEvent.packageName}" +
            " channel=${report.notifyEvent.channelId}" +
            " type=${report.notifyEvent.msgType}" +
            " skip=${report.notifyEvent.skipReason}" +
            " at=${formatDiagnosticTime(report.notifyEvent.lastSeenAtMs)}",
    )
    appendLine(
        "autoInput.24h=${report.autoInput.attempts24h}/${report.autoInput.success24h}/${report.autoInput.failed24h}" +
            " last=${formatDiagnosticTime(report.autoInput.lastAttemptAtMs)}" +
            " success=${report.autoInput.lastSuccess}" +
            " reason=${report.autoInput.lastFailReason}",
    )
    appendLine(
        "remote.state=${report.remote.syncState}" +
            " baseConfigured=${report.remote.baseConfigured}" +
            " heartbeat=${formatDiagnosticTime(report.remote.lastHeartbeatAtMs)}" +
            " pull=${formatDiagnosticTime(report.remote.lastPullAtMs)}" +
            " push=${formatDiagnosticTime(report.remote.lastPushAtMs)}" +
            " revision=${report.remote.configRevision}" +
            " pending=${report.remote.configPending}",
    )
    appendLine(
        "data.dbBytes=${report.data.databaseBytes}" +
            " rootCatchup=${report.data.rootCatchupEnabled}" +
            " interval=${report.data.rootCatchupIntervalMin}" +
            " writeback=${report.data.rootCatchupWriteback}",
    )
}
