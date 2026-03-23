package io.github.magisk317.relay.ui.sender.forms

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.common.utils.ClipboardUtils
import io.github.magisk317.relay.diagnostics.RuntimeLogStore
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun logSenderTest(channel: String, message: String, priority: Int = Log.INFO) {
    RuntimeLogStore.append(
        priority = priority,
        tag = "SenderTest-$channel",
        message = message,
        force = true,
        route = RuntimeLogStore.ROUTE_SENDER,
    )
}

internal fun copySenderContextLog(context: Context, channel: String) {
    val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    val channelKeyword = "SenderTest-$channel"
    val channelLogs = RuntimeLogStore.exportText(minutes = 10, keyword = channelKeyword, limit = 500)
    val extraKeywords = channelExtraLogKeywords(channel)
    val extraLogSections = extraKeywords.mapNotNull { keyword ->
        val logs = RuntimeLogStore.exportText(minutes = 10, keyword = keyword, limit = 500)
        if (logs.isBlank()) null else "[$keyword]\n$logs"
    }
    val mergedLogs = buildString {
        if (channelLogs.isNotBlank()) {
            append(channelLogs)
        }
        if (extraLogSections.isNotEmpty()) {
            if (isNotEmpty()) append("\n\n")
            append(extraLogSections.joinToString(separator = "\n\n"))
        }
    }
    val payload = buildString {
        append("渠道: ")
        append(channel)
        append('\n')
        append("导出时间: ")
        append(now)
        append('\n')
        append("------------------------------")
        append('\n')
        append(if (mergedLogs.isNotBlank()) mergedLogs else "暂无发送测试相关日志")
    }
    ClipboardUtils.copyToClipboard(context, payload)
}

private fun channelExtraLogKeywords(channel: String): List<String> {
    return when (channel.trim().uppercase(Locale.ROOT)) {
        "WEBHOOK" -> listOf("WebhookUtils")
        "NTFY" -> listOf("NtfyUtils")
        else -> emptyList()
    }
}

private fun Throwable.toReadableError(): String {
    val detail = message?.takeIf { it.isNotBlank() } ?: "no message"
    return "${javaClass.simpleName}: $detail"
}

@Composable
internal fun SenderTestActionRow(
    channel: String,
    onSendTest: suspend () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = {
                scope.launch {
                    logSenderTest(channel, "开始发送测试")
                    runCatching {
                        // Force sender test execution off the main thread.
                        withContext(Dispatchers.IO) { onSendTest() }
                    }
                        .onSuccess {
                            logSenderTest(channel, "发送测试成功")
                            scope.launch {
                                showMessage("发送成功")
                            }
                        }
                        .onFailure { error ->
                            val readable = error.toReadableError()
                            logSenderTest(
                                channel = channel,
                                message = "发送测试失败: $readable\n${Log.getStackTraceString(error)}",
                                priority = Log.ERROR,
                            )
                            scope.launch {
                                showMessage("异常: $readable")
                            }
                        }
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            Text("发送测试")
        }

        OutlinedButton(
            onClick = {
                copySenderContextLog(context, channel)
                scope.launch {
                    showMessage("上下文日志已复制")
                }
            },
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 120.dp),
        ) {
            Text("复制日志")
        }
    }
}
