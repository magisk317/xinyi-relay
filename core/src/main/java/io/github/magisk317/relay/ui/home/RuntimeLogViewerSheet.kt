package io.github.magisk317.relay.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.magisk317.smscode.runtime.common.utils.ClipboardUtils
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.diagnostics.LogBundleExporter
import io.github.magisk317.relay.diagnostics.RuntimeLogEntry
import io.github.magisk317.relay.diagnostics.RuntimeLogStore
import io.github.magisk317.relay.ui.common.SegmentedOption
import io.github.magisk317.relay.ui.common.SingleChoiceSegmentedSelector
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuntimeLogViewerSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    var keyword by remember { mutableStateOf("") }
    var selectedMinutes by remember { mutableIntStateOf(5) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1200L)
            refreshTick += 1
        }
    }

    val entries = remember(keyword, selectedMinutes, refreshTick) {
        RuntimeLogStore.query(
            minutes = selectedMinutes.takeIf { it > 0 },
            keyword = keyword,
            limit = 800,
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.runtime_log_viewer_title), style = MaterialTheme.typography.titleLarge)
                Row {
                    IconButton(onClick = { refreshTick += 1 }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                LogBundleExporter.buildLogBundle(context)
                            }
                            val file = result.file
                            if (file == null) {
                                showMessage(context.getString(R.string.runtime_log_export_failed, result.details))
                                return@launch
                            }
                            runCatching {
                                LogBundleExporter.shareLogBundle(context, file)
                            }.onFailure {
                                showMessage(context.getString(R.string.runtime_log_share_failed, it.message ?: it.javaClass.simpleName))
                            }
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
                    }
                    IconButton(onClick = {
                        val text = RuntimeLogStore.exportText(
                            minutes = selectedMinutes.takeIf { it > 0 },
                            keyword = keyword,
                            limit = 800,
                        )
                        ClipboardUtils.copyToClipboard(context, text)
                        showMessage(context.getString(R.string.runtime_log_copied))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.action_copy))
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                LogBundleExporter.clearLogFolders(context)
                            }
                            refreshTick += 1
                            val messageText = if (result.success) {
                                context.getString(R.string.runtime_log_cleared)
                            } else {
                                context.getString(R.string.runtime_log_clear_partial_failed, result.details)
                            }
                            showMessage(messageText)
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_clear))
                    }
                }
            }

            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.runtime_log_keyword_search)) },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { keyword = "ForwardFlow" }) {
                    Text(stringResource(R.string.runtime_log_filter_forward_flow))
                }
                TextButton(onClick = { keyword = "WebhookUtils" }) {
                    Text(stringResource(R.string.runtime_log_filter_webhook))
                }
                TextButton(onClick = { keyword = "EmailUtils" }) {
                    Text(stringResource(R.string.runtime_log_filter_email))
                }
                TextButton(onClick = { keyword = "" }) {
                    Text(stringResource(R.string.runtime_log_filter_clear))
                }
            }

            SingleChoiceSegmentedSelector(
                options = listOf(
                    SegmentedOption(1, stringResource(R.string.runtime_log_window_1m)),
                    SegmentedOption(5, stringResource(R.string.runtime_log_window_5m)),
                    SegmentedOption(10, stringResource(R.string.runtime_log_window_10m)),
                    SegmentedOption(0, stringResource(R.string.runtime_log_window_all)),
                ),
                selected = selectedMinutes,
                onSelect = { selectedMinutes = it },
            )

            if (entries.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.runtime_log_empty_filtered),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.runtime_log_total_count, entries.size)) },
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(entries) { entry ->
                        RuntimeLogCard(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeLogCard(entry: RuntimeLogEntry) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val timeText = formatter.format(Date(entry.timestamp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "$timeText  ${priorityName(entry.priority)}  ${entry.tag}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private fun priorityName(priority: Int): String {
    return when (priority) {
        android.util.Log.VERBOSE -> "VERBOSE"
        android.util.Log.DEBUG -> "DEBUG"
        android.util.Log.INFO -> "INFO"
        android.util.Log.WARN -> "WARN"
        android.util.Log.ERROR -> "ERROR"
        android.util.Log.ASSERT -> "ASSERT"
        else -> priority.toString()
    }
}
