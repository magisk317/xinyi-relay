package io.github.magisk317.relay.ui.record

import android.graphics.Bitmap
import io.github.magisk317.relay.ui.common.rememberBlacklistHitDateFormat
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.model.ReadSmsBlacklistHitData
import io.github.magisk317.relay.ui.common.AppIconBitmapImage
import io.github.magisk317.uikit.surface.WorkspaceListItem
import io.github.magisk317.uikit.surface.WorkspaceListItemDefaults
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val SMS_BLOCK_REASON_BLACKLIST = "blacklist_block"
internal const val SMS_BLOCK_REASON_PREF = "pref_block_sms"

@Composable
internal fun SmsBlacklistHitListItem(
    hit: ReadSmsBlacklistHitData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dateFormat: SimpleDateFormat = rememberBlacklistHitDateFormat(),
    defaultSmsIcon: Bitmap? = null,
) {
    val sourceText = blacklistHitSourceText(hit.source)
    val notSetText = stringResource(R.string.blacklist_not_set)
    val senderTitle = compactBlacklistHitSenderTitle(hit.sender, notSetText)
    val matchText = stringResource(
        R.string.sms_blacklist_hit_match,
        blacklistHitMatchTypeText(hit.matchType),
        hit.pattern.orEmpty().ifBlank { notSetText },
    )
    val actionText = blacklistHitActionText(hit.actionDelete, hit.actionBlock)
    val blockText = hit.blockReason?.let { reason ->
        stringResource(R.string.sms_blacklist_hit_block_reason, blacklistHitBlockReasonText(reason))
    }.orEmpty()
    val actionLine = stringResource(R.string.sms_blacklist_hit_actions, actionText)
    val body = hit.body.orEmpty()
    val status = buildString {
        append(matchText)
        append(" · ")
        append(actionLine)
        if (blockText.isNotBlank()) {
            append(" · ")
            append(blockText)
        }
        append(" · ")
        append(sourceText)
    }
    WorkspaceListItem(
        modifier = modifier,
        onClick = onClick,
        leadingWidth = WorkspaceListItemDefaults.iconColumnWidth,
        leadingContent = {
            AppIconBitmapImage(
                bitmap = defaultSmsIcon,
                size = WorkspaceListItemDefaults.iconSize,
                contentDescription = stringResource(R.string.sms_icon_description),
                fallbackIcon = Icons.Default.Email,
            )
        },
        supportingContent = {
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = senderTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )
            Text(
                text = dateFormat.format(Date(hit.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.End,
            )
        }
        if (body.isNotBlank()) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun compactBlacklistHitSenderTitle(sender: String?, fallback: String): String {
    val raw = sender?.takeIf { it.isNotBlank() } ?: fallback
    return raw.trim().take(8)
}

@Composable
internal fun blacklistHitSourceText(source: String): String {
    return when (source) {
        "dispatch_intent" -> stringResource(R.string.sms_blacklist_hit_source_dispatch_intent)
        "dispatch_chain" -> stringResource(R.string.sms_blacklist_hit_source_dispatch_chain)
        "mms" -> stringResource(R.string.sms_blacklist_hit_source_mms)
        else -> source
    }
}

@Composable
internal fun blacklistHitMatchTypeText(matchType: String?): String {
    return when (matchType) {
        "number" -> stringResource(R.string.sms_blacklist_hit_match_number)
        "prefix" -> stringResource(R.string.sms_blacklist_hit_match_prefix)
        "regex" -> stringResource(R.string.sms_blacklist_hit_match_regex)
        "content" -> stringResource(R.string.sms_blacklist_hit_match_content)
        else -> matchType.orEmpty().ifBlank { stringResource(R.string.blacklist_not_set) }
    }
}

@Composable
internal fun blacklistHitActionText(actionDelete: Boolean, actionBlock: Boolean): String {
    return when {
        actionDelete && actionBlock -> stringResource(R.string.sms_blacklist_hit_action_delete_and_block)
        actionDelete -> stringResource(R.string.sms_blacklist_hit_action_delete)
        actionBlock -> stringResource(R.string.sms_blacklist_hit_action_block)
        else -> stringResource(R.string.sms_blacklist_hit_action_none)
    }
}

@Composable
internal fun blacklistHitBlockReasonText(reason: String): String {
    return when (reason) {
        SMS_BLOCK_REASON_BLACKLIST -> stringResource(R.string.sms_blacklist_hit_block_reason_blacklist)
        SMS_BLOCK_REASON_PREF -> stringResource(R.string.sms_blacklist_hit_block_reason_pref)
        else -> reason
    }
}
