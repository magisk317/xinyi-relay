package io.github.magisk317.relay.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.magisk317.relay.android.diagnostics.RuntimeLogFileContent
import io.github.magisk317.relay.android.diagnostics.RuntimeLogFileInfo
import io.github.magisk317.relay.android.diagnostics.RuntimeLogFileSummary
import io.github.magisk317.relay.android.diagnostics.RuntimeLogStore
import io.github.magisk317.relay.core.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal data class RuntimeLogDialogData(
    val summary: RuntimeLogFileSummary,
    val selectedFileName: String?,
    val content: RuntimeLogFileContent?,
    val formattedPreview: String,
)

@Composable
internal fun RuntimeLogInfoDialog(
    data: RuntimeLogDialogData?,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSelectFile: (String) -> Unit,
    onOpenPreview: () -> Unit,
    onClear: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.runtime_log_viewer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (data == null) {
                    Text(text = stringResource(id = R.string.runtime_log_info_loading))
                    return@Column
                }
                val summary = data.summary
                if (summary.fileCount == 0) {
                    Text(text = stringResource(id = R.string.runtime_log_info_empty))
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = stringResource(
                                id = R.string.runtime_log_info_summary,
                                summary.fileCount,
                                formatLogSize(summary.totalBytes),
                                summary.entryCount,
                            ),
                            maxLines = 1,
                            softWrap = false,
                        )
                        val first = summary.firstTimestamp
                        val last = summary.lastTimestamp
                        if (first != null && last != null) {
                            Text(
                                text = stringResource(
                                    id = R.string.runtime_log_info_range,
                                    formatLogTimestamp(first),
                                    formatLogTimestamp(last),
                                ),
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        summary.files.forEach { file ->
                            val selected = file.name == data.selectedFileName
                            Text(
                                text = formatRuntimeLogFileListLine(file, selected),
                                modifier = Modifier
                                    .clickable { onSelectFile(file.name) }
                                    .padding(vertical = 2.dp),
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(id = R.string.runtime_log_info_preview_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = data.formattedPreview.ifBlank { stringResource(id = R.string.runtime_log_info_empty) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .clickable(enabled = data.content != null, onClick = onOpenPreview),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    softWrap = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onShare, enabled = data != null) {
                Text(text = stringResource(id = R.string.action_share))
            }
        },
        dismissButton = {
            TextButton(onClick = onClear, enabled = data != null) {
                Text(text = stringResource(id = R.string.action_clear))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RuntimeLogFullScreenPreviewDialog(
    fileName: String,
    text: String,
    wrapLines: Boolean,
    onWrapLinesChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(text = fileName, maxLines = 1, softWrap = false)
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(id = android.R.string.cancel),
                                )
                            }
                        },
                        actions = {
                            TextButton(onClick = { onWrapLinesChange(!wrapLines) }) {
                                Text(
                                    text = stringResource(
                                        id = if (wrapLines) {
                                            R.string.runtime_log_action_no_wrap
                                        } else {
                                            R.string.runtime_log_action_wrap
                                        },
                                    ),
                                )
                            }
                        },
                    )
                },
            ) { padding ->
                val vertical = rememberScrollState()
                val horizontal = rememberScrollState()
                Text(
                    text = text.ifBlank { stringResource(id = R.string.runtime_log_info_empty) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(12.dp)
                        .verticalScroll(vertical)
                        .then(if (wrapLines) Modifier else Modifier.horizontalScroll(horizontal)),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    softWrap = wrapLines,
                )
            }
        }
    }
}

internal fun loadRuntimeLogDialogData(selectedFileName: String? = null): RuntimeLogDialogData {
    val summary = runCatching {
        RuntimeLogStore.summarizeFiles()
    }.getOrElse {
        RuntimeLogFileSummary(
            fileCount = 0,
            totalBytes = 0L,
            entryCount = 0,
            firstTimestamp = null,
            lastTimestamp = null,
            files = emptyList(),
        )
    }
    val selected = selectRuntimeLogFile(summary, selectedFileName)
    val content = selected?.let { fileName ->
        runCatching { RuntimeLogStore.readLogFile(fileName) }.getOrNull()
    }
    val preview = content?.let { formatRuntimeLogContent(it.name, it.text) }.orEmpty()
    return RuntimeLogDialogData(
        summary = summary,
        selectedFileName = selected,
        content = content,
        formattedPreview = preview,
    )
}

private fun selectRuntimeLogFile(summary: RuntimeLogFileSummary, selectedFileName: String?): String? {
    val files = summary.files
    if (files.any { it.name == selectedFileName }) return selectedFileName
    return files.lastOrNull { it.name.matches(Regex("""runtime\.\d{4}-\d{2}-\d{2}\.jsonl""")) }?.name
        ?: files.lastOrNull()?.name
}

private fun formatRuntimeLogFileListLine(file: RuntimeLogFileInfo, selected: Boolean): String {
    val marker = if (selected) "*" else " "
    val lines = file.lineCount.toString().padStart(5)
    val size = formatLogSize(file.sizeBytes).padStart(8)
    val modified = file.lastTimestamp?.let(::formatLogTimestamp).orEmpty().padEnd(19)
    return "$marker -rw------- $lines $size $modified ${file.name}"
}

private fun formatRuntimeLogContent(fileName: String, text: String): String {
    if (!fileName.endsWith(".jsonl")) return text
    return text.lineSequence()
        .filter { it.isNotBlank() }
        .joinToString(separator = "\n\n") { line ->
            formatJsonLine(line)
        }
}

private fun formatJsonLine(line: String): String {
    return runCatching {
        when (val value = JSONTokener(line).nextValue()) {
            is JSONObject -> value.toString(2)
            is JSONArray -> value.toString(2)
            else -> line
        }
    }.getOrDefault(line)
}

private fun formatLogTimestamp(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

private fun formatLogSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
}
