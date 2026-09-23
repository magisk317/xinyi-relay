package io.github.magisk317.relay.ui.home.appconfig

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.magisk317.relay.core.R
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppPrimaryButton
import io.github.magisk317.uikit.surface.AppSecondaryButton
import kotlinx.coroutines.launch

/**
 * Dual-channel dialog for the usage access permission.
 *
 * On rooted devices the primary button performs a one-tap root grant; otherwise it
 * opens the system usage-access settings page. A failed root grant keeps the dialog
 * open and shows the failure hint so the user can retry or grant manually.
 *
 * @param packageName package whose usage access should be granted.
 * @param onGrant suspending callback that runs the root grant; returns true on success.
 * @param onDismiss dismisses the dialog.
 */
@Composable
fun UsageStatsPermissionDialog(
    packageName: String,
    onGrant: suspend (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    val granter = remember { UsageStatsPermissionGranter() }
    var rootAvailable by remember { mutableStateOf(false) }
    var granting by remember { mutableStateOf(false) }
    var grantFailed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        rootAvailable = granter.canGrantWithRoot()
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val openUsageAccessSettings: () -> Unit = {
        try {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (_: Exception) {
        }
    }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.usage_permission_title)) },
        text = {
            Column {
                Text(stringResource(R.string.usage_permission_prompt))
                if (grantFailed) {
                    Text(stringResource(R.string.usage_permission_grant_failed))
                }
            }
        },
        confirmButton = {
            if (rootAvailable) {
                AppPrimaryButton(
                    text = if (granting) {
                        stringResource(R.string.usage_permission_action_granting)
                    } else {
                        stringResource(R.string.usage_permission_action_grant)
                    },
                    enabled = !granting,
                    onClick = {
                        granting = true
                        grantFailed = false
                        scope.launch {
                            val granted = onGrant(packageName)
                            granting = false
                            if (granted) {
                                onDismiss()
                            } else {
                                grantFailed = true
                            }
                        }
                    },
                )
            } else {
                AppPrimaryButton(
                    text = stringResource(R.string.usage_permission_action_open_settings),
                    onClick = openUsageAccessSettings,
                )
            }
        },
        dismissButton = {
            if (rootAvailable) {
                AppSecondaryButton(
                    text = stringResource(R.string.usage_permission_action_settings),
                    onClick = openUsageAccessSettings,
                )
            } else {
                AppSecondaryButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                )
            }
        },
    )
}
