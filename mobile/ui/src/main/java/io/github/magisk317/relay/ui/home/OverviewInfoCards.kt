package io.github.magisk317.relay.ui.home

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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
internal fun AppInfoCard(
    appVersionName: String,
    appVersionCode: String,
    frameworkType: String,
    frameworkVersion: String,
    hasRootAccess: Boolean,
    interactive: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val rootHint = stringResource(id = R.string.root_permission_hint)
    val showRootHint: () -> Unit = {
        scope.launch { snackbarHostState.showSnackbar(rootHint) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            InfoItem(Icons.Default.Star, stringResource(id = R.string.version_name), appVersionName)
            InfoItem(Icons.AutoMirrored.Filled.List, stringResource(id = R.string.version_code), appVersionCode)
            InfoItem(
                Icons.Default.Build,
                stringResource(id = R.string.framework_type),
                frameworkType,
                onClick = if (!interactive || hasRootAccess) {
                    null
                } else {
                    showRootHint
                },
            )
            InfoItem(
                Icons.Default.CheckCircle,
                stringResource(id = R.string.framework_version),
                frameworkVersion,
                onClick = if (!interactive || hasRootAccess) {
                    null
                } else {
                    showRootHint
                },
            )
        }
    }
}

@Composable
internal fun DeviceInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            InfoItem(Icons.Default.Build, stringResource(id = R.string.android_version), Build.VERSION.RELEASE)
            InfoItem(Icons.Default.Info, stringResource(id = R.string.android_codename), Build.VERSION.CODENAME)
            InfoItem(Icons.Default.Info, stringResource(id = R.string.api_level), Build.VERSION.SDK_INT.toString())
            InfoItem(Icons.Default.AccountBox, stringResource(id = R.string.manufacturer), Build.MANUFACTURER)
            InfoItem(Icons.Default.Phone, stringResource(id = R.string.model), Build.MODEL)
        }
    }
}

@Composable
internal fun LinksCard(
    onCheckUpdate: () -> Unit,
    onDonate: () -> Unit,
    interactive: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            InfoItem(
                icon = Icons.Default.Info,
                label = stringResource(id = R.string.check_update_title),
                value = stringResource(id = R.string.check_update_summary),
                onClick = if (interactive) onCheckUpdate else null,
            )
            InfoItem(
                icon = Icons.Default.Email,
                label = stringResource(id = R.string.pref_join_qq_group_title),
                value = stringResource(id = R.string.pref_join_qq_group_summary),
                onClick = if (interactive) {
                    { PackageUtils.joinQQGroup(context)?.let { message -> showMessage(message) } }
                } else {
                    null
                },
            )
            InfoItem(
                icon = Icons.AutoMirrored.Filled.Send,
                label = stringResource(id = R.string.pref_join_telegram_group_title),
                value = stringResource(id = R.string.pref_join_telegram_group_summary),
                onClick = if (interactive) {
                    { Utils.showWebPage(context, Const.TELEGRAM_GROUP_URL)?.let { message -> showMessage(message) } }
                } else {
                    null
                },
            )
            InfoItem(
                icon = Icons.Default.Info,
                label = stringResource(id = R.string.pref_source_code_title),
                value = stringResource(id = R.string.pref_source_code_summary),
                onClick = if (interactive) {
                    { Utils.showWebPage(context, Const.PROJECT_SOURCE_CODE_URL)?.let { message -> showMessage(message) } }
                } else {
                    null
                },
            )
            InfoItem(
                icon = Icons.Default.Favorite,
                label = stringResource(id = R.string.pref_donate_by_alipay_title),
                value = stringResource(id = R.string.dialog_donate_summary),
                onClick = if (interactive) onDonate else null,
            )
        }
    }
}

@Composable
fun StatusCard(
    isEnabled: Boolean,
    showDiagnostics: Boolean,
    diagnostics: List<Pair<String, String>>,
    onClick: (() -> Unit)? = null,
) {
    val containerColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.errorContainer
    val contentColor = if (isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onErrorContainer

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
                    imageVector = if (isEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                Column {
                    Text(
                        text = if (isEnabled) stringResource(id = R.string.status_working) else stringResource(id = R.string.status_not_active),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!isEnabled) {
                        Text(
                            text = stringResource(id = R.string.status_tip),
                            style = MaterialTheme.typography.bodyMedium,
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
        headlineContent = {
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
        },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
