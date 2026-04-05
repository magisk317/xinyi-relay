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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.smscode.runtime.common.utils.ClipboardUtils
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.diagnostics.RuntimeLogStore
import io.github.magisk317.relay.domain.model.MsgInfo
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

internal fun buildSenderTestMsgInfo(context: Context, senderName: String): MsgInfo {
    return MsgInfo(
        type = "sms",
        from = "10086",
        content = context.getString(R.string.sender_test_message_template, senderName),
        date = Date(),
        simInfo = "SIM1",
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
        append(context.getString(R.string.sender_test_log_channel))
        append(": ")
        append(channel)
        append('\n')
        append(context.getString(R.string.sender_test_log_exported_at))
        append(": ")
        append(now)
        append('\n')
        append("------------------------------")
        append('\n')
        append(if (mergedLogs.isNotBlank()) mergedLogs else context.getString(R.string.sender_test_log_empty))
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
                    logSenderTest(channel, context.getString(R.string.sender_test_started))
                    runCatching {
                        // Force sender test execution off the main thread.
                        withContext(Dispatchers.IO) { onSendTest() }
                    }
                        .onSuccess {
                            logSenderTest(channel, context.getString(R.string.sender_test_succeeded))
                            scope.launch {
                                showMessage(context.getString(R.string.sender_send_success))
                            }
                        }
                        .onFailure { error ->
                            val readable = error.toReadableError()
                            logSenderTest(
                                channel = channel,
                                message = context.getString(R.string.sender_test_failed, readable) + "\n${Log.getStackTraceString(error)}",
                                priority = Log.ERROR,
                            )
                            scope.launch {
                                showMessage(context.getString(R.string.sender_send_exception, readable))
                            }
                        }
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.sender_test_send))
        }

        OutlinedButton(
            onClick = {
                copySenderContextLog(context, channel)
                scope.launch {
                    showMessage(context.getString(R.string.sender_log_copied))
                }
            },
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 120.dp),
        ) {
            Text(stringResource(R.string.sender_copy_log))
        }
    }
}
