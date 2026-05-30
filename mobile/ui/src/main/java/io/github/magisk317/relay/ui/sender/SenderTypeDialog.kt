package io.github.magisk317.relay.ui.sender

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.mobileui.BuildConfig

@Suppress("MagicNumber")
@Composable
internal fun SenderTypeDialog(
    onDismiss: () -> Unit,
    onAddClick: (Int) -> Unit,
) {
    val context = LocalContext.current
    val otherChannels = mutableListOf(
        SenderType.EMAIL to getSenderTypeName(context, SenderType.EMAIL),
        SenderType.URL_SCHEME to getSenderTypeName(context, SenderType.URL_SCHEME),
        SenderType.SOCKET to getSenderTypeName(context, SenderType.SOCKET),
    )
    if (BuildConfig.ENABLE_SMS_CHANNEL) {
        otherChannels.add(1, SenderType.SMS to getSenderTypeName(context, SenderType.SMS))
    }
    val supportedTypeGroups = listOf(
        senderTypeGroupLabel(context, "collaboration") to listOf(
            SenderType.DINGTALK_GROUP_ROBOT to getSenderTypeName(context, SenderType.DINGTALK_GROUP_ROBOT),
            SenderType.DINGTALK_INNER_ROBOT to getSenderTypeName(context, SenderType.DINGTALK_INNER_ROBOT),
            SenderType.FEISHU to getSenderTypeName(context, SenderType.FEISHU),
            SenderType.FEISHU_APP to getSenderTypeName(context, SenderType.FEISHU_APP),
            SenderType.WEWORK_ROBOT to getSenderTypeName(context, SenderType.WEWORK_ROBOT),
            SenderType.WEWORK_AGENT to getSenderTypeName(context, SenderType.WEWORK_AGENT),
        ),
        senderTypeGroupLabel(context, "push") to listOf(
            SenderType.TELEGRAM to getSenderTypeName(context, SenderType.TELEGRAM),
            SenderType.WEBHOOK to getSenderTypeName(context, SenderType.WEBHOOK),
            SenderType.SERVERCHAN to getSenderTypeName(context, SenderType.SERVERCHAN),
            SenderType.PUSHPLUS to getSenderTypeName(context, SenderType.PUSHPLUS),
            SenderType.GOTIFY to getSenderTypeName(context, SenderType.GOTIFY),
            SenderType.NTFY to getSenderTypeName(context, SenderType.NTFY),
            SenderType.BARK to getSenderTypeName(context, SenderType.BARK),
            SenderType.YUNHU to getSenderTypeName(context, SenderType.YUNHU),
        ),
        senderTypeGroupLabel(context, "other") to listOf(
            *otherChannels.toTypedArray(),
        ),
    )

    AlertDialog(
        modifier = Modifier.fillMaxWidth(0.88f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sender_add_type_title)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                supportedTypeGroups.forEach { (groupName, groupItems) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = groupName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                        )
                    }
                    groupItems.forEach { (type, name) ->
                        item {
                            Button(
                                onClick = { onAddClick(type) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
