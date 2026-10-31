package io.github.magisk317.relay.ui.record

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import io.github.magisk317.forward.engine.model.ReadRecordData
import io.github.magisk317.relay.mobile.core.R
import io.github.magisk317.uikit.surface.AppPrimaryButton
import io.github.magisk317.uikit.surface.AppSecondaryButton
import io.github.magisk317.uikit.surface.AppSurface
import io.github.magisk317.uikit.surface.WorkspaceListDivider
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.AppShapeRole
import io.github.magisk317.uikit.theme.appColor
import io.github.magisk317.uikit.theme.appShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RecordDetailOverlay(
    sms: ReadRecordData,
    packageLabels: Map<String, String>,
    onDismiss: () -> Unit,
    onCopy: (label: String, value: String, message: String) -> Unit,
    onDelete: () -> Unit,
    onRefund: () -> Unit,
) {
    val context = LocalContext.current
    val isAppNotification = sms.msgType == ReadRecordData.MSG_TYPE_APP_NOTIFY
    val detailDateFormatter = remember { SimpleDateFormat(RECORD_DATE_FORMAT, Locale.getDefault()) }
    val sender = sms.sender ?: sms.company ?: context.getString(R.string.unknown)
    val originalTime = formatDetailTime(detailDateFormatter, sms.date)
    val processedTime = formatDetailTime(detailDateFormatter, sms.processedTime)
    val content = sms.body.orEmpty()
    val forwardStatusAnnotated = resolveForwardStatusAnnotated(sms)
    val forwardTarget = sanitizeForwardTarget(sms.forwardTarget)
    val forwardTime = if (sms.forwardTime > 0L) detailDateFormatter.format(Date(sms.forwardTime)) else "-"
    val forwardMessageAnnotated = resolveForwardMessageAnnotated(sms.forwardMessage)
    val dismissInteraction = remember { MutableInteractionSource() }
    val detailTitleRes = if (isAppNotification) R.string.message_details_notification else R.string.message_details
    val deleteTextRes =
        if (isAppNotification) R.string.delete_notification_action else R.string.delete_sms_action
    val appDisplayName = sms.packageName?.takeIf { isAppNotification && it.isNotBlank() }?.let(packageLabels::get)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appColor(AppColorRole.Scrim).copy(alpha = 0.28f))
            .clickable(
                interactionSource = dismissInteraction,
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        AppSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
            shape = appShape(AppShapeRole.ExtraLarge),
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            color = appColor(AppColorRole.SurfaceContainerHigh),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        text = stringResource(detailTitleRes),
                        role = AppTextRole.Title,
                    )
                    AppText(
                        text = stringResource(R.string.detail_click_copy_hint),
                        role = AppTextRole.Footnote,
                        color = appColor(AppColorRole.OnSurfaceVariant),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                if (isAppNotification && !appDisplayName.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AppText(
                            text = "${stringResource(R.string.detail_app)}:",
                            role = AppTextRole.Body,
                        )
                        AppText(
                            text = appDisplayName,
                            role = AppTextRole.Body,
                            color = appColor(AppColorRole.Primary),
                            modifier = Modifier.clickable {
                                val message = context.getString(
                                    R.string.prompt_field_copied,
                                    context.getString(R.string.detail_app),
                                )
                                onCopy("app_name", appDisplayName, message)
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AppText(
                        text = "${stringResource(R.string.detail_sender)}:",
                        role = AppTextRole.Body,
                    )
                    AppText(
                        text = sender,
                        role = AppTextRole.Body,
                        color = appColor(AppColorRole.Primary),
                        modifier = Modifier.clickable {
                            val message = context.getString(
                                R.string.prompt_field_copied,
                                context.getString(R.string.detail_sender),
                            )
                            onCopy("sms_sender", sender, message)
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AppText(
                        text = "${stringResource(R.string.detail_original_time)}:",
                        role = AppTextRole.Body,
                    )
                    AppText(
                        text = originalTime,
                        role = AppTextRole.Body,
                        color = appColor(AppColorRole.Primary),
                        modifier = if (sms.date > 0L) {
                            Modifier.clickable {
                                val message = context.getString(
                                    R.string.prompt_field_copied,
                                    context.getString(R.string.detail_original_time),
                                )
                                onCopy("sms_original_time", originalTime, message)
                            }
                        } else {
                            Modifier
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AppText(
                        text = "${stringResource(R.string.detail_processed_time)}:",
                        role = AppTextRole.Body,
                    )
                    AppText(
                        text = processedTime,
                        role = AppTextRole.Body,
                        color = appColor(AppColorRole.Primary),
                        modifier = if (sms.processedTime > 0L) {
                            Modifier.clickable {
                                val message = context.getString(
                                    R.string.prompt_field_copied,
                                    context.getString(R.string.detail_processed_time),
                                )
                                onCopy("sms_processed_time", processedTime, message)
                            }
                        } else {
                            Modifier
                        },
                    )
                }
                AppText(
                    text = "${stringResource(R.string.detail_content)}:",
                    role = AppTextRole.Body,
                )
                AppText(
                    text = content,
                    role = AppTextRole.Body,
                    color = appColor(AppColorRole.Primary),
                    modifier = Modifier.clickable {
                        if (content.isNotEmpty()) {
                            val message = context.getString(
                                R.string.prompt_field_copied,
                                context.getString(R.string.detail_content),
                            )
                            onCopy("sms_body", content, message)
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AppText(
                        text = "${stringResource(R.string.detail_forward_status)}:",
                        role = AppTextRole.Body,
                    )
                    AppText(
                        text = forwardStatusAnnotated,
                        role = AppTextRole.Body,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AppText(
                        text = "${stringResource(R.string.detail_forward_target)}:",
                        role = AppTextRole.Body,
                    )
                    AppText(
                        text = forwardTarget,
                        role = AppTextRole.Body,
                        color = appColor(AppColorRole.Primary),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AppText(
                        text = "${stringResource(R.string.detail_forward_time)}:",
                        role = AppTextRole.Body,
                    )
                    AppText(
                        text = forwardTime,
                        role = AppTextRole.Body,
                        color = appColor(AppColorRole.Primary),
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AppText(
                        text = "${stringResource(R.string.detail_forward_message)}:",
                        role = AppTextRole.Body,
                    )
                    AppText(
                        text = forwardMessageAnnotated,
                        role = AppTextRole.Body,
                    )
                }
                }
                WorkspaceListDivider()
                val refundLabel = stringResource(if (isAppNotification) R.string.refund_notification else R.string.refund_sms)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppSecondaryButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onRefund()
                            onDismiss()
                        },
                    ) {
                        AppText(refundLabel)
                    }
                    AppPrimaryButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onDelete()
                            onDismiss()
                        },
                        containerColor = appColor(AppColorRole.ErrorContainer),
                        contentColor = appColor(AppColorRole.OnErrorContainer),
                    ) {
                        AppText(stringResource(deleteTextRes))
                    }
                }
            }
        }
    }
}

private fun formatDetailTime(
    formatter: SimpleDateFormat,
    timestamp: Long,
): String {
    return if (timestamp > 0L) {
        formatter.format(Date(timestamp))
    } else {
        "-"
    }
}

private fun sanitizeForwardTarget(rawTarget: String?): String {
    if (rawTarget.isNullOrBlank()) return "-"
    val channels = rawTarget.split("|")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { segment ->
            val channel = segment.substringBefore(":", segment).trim()
            if (channel.isEmpty()) segment else channel
        }
        .distinct()
    return if (channels.isEmpty()) "-" else channels.joinToString(" | ")
}

private data class ForwardCounts(val success: Int, val failed: Int)

@Composable
private fun parseForwardCountsFromMessage(rawMessage: String?): ForwardCounts {
    if (rawMessage.isNullOrBlank()) return ForwardCounts(success = 0, failed = 0)
    val successLabel = stringResource(R.string.forward_status_success)
    val failedLabel = stringResource(R.string.forward_status_failed)
    var success = 0
    var failed = 0
    rawMessage.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { line ->
            when {
                line.contains(successLabel) -> success++
                line.contains(failedLabel) -> failed++
            }
        }
    return ForwardCounts(success = success, failed = failed)
}

private fun countForwardTargets(rawTarget: String?): Int {
    if (rawTarget.isNullOrBlank()) return 0
    return rawTarget.split(",", "|")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .size
}

private fun formatForwardMessage(rawMessage: String?): String {
    if (rawMessage.isNullOrBlank()) return "-"
    val lines = rawMessage
        .replace(Regex("\\s*\\|\\s*"), "\n")
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()
    if (lines.isEmpty()) return "-"
    return lines.mapIndexed { index, line -> "${index + 1}. $line" }.joinToString("\n")
}

@Composable
private fun resolveForwardMessageAnnotated(rawMessage: String?): AnnotatedString {
    if (rawMessage.isNullOrBlank()) return AnnotatedString("-")
    val successLabel = stringResource(R.string.forward_status_success)
    val failedLabel = stringResource(R.string.forward_status_failed)
    val lines = rawMessage
        .replace(Regex("\\s*\\|\\s*"), "\n")
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()
    if (lines.isEmpty()) return AnnotatedString("-")
    return buildAnnotatedString {
        lines.forEachIndexed { index, line ->
            if (index > 0) append("\n")
            val color = when {
                line.contains(successLabel) -> FORWARD_SUCCESS_COLOR
                line.contains(failedLabel) -> FORWARD_FAILED_COLOR
                else -> appColor(AppColorRole.OnSurfaceVariant)
            }
            pushStyle(SpanStyle(color = color))
            append("${index + 1}. $line")
            pop()
        }
    }
}

private data class ForwardStatusSnapshot(
    val status: Int,
    val successCount: Int,
    val failedCount: Int,
)

@Composable
private fun resolveForwardStatusSnapshot(smsMsg: ReadRecordData): ForwardStatusSnapshot {
    val parsed = parseForwardCountsFromMessage(smsMsg.forwardMessage)
    val targetCount = countForwardTargets(smsMsg.forwardTarget)
    val statusForDisplay = when {
        parsed.success > 0 && parsed.failed > 0 -> ReadRecordData.FORWARD_STATUS_PARTIAL
        parsed.success > 0 -> ReadRecordData.FORWARD_STATUS_SUCCESS
        parsed.failed > 0 -> ReadRecordData.FORWARD_STATUS_FAILED
        smsMsg.forwardStatus == ReadRecordData.FORWARD_STATUS_FAILED &&
            targetCount == 0 &&
            !smsMsg.forwardMessage.isNullOrBlank() -> ReadRecordData.FORWARD_STATUS_NONE
        else -> smsMsg.forwardStatus
    }
    val successCount = when {
        parsed.success > 0 -> parsed.success
        statusForDisplay == ReadRecordData.FORWARD_STATUS_SUCCESS -> maxOf(targetCount, 1)
        else -> 0
    }
    val failedCount = when {
        parsed.failed > 0 -> parsed.failed
        statusForDisplay == ReadRecordData.FORWARD_STATUS_FAILED -> maxOf(targetCount, 1)
        else -> 0
    }
    return ForwardStatusSnapshot(
        status = statusForDisplay,
        successCount = successCount,
        failedCount = failedCount,
    )
}

@Composable
private fun resolveForwardStatusText(smsMsg: ReadRecordData): String {
    val snapshot = resolveForwardStatusSnapshot(smsMsg)
    return when (snapshot.status) {
        ReadRecordData.FORWARD_STATUS_BLOCKED -> stringResource(R.string.forward_status_none)
        ReadRecordData.FORWARD_STATUS_PARTIAL -> stringResource(
            R.string.forward_status_partial,
            maxOf(snapshot.successCount, 1),
            maxOf(snapshot.failedCount, 1),
        )
        ReadRecordData.FORWARD_STATUS_SUCCESS -> stringResource(
            R.string.forward_status_success_count,
            maxOf(snapshot.successCount, 1),
        )
        ReadRecordData.FORWARD_STATUS_FAILED -> stringResource(
            R.string.forward_status_failed_count,
            maxOf(snapshot.failedCount, 1),
        )
        else -> stringResource(R.string.forward_status_none)
    }
}

@Composable
private fun resolveForwardStatusColor(smsMsg: ReadRecordData): Color {
    return when (resolveForwardStatusSnapshot(smsMsg).status) {
        ReadRecordData.FORWARD_STATUS_SUCCESS -> FORWARD_SUCCESS_COLOR
        ReadRecordData.FORWARD_STATUS_FAILED -> FORWARD_FAILED_COLOR
        ReadRecordData.FORWARD_STATUS_PARTIAL,
        ReadRecordData.FORWARD_STATUS_BLOCKED,
        ReadRecordData.FORWARD_STATUS_NONE,
        -> FORWARD_WARNING_COLOR
        else -> FORWARD_WARNING_COLOR
    }
}

@Composable
internal fun resolveForwardStatusAnnotated(smsMsg: ReadRecordData): AnnotatedString {
    val snapshot = resolveForwardStatusSnapshot(smsMsg)
    return if (snapshot.status == ReadRecordData.FORWARD_STATUS_PARTIAL) {
        val successText = stringResource(
            R.string.forward_status_success_count,
            maxOf(snapshot.successCount, 1),
        )
        val failedText = stringResource(
            R.string.forward_status_failed_count,
            maxOf(snapshot.failedCount, 1),
        )
        buildAnnotatedString {
            pushStyle(SpanStyle(color = FORWARD_SUCCESS_COLOR))
            append(successText)
            pop()
            append(" ")
            pushStyle(SpanStyle(color = FORWARD_FAILED_COLOR))
            append(failedText)
            pop()
        }
    } else {
        val text = resolveForwardStatusText(smsMsg)
        val color = resolveForwardStatusColor(smsMsg)
        buildAnnotatedString {
            pushStyle(SpanStyle(color = color))
            append(text)
            pop()
        }
    }
}
