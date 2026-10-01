package io.github.magisk317.relay.ui.sender

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.uikit.preference.AppSwitch
import io.github.magisk317.uikit.surface.AppCard
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.surface.AppIconButton
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun SenderCard(
    sender: Sender,
    displayPriority: Int,
    dragModifier: Modifier,
    onEdit: () -> Unit,
    onPriorityClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = sender.name.ifEmpty { getSenderTypeName(context, sender.type) },
                    role = AppTextRole.Subtitle,
                    modifier = Modifier.weight(1f),
                )
                AppIconButton(
                    modifier = dragModifier,
                    onClick = {},
                ) {
                    AppIcon(
                        imageVector = Icons.Filled.DragHandle,
                        contentDescription = stringResource(R.string.sender_priority_drag_handle),
                    )
                }
                AppSwitch(
                    checked = sender.status == 1,
                    onCheckedChange = { onToggle(it) },
                )
            }
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            AppText(
                text = stringResource(
                    R.string.sender_type_line,
                    getSenderTypeName(context, sender.type),
                    sdf.format(sender.time),
                ),
                role = AppTextRole.BodySmall,
                color = appColor(AppColorRole.OnSurfaceVariant),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTextButton(
                    text = stringResource(R.string.sender_priority_value, displayPriority),
                    onClick = onPriorityClick,
                )
                AppTextButton(
                    text = stringResource(R.string.action_delete),
                    onClick = onDelete,
                    color = appColor(AppColorRole.Error),
                )
            }
        }
    }
}
