@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.relay.ui.home.appconfig

import io.github.magisk317.uikit.common.showLatestSnackbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import io.github.magisk317.uikit.common.AppSnackbarHostState
import androidx.compose.material3.TextButton
import io.github.magisk317.uikit.preference.AppSwitch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.core.R
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole

@Composable
fun AppConfigDetailScreen(
    packageName: String,
    onBack: () -> Unit,
    onConfigureNotifyChannels: () -> Unit,
    onConfigureForwardFilters: () -> Unit,
    viewModel: AppConfigViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val app = uiState.apps.firstOrNull { it.packageName == packageName } ?: viewModel.getAppByPackageName(packageName)
    val appLogs by remember(packageName) { viewModel.appNotifyLogsFlow(packageName) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { AppSnackbarHostState() }

    val appConfigDetailBody: @Composable (PaddingValues) -> Unit = { listPadding ->
if (app == null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(listPadding)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppText(text = stringResource(R.string.app_config_detail_not_found))
    }
} else {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(listPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ConfigToggleRow(
                    title = stringResource(R.string.label_blocked),
                    checked = app.blocked,
                    onCheckedChange = {
                        viewModel.setBlocked(app.packageName, it)
                        scope.launch {
                            snackbarHostState.showLatestSnackbar(context.getString(R.string.pref_sync_snackbar))
                        }
                    },
                )
                HorizontalDivider()
                ConfigToggleRow(
                    title = stringResource(R.string.label_app_notify_source_enabled),
                    checked = app.forwarding,
                    onCheckedChange = {
                        viewModel.setForwarding(app.packageName, it)
                        scope.launch {
                            snackbarHostState.showLatestSnackbar(context.getString(R.string.pref_sync_snackbar))
                        }
                    },
                )
                HorizontalDivider()
                androidx.compose.material3.ListItem(
                    supportingContent = {
                        val count = viewModel.getAppNotifyBindingCount(app.packageName)
                        AppText(
                            text = if (count <= 0) {
                                stringResource(R.string.app_notify_channel_global_summary)
                            } else {
                                stringResource(R.string.app_notify_channel_bound_count, count)
                            },
                        )
                    },
                    trailingContent = {
                        TextButton(onClick = onConfigureNotifyChannels) {
                            AppText(text = stringResource(R.string.item_config))
                        }
                    },
                ) {
                    AppText(text = stringResource(R.string.app_notify_channel_config_title))
                }
                HorizontalDivider()
                androidx.compose.material3.ListItem(
                    supportingContent = {
                        AppText(text = stringResource(R.string.app_detail_forward_filter_summary))
                    },
                    trailingContent = {
                        TextButton(onClick = onConfigureForwardFilters) {
                            AppText(text = stringResource(R.string.item_config))
                        }
                    },
                ) {
                    AppText(text = stringResource(R.string.app_detail_forward_filter_title))
                }
            }
        }

        AppRecentLogCard(logs = appLogs)
    }
    }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentUiKitStyle()) {
            UiKitStyle.Miuix -> AppConfigDetailScreenMiuix(
                title = app?.label ?: packageName,
                onBack = onBack,
                body = appConfigDetailBody,
            )

            UiKitStyle.Expressive -> AppConfigDetailScreenMaterial(
                title = app?.label ?: packageName,
                onBack = onBack,
                body = appConfigDetailBody,
            )
        }

        io.github.magisk317.uikit.common.AppSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun AppRecentLogCard(logs: List<SmsMsg>) {
    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault()) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppText(
                text = stringResource(R.string.app_detail_recent_logs_title),
                role = AppTextRole.Subtitle,
                fontWeight = FontWeight.SemiBold,
            )
            if (logs.isEmpty()) {
                AppText(
                    text = stringResource(R.string.app_detail_recent_logs_empty),
                    role = AppTextRole.BodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            logs.forEachIndexed { index, log ->
                val forwardMessage = log.forwardMessage.orEmpty()
                val statusText = when (log.forwardStatus) {
                    SmsMsg.FORWARD_STATUS_SUCCESS -> stringResource(R.string.forward_status_success)
                    SmsMsg.FORWARD_STATUS_FAILED -> stringResource(R.string.forward_status_failed)
                    SmsMsg.FORWARD_STATUS_PARTIAL -> stringResource(R.string.app_detail_status_partial)
                    SmsMsg.FORWARD_STATUS_BLOCKED -> stringResource(R.string.forward_status_none)
                    else -> stringResource(R.string.forward_status_none)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AppText(
                        text = stringResource(R.string.app_detail_recent_logs_time, dateFormat.format(java.util.Date(log.date))),
                        role = AppTextRole.Footnote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AppText(
                        text = stringResource(R.string.app_detail_recent_logs_content, log.body.orEmpty()),
                        role = AppTextRole.BodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AppText(
                        text = stringResource(R.string.app_detail_recent_logs_status, statusText, log.forwardTarget ?: "-"),
                        role = AppTextRole.BodySmall,
                    )
                    if (forwardMessage.isNotBlank()) {
                        AppText(
                            text = stringResource(R.string.app_detail_recent_logs_result, forwardMessage),
                            role = AppTextRole.Footnote,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (index != logs.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun ConfigToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    androidx.compose.material3.ListItem(
        trailingContent = {
            AppSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    ) {
        AppText(text = title)
    }
}
