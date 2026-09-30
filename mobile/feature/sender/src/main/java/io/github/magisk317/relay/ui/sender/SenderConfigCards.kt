package io.github.magisk317.relay.ui.sender

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.uikit.preference.AppSwitch
import io.github.magisk317.uikit.surface.AppCard
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor

@Composable
internal fun GeneralConfigCard(
    modifier: Modifier = Modifier,
    deviceName: String,
    simSlot1Remark: String,
    simSlot2Remark: String,
    onEdit: () -> Unit,
) {
    AppCard(
        onClick = onEdit,
        modifier = modifier,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppText(
                text = stringResource(R.string.sender_general_config_title),
                role = AppTextRole.Subtitle,
                maxLines = 1,
            )
            AppText(
                text = stringResource(
                    R.string.sender_general_config_summary,
                    deviceName.ifBlank { stringResource(R.string.sender_system_default) },
                    simSlot1Remark.ifBlank { stringResource(R.string.sender_not_set) },
                    simSlot2Remark.ifBlank { stringResource(R.string.sender_not_set) },
                ),
                role = AppTextRole.Body,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun SmsConfigCard(
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
) {
    AppCard(
        onClick = onEdit,
        modifier = modifier,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppText(
                text = stringResource(R.string.sender_sms_config_title),
                role = AppTextRole.Subtitle,
                maxLines = 1,
            )
            AppText(
                text = stringResource(R.string.sender_config_card_summary),
                role = AppTextRole.BodySmall,
                color = appColor(AppColorRole.OnSurfaceVariant),
            )
        }
    }
}

@Composable
internal fun AppNotifyConfigCard(
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
) {
    AppCard(
        onClick = onEdit,
        modifier = modifier,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppText(
                text = stringResource(R.string.sender_app_config_title),
                role = AppTextRole.Subtitle,
                maxLines = 1,
            )
            AppText(
                text = stringResource(R.string.sender_config_card_summary),
                role = AppTextRole.BodySmall,
                color = appColor(AppColorRole.OnSurfaceVariant),
            )
        }
    }
}

@Composable
internal fun CallNotifyConfigCard(
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
) {
    AppCard(
        onClick = onEdit,
        modifier = modifier,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppText(
                text = stringResource(R.string.sender_call_config_title),
                role = AppTextRole.Subtitle,
                maxLines = 1,
            )
            AppText(
                text = stringResource(R.string.sender_config_card_summary),
                role = AppTextRole.BodySmall,
                color = appColor(AppColorRole.OnSurfaceVariant),
            )
        }
    }
}

@Composable
internal fun ConfigGateToggle(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AppText(text = title, role = AppTextRole.Body)
            AppText(text = summary, role = AppTextRole.BodySmall)
        }
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
