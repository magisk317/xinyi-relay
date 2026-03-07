package io.github.magisk317.relay.ui.home

import android.widget.Toast
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.data.db.entity.SmsMsg
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.Locale

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
                            Toast.makeText(
                                context,
                                context.getString(R.string.pref_sync_toast),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                    HorizontalDivider()
                    ConfigToggleRow(
                        title = stringResource(R.string.label_forwarding),
                        checked = app.forwarding,
                        onCheckedChange = {
                            viewModel.setForwarding(app.packageName, it)
                            Toast.makeText(
                                context,
                                context.getString(R.string.pref_sync_toast),
                                Toast.LENGTH_SHORT,
                            ).show()
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
                            Text(text = "应用关键词过滤")
                        },
                        supportingContent = {
                            Text(text = "配置应用级与通知渠道ID级别的关键词黑白名单")
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
                text = "近期转发日志",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (logs.isEmpty()) {
                Text(
                    text = "暂无日志",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            logs.forEachIndexed { index, log ->
                val statusText = when (log.forwardStatus) {
                    SmsMsg.FORWARD_STATUS_SUCCESS -> "成功"
                    SmsMsg.FORWARD_STATUS_FAILED -> "失败"
                    SmsMsg.FORWARD_STATUS_PARTIAL -> "部分成功"
                    SmsMsg.FORWARD_STATUS_BLOCKED -> "未转发"
                    else -> "未转发"
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "时间: ${dateFormat.format(java.util.Date(log.date))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "内容: ${log.body.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "状态: $statusText  目标: ${log.forwardTarget ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (!log.forwardMessage.isNullOrBlank()) {
                        Text(
                            text = "结果: ${log.forwardMessage}",
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
