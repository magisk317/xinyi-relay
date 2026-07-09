package io.github.magisk317.relay.ui.home.overview

import io.github.magisk317.uikit.common.showLatestSnackbar

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.android.diagnostics.ActivationDiagnosticsSnapshot
import io.github.magisk317.relay.common.utils.PackageUtils
import io.github.magisk317.relay.common.utils.Utils
import io.github.magisk317.relay.contract.constant.RelayAppConst as Const
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState
import io.github.magisk317.relay.feature.mode.StandardModeFeatureGate
import io.github.magisk317.relay.feature.mode.StandardModeFeatureGate.Feature.*
import io.github.magisk317.relay.feature.mode.WorkMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun StatusCard(
    isEnhancedModeEnabled: Boolean,
    isStandardModeEnabled: Boolean,
    showBatteryOptimizationHint: Boolean = false,
    showDiagnostics: Boolean,
    diagnostics: List<Pair<String, String>>,
    onClick: (() -> Unit)? = null,
    onBatteryOptimizationClick: (() -> Unit)? = null,
) {
    val isWorking = isEnhancedModeEnabled || isStandardModeEnabled
    val containerColor = if (isWorking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.errorContainer
    val contentColor = if (isWorking) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onErrorContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        onClick = { onClick?.invoke() },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = if (isWorking) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                Column {
                    Text(
                        text = when {
                            isEnhancedModeEnabled -> stringResource(id = R.string.status_working_enhanced)
                            isStandardModeEnabled -> stringResource(id = R.string.status_working_standard)
                            else -> stringResource(id = R.string.status_not_active)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!isWorking) {
                        Text(
                            text = stringResource(id = R.string.status_tip),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else if (isStandardModeEnabled) {
                        Text(
                            text = stringResource(id = R.string.standard_mode_service_notification_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor.copy(alpha = 0.82f),
                        )
                    }
                }
            }
            if (showDiagnostics && diagnostics.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(contentColor.copy(alpha = 0.12f))
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    diagnostics.forEach { (label, value) ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor.copy(alpha = 0.8f),
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
            // Standard mode limitations hint
            if (isStandardModeEnabled) {
                if (showBatteryOptimizationHint) {
                    Row(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(contentColor.copy(alpha = 0.12f))
                            .clickable(enabled = onBatteryOptimizationClick != null) {
                                onBatteryOptimizationClick?.invoke()
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = contentColor.copy(alpha = 0.85f),
                        )
                        Text(
                            text = stringResource(R.string.standard_mode_battery_optimization_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.82f),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                val disabledFeatureLabels = listOf(
                    SMS_HOOK_INTERCEPT to R.string.standard_mode_limit_sms_hook,
                    SMS_BLACKLIST_BLOCK to R.string.standard_mode_limit_block_sms,
                    COPY_CODE_TO_CLIPBOARD to R.string.standard_mode_limit_clipboard,
                    DELETE_SMS_ON_CODE_INPUT to R.string.standard_mode_limit_delete_sms,
                    CALL_HOOK_INTERCEPT to R.string.standard_mode_limit_call_hook,
                    KEEPALIVE_OOM_ADJ to R.string.standard_mode_limit_keepalive,
                )
                    .filter { (feature, _) ->
                        !StandardModeFeatureGate.isAvailable(feature, WorkMode.Standard)
                    }
                    .map { (_, labelRes) ->
                        stringResource(labelRes)
                    }
                if (disabledFeatureLabels.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(contentColor.copy(alpha = 0.08f))
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.standard_mode_limitations_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor.copy(alpha = 0.7f),
                        )
                        Text(
                            text = disabledFeatureLabels.joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

internal fun buildStatusDiagnostics(
    context: android.content.Context,
    snapshot: ActivationDiagnosticsSnapshot,
    runtimeConnected: Boolean,
): List<Pair<String, String>> {
    val serviceValue = buildString {
        append(
            if (runtimeConnected) {
                context.getString(R.string.overview_runtime_connected)
            } else {
                context.getString(R.string.overview_runtime_disconnected)
            },
        )
        if (snapshot.lastServiceBindAtMs > 0L) {
            append(" · ")
            append(context.getString(R.string.overview_runtime_last_connected))
            append(" ")
            append(formatStatusDiagnosticTime(snapshot.lastServiceBindAtMs))
        }
        if (snapshot.lastServiceFrameworkName.isNotBlank() || snapshot.lastServiceFrameworkVersion.isNotBlank()) {
            append(" · ")
            append(snapshot.lastServiceFrameworkName.ifBlank { context.getString(R.string.overview_runtime_unknown) })
            append(" ")
            append(snapshot.lastServiceFrameworkVersion.ifBlank { context.getString(R.string.overview_runtime_unknown) })
        }
    }
    val hookProcess = listOf(
        snapshot.lastHookPackage.ifBlank { context.getString(R.string.overview_runtime_none) },
        snapshot.lastHookProcess.ifBlank { context.getString(R.string.overview_runtime_none) },
    ).joinToString(" / ")
    val hookTime = buildString {
        append(
            if (snapshot.lastHookAtMs > 0L) {
                formatStatusDiagnosticTime(snapshot.lastHookAtMs)
            } else {
                context.getString(R.string.overview_runtime_not_available)
            },
        )
        if (snapshot.lastHookSource.isNotBlank()) {
            append(" · ")
            append(snapshot.lastHookSource)
        }
    }
    return listOf(
        context.getString(R.string.overview_runtime_service_label) to serviceValue,
        context.getString(R.string.overview_runtime_recent_hook_process) to hookProcess,
        context.getString(R.string.overview_runtime_recent_hook_time) to hookTime,
    )
}

private fun formatStatusDiagnosticTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))
}

@Composable
fun InfoItem(icon: ImageVector, label: String, value: String, onClick: (() -> Unit)? = null) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
