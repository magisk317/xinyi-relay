package io.github.magisk317.relay.ui.home.overview

import io.github.magisk317.uikit.common.showLatestSnackbar
import io.github.magisk317.uikit.surface.AppCard
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.surface.AppSurface
import io.github.magisk317.uikit.surface.MiuixStatusCheckCard
import io.github.magisk317.uikit.surface.rememberStatusCardClickHandler
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.appColor
import io.github.magisk317.uikit.theme.currentUiKitStyle

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.contract.constant.RelayAppConst as Const
import io.github.magisk317.relay.mobile.core.R
import io.github.magisk317.uikit.theme.AppShapeRole
import io.github.magisk317.uikit.theme.appShape

@Composable
fun StatusCard(
    isEnhancedModeEnabled: Boolean,
    isEntitled: Boolean = false,
    showEntitlement: Boolean = true,
    onActivateClick: (() -> Unit)? = null,
    onOpenDiagnostics: (() -> Unit)? = null,
) {
    val isWorking = isEnhancedModeEnabled
    val isAllOk = if (showEntitlement) isWorking && isEntitled else isWorking
    val moduleStatusText = when {
        isEnhancedModeEnabled -> stringResource(id = R.string.status_module_activated)
        else -> stringResource(id = R.string.status_module_not_activated)
    }
    val entitlementStatusText = if (!showEntitlement) {
        ""
    } else if (isEntitled) {
        stringResource(id = R.string.status_entitlement_verified)
    } else {
        stringResource(id = R.string.status_entitlement_unverified)
    }
    // Single tap keeps its activation role while the entitlement is missing; once entitled
    // the historical 5-tap gesture now opens the runtime diagnostics page. The inline
    // diagnostics block itself stays removed.
    val resolvedOnClick = rememberStatusCardClickHandler(
        isEntitled = if (showEntitlement) isEntitled else true,
        onActivateClick = onActivateClick.takeIf { showEntitlement },
        onDiagnosticsToggle = onOpenDiagnostics,
    )

    // Miuix hero card adopts the KernelSU-style oversized corner check mark shared via ui-kit
    // (MiPush OverviewMiuix lineage). The auth state (mobile automation entitlement) drives the
    // pass branch: entitled shows the check mark, otherwise the error mark.
    if (currentUiKitStyle() == UiKitStyle.Miuix) {
        MiuixStatusCheckCard(
            passed = if (showEntitlement) isEntitled else isWorking,
            title = if (showEntitlement) entitlementStatusText else moduleStatusText,
            badge = if (showEntitlement) moduleStatusText else "",
            summary = when {
                !isWorking -> stringResource(id = R.string.status_activate_hint)
                else -> null
            },
            onClick = { resolvedOnClick?.invoke() },
        )
        return
    }

    val containerColor = if (isAllOk) appColor(AppColorRole.Primary) else appColor(AppColorRole.ErrorContainer)
    val contentColor = if (isAllOk) appColor(AppColorRole.OnPrimary) else appColor(AppColorRole.OnErrorContainer)

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = appShape(AppShapeRole.ExtraLarge),
        color = containerColor,
        contentColor = contentColor,
        onClick = { resolvedOnClick?.invoke() },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AppIcon(
                    imageVector = if (isAllOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                Column {
                    AppText(
                        text = moduleStatusText,
                        role = AppTextRole.Subtitle,
                    )
                    if (showEntitlement) {
                        AppText(
                            text = entitlementStatusText,
                            role = AppTextRole.Subtitle,
                        )
                    }
                    if (!isWorking) {
                        AppText(
                            text = stringResource(id = R.string.status_activate_hint),
                            role = AppTextRole.Body,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InfoItem(icon: ImageVector, label: String, value: String, onClick: (() -> Unit)? = null) {
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(
                imageVector = icon,
                contentDescription = null,
                tint = appColor(AppColorRole.Primary),
            )
            AppText(
                text = label,
                role = AppTextRole.Body,
                color = appColor(AppColorRole.Outline),
                modifier = Modifier.weight(1f),
            )
            AppText(
                text = value,
                role = AppTextRole.Body,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
