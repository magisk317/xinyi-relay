@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.relay.ui.home

import io.github.magisk317.relay.ui.common.showLatestSnackbar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppConfigDetailScreen(
    packageName: String,
    onBack: () -> Unit,
    onConfigureNotifyChannels: () -> Unit,
    onConfigureForwardFilters: () -> Unit,
    viewModel: AppConfigViewModel = koinViewModel(),
) {
    val apps by viewModel.appsFlow.collectAsStateWithLifecycle()
    val app = apps.firstOrNull { it.packageName == packageName } ?: viewModel.getAppByPackageName(packageName)
    val appLogs by remember(packageName) { viewModel.appNotifyLogsFlow(packageName) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = app?.label ?: packageName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                windowInsets = WindowInsets.statusBars,
            )
        },
        snackbarHost = {
            io.github.magisk317.relay.ui.common.DismissibleSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { innerPadding ->
        if (app == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = stringResource(R.string.app_config_detail_not_found))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                        headlineContent = {
                            Text(text = stringResource(R.string.app_notify_channel_config_title))
                        },
                        supportingContent = {
                            val count = viewModel.getAppNotifyBindingCount(app.packageName)
                            Text(
                                text = if (count <= 0) {
                                    stringResource(R.string.app_notify_channel_global_summary)
                                } else {
                                    stringResource(R.string.app_notify_channel_bound_count, count)
                                },
                            )
                        },
                        trailingContent = {
                            TextButton(onClick = onConfigureNotifyChannels) {
                                Text(text = stringResource(R.string.item_config))
                            }
                        },
                    )
                    HorizontalDivider()
                    androidx.compose.material3.ListItem(
                        headlineContent = {
                            Text(text = stringResource(R.string.app_detail_forward_filter_title))
                        },
                        supportingContent = {
                            Text(text = stringResource(R.string.app_detail_forward_filter_summary))
                        },
                        trailingContent = {
                            TextButton(onClick = onConfigureForwardFilters) {
                                Text(text = stringResource(R.string.item_config))
                            }
                        },
                    )
                }
            }

            AppRecentLogCard(logs = appLogs)
        }
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
            Text(
                text = stringResource(R.string.app_detail_recent_logs_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (logs.isEmpty()) {
                Text(
                    text = stringResource(R.string.app_detail_recent_logs_empty),
                    style = MaterialTheme.typography.bodySmall,
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
                    Text(
                        text = stringResource(R.string.app_detail_recent_logs_time, dateFormat.format(java.util.Date(log.date))),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.app_detail_recent_logs_content, log.body.orEmpty()),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.app_detail_recent_logs_status, statusText, log.forwardTarget ?: "-"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (forwardMessage.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.app_detail_recent_logs_result, forwardMessage),
                            style = MaterialTheme.typography.labelSmall,
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
        headlineContent = {
            Text(text = title)
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}
