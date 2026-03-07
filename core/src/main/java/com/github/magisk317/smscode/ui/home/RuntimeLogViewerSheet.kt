package com.github.magisk317.smscode.ui.home

import android.widget.Toast
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.github.magisk317.smscode.common.utils.ClipboardUtils
import com.github.magisk317.smscode.common.utils.LogBundleExporter
import com.github.magisk317.smscode.common.utils.RuntimeLogEntry
import com.github.magisk317.smscode.common.utils.RuntimeLogStore
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
                Text("详细日志", style = MaterialTheme.typography.titleLarge)
                Row {
                    IconButton(onClick = { refreshTick += 1 }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                LogBundleExporter.buildLogBundle(context)
                            }
                            val file = result.file
                            if (file == null) {
                                Toast.makeText(context, "导出失败: ${result.details}", Toast.LENGTH_LONG).show()
                                return@launch
                            }
                            runCatching {
                                LogBundleExporter.shareLogBundle(context, file)
                            }.onFailure {
                                Toast.makeText(context, "分享失败: ${it.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = {
                        val text = RuntimeLogStore.exportText(
                            minutes = selectedMinutes.takeIf { it > 0 },
                            keyword = keyword,
                            limit = 800,
                        )
                        ClipboardUtils.copyToClipboard(context, text)
                        Toast.makeText(context, "日志已复制", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                LogBundleExporter.clearLogFolders(context)
                            }
                            refreshTick += 1
                            val toastText = if (result.success) {
                                "日志与崩溃文件已清空"
                            } else {
                                "部分清空失败: ${result.details}"
                            }
                            Toast.makeText(context, toastText, Toast.LENGTH_LONG).show()
                        }
                    }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear")
                    }
                }
            }

            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("关键字搜索") },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { keyword = "ForwardFlow" }) {
                    Text("转发链路")
                }
                TextButton(onClick = { keyword = "WebhookUtils" }) {
                    Text("Webhook")
                }
                TextButton(onClick = { keyword = "EmailUtils" }) {
                    Text("Email")
                }
                TextButton(onClick = { keyword = "" }) {
                    Text("清空筛选")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TimeFilterChip(label = "近1分钟", selected = selectedMinutes == 1) { selectedMinutes = 1 }
                TimeFilterChip(label = "近5分钟", selected = selectedMinutes == 5) { selectedMinutes = 5 }
                TimeFilterChip(label = "近10分钟", selected = selectedMinutes == 10) { selectedMinutes = 10 }
                TimeFilterChip(label = "全部", selected = selectedMinutes == 0) { selectedMinutes = 0 }
            }

            if (entries.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Text(
                        text = "当前筛选条件下没有日志",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                AssistChip(
                    onClick = {},
                    label = { Text("共 ${entries.size} 条") },
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

@Composable
private fun TimeFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
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
