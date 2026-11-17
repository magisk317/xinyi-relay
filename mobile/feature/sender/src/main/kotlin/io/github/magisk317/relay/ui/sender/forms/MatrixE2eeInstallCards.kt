package io.github.magisk317.relay.ui.sender.forms


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.mobile.core.R
import io.github.magisk317.forward.engine.sender.SenderType
import io.github.magisk317.forward.sender.E2eeModuleStatus
import io.github.magisk317.forward.sender.MatrixE2eeAvailability
import io.github.magisk317.forward.sender.MatrixE2eeAvailabilityProvider
import io.github.magisk317.forward.sender.MatrixE2eeVerificationProvider
import io.github.magisk317.forward.sender.MatrixE2eeVerificationState
import io.github.magisk317.forward.sender.MatrixE2eeVerificationStatus
import io.github.magisk317.forward.sender.SenderSettingJson
import io.github.magisk317.forward.sender.SenderSettingDraft
import io.github.magisk317.forward.sender.config.MatrixSetting
import io.github.magisk317.relay.ui.sender.SenderViewModel
import io.github.magisk317.uikit.common.showLatestSnackbar
import io.github.magisk317.uikit.foundation.LocalSnackbarHostState
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppCard
import io.github.magisk317.uikit.surface.AppFilterChip
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.surface.AppLinearProgressIndicator
import io.github.magisk317.uikit.surface.AppPrimaryButton
import io.github.magisk317.uikit.surface.AppSecondaryButton
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor
import kotlinx.coroutines.launch

/** Stored value of [io.github.magisk317.forward.contract.model.ProxyType.DIRECT]. */

@Composable
internal fun MatrixE2eeStatusSection(
    availability: MatrixE2eeAvailability = MatrixE2eeAvailabilityProvider.get(),
    onInstalled: (() -> Unit)? = null,
    onUninstalled: (() -> Unit)? = null,
) {
    var currentStatus by remember { mutableStateOf(availability.status) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showUninstallDialog by remember { mutableStateOf(false) }

    when (currentStatus) {
        E2eeModuleStatus.AVAILABLE -> MatrixE2eeEnabledCard(
            onUninstallClick = { showUninstallDialog = true },
        )
        E2eeModuleStatus.NOT_APPLICABLE -> MatrixE2eeInfoBanner()
        E2eeModuleStatus.NOT_INSTALLED -> MatrixE2eeInstallCard(
            onInstallClick = {
                currentStatus = E2eeModuleStatus.DOWNLOADING
                downloadProgress = 0
                errorMessage = null
                triggerInstall(
                    availability = availability,
                    onProgress = { percent ->
                        downloadProgress = percent
                    },
                    onSuccess = {
                        currentStatus = E2eeModuleStatus.AVAILABLE
                        onInstalled?.invoke()
                    },
                    onFailure = { msg ->
                        errorMessage = msg
                        currentStatus = E2eeModuleStatus.INSTALL_FAILED
                    },
                )
            },
        )
        E2eeModuleStatus.DOWNLOADING -> MatrixE2eeDownloadingCard(
            progress = downloadProgress,
        )
        E2eeModuleStatus.UNINSTALLING -> MatrixE2eeUninstallingCard(
            errorMessage = errorMessage,
            onRetryClick = {
                errorMessage = null
                triggerUninstall(
                    availability = availability,
                    onSuccess = {
                        currentStatus = E2eeModuleStatus.NOT_INSTALLED
                        onUninstalled?.invoke()
                    },
                    onFailure = { msg ->
                        errorMessage = msg
                    },
                )
            },
        )
        E2eeModuleStatus.INSTALL_FAILED -> MatrixE2eeInstallFailedCard(
            errorMessage = errorMessage,
            onRetryClick = {
                currentStatus = E2eeModuleStatus.DOWNLOADING
                downloadProgress = 0
                errorMessage = null
                triggerInstall(
                    availability = availability,
                    onProgress = { percent ->
                        downloadProgress = percent
                    },
                    onSuccess = {
                        currentStatus = E2eeModuleStatus.AVAILABLE
                        onInstalled?.invoke()
                    },
                    onFailure = { msg ->
                        errorMessage = msg
                        currentStatus = E2eeModuleStatus.INSTALL_FAILED
                    },
                )
            },
        )
        E2eeModuleStatus.LOAD_FAILED -> MatrixE2eeLoadFailedCard()
    }

    if (showUninstallDialog) {
        MatrixE2eeUninstallDialog(
            onConfirm = {
                showUninstallDialog = false
                currentStatus = E2eeModuleStatus.UNINSTALLING
                errorMessage = null
                triggerUninstall(
                    availability = availability,
                    onSuccess = {
                        currentStatus = E2eeModuleStatus.NOT_INSTALLED
                        onUninstalled?.invoke()
                    },
                    onFailure = { msg ->
                        errorMessage = msg
                    },
                )
            },
            onDismiss = { showUninstallDialog = false },
        )
    }
}

@Composable
private fun MatrixE2eeEnabledCard(onUninstallClick: () -> Unit) {
    val containerColor = appColor(AppColorRole.PrimaryContainer)
    val contentColor = appColor(AppColorRole.OnPrimaryContainer)
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    AppText(
                        text = stringResource(R.string.matrix_e2ee_status_enabled),
                        role = AppTextRole.Subtitle,
                        color = contentColor,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    AppText(
                        text = stringResource(R.string.matrix_e2ee_status_enabled_desc),
                        role = AppTextRole.BodySmall,
                        color = contentColor,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            AppPrimaryButton(
                text = stringResource(R.string.matrix_e2ee_uninstall_button),
                onClick = onUninstallClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Confirms the uninstall: it deletes roughly 64 MB of plugin files and makes
 * encrypted rooms unsendable until the module is installed again.
 */
@Composable
private fun MatrixE2eeUninstallDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(text = stringResource(R.string.matrix_e2ee_uninstall_confirm_title)) },
        text = {
            AppText(
                text = stringResource(R.string.matrix_e2ee_uninstall_confirm_message),
                role = AppTextRole.BodySmall,
            )
        },
        confirmButton = {
            AppTextButton(
                text = stringResource(R.string.matrix_e2ee_uninstall_button),
                onClick = onConfirm,
                color = appColor(AppColorRole.Error),
            )
        },
        dismissButton = {
            AppTextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
            )
        },
    )
}

/**
 * Shown while the plugin files are being deleted. The GitHub plugin uninstall
 * finishes within the same screen visit; the Play DFM uninstall is deferred
 * until the app is backgrounded, which the note explains.
 */
@Composable
private fun MatrixE2eeUninstallingCard(errorMessage: String?, onRetryClick: () -> Unit) {
    val failed = errorMessage != null
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        color = if (failed) {
            appColor(AppColorRole.ErrorContainer)
        } else {
            appColor(AppColorRole.SecondaryContainer)
        },
        contentColor = if (failed) {
            appColor(AppColorRole.OnErrorContainer)
        } else {
            appColor(AppColorRole.OnSecondaryContainer)
        },
    ) {
        val contentColor = if (failed) {
            appColor(AppColorRole.OnErrorContainer)
        } else {
            appColor(AppColorRole.OnSecondaryContainer)
        }
        Column(modifier = Modifier.padding(16.dp)) {
            AppText(
                text = stringResource(R.string.matrix_e2ee_feature_title),
                role = AppTextRole.Subtitle,
                color = contentColor,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (failed) {
                AppText(
                    text = stringResource(
                        R.string.matrix_e2ee_uninstall_failed,
                        errorMessage,
                    ),
                    role = AppTextRole.BodySmall,
                    color = contentColor,
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppPrimaryButton(
                    text = stringResource(R.string.matrix_e2ee_uninstall_button),
                    onClick = onRetryClick,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = appColor(AppColorRole.Error),
                    contentColor = appColor(AppColorRole.OnError),
                )
            } else {
                AppLinearProgressIndicator(
                    progress = null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                AppText(
                    text = stringResource(R.string.matrix_e2ee_uninstalling),
                    role = AppTextRole.BodySmall,
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
private fun MatrixE2eeInfoBanner() {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        color = appColor(AppColorRole.PrimaryContainer),
        contentColor = appColor(AppColorRole.OnPrimaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AppText(
                text = stringResource(R.string.matrix_e2ee_banner_title),
                role = AppTextRole.Subtitle,
                color = appColor(AppColorRole.OnPrimaryContainer),
            )
            Spacer(modifier = Modifier.height(4.dp))
            AppText(
                text = stringResource(R.string.matrix_e2ee_banner_message),
                role = AppTextRole.BodySmall,
                color = appColor(AppColorRole.OnPrimaryContainer),
            )
        }
    }
}

@Composable
private fun MatrixE2eeInstallCard(onInstallClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        color = appColor(AppColorRole.SecondaryContainer),
        contentColor = appColor(AppColorRole.OnSecondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AppText(
                text = stringResource(R.string.matrix_e2ee_feature_title),
                role = AppTextRole.Subtitle,
                color = appColor(AppColorRole.OnSecondaryContainer),
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppPrimaryButton(
                text = stringResource(R.string.matrix_e2ee_install_button),
                onClick = onInstallClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MatrixE2eeDownloadingCard(progress: Int) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        color = appColor(AppColorRole.SecondaryContainer),
        contentColor = appColor(AppColorRole.OnSecondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AppText(
                text = stringResource(R.string.matrix_e2ee_feature_title),
                role = AppTextRole.Subtitle,
                color = appColor(AppColorRole.OnSecondaryContainer),
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppLinearProgressIndicator(
                progress = progress / 100f,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            AppText(
                text = stringResource(R.string.matrix_e2ee_downloading, progress),
                role = AppTextRole.BodySmall,
                color = appColor(AppColorRole.OnSecondaryContainer),
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppPrimaryButton(
                text = stringResource(R.string.matrix_e2ee_install_button),
                onClick = { /* disabled during download */ },
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
            )
        }
    }
}

@Composable
private fun MatrixE2eeInstallFailedCard(errorMessage: String?, onRetryClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        color = appColor(AppColorRole.ErrorContainer),
        contentColor = appColor(AppColorRole.OnErrorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AppText(
                text = stringResource(R.string.matrix_e2ee_feature_title),
                role = AppTextRole.Subtitle,
                color = appColor(AppColorRole.OnErrorContainer),
            )
            Spacer(modifier = Modifier.height(4.dp))
            AppText(
                text = stringResource(
                    R.string.matrix_e2ee_install_failed,
                    errorMessage ?: stringResource(R.string.matrix_e2ee_unknown_error),
                ),
                role = AppTextRole.BodySmall,
                color = appColor(AppColorRole.OnErrorContainer),
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppPrimaryButton(
                text = stringResource(R.string.matrix_e2ee_install_button),
                onClick = onRetryClick,
                modifier = Modifier.fillMaxWidth(),
                containerColor = appColor(AppColorRole.Error),
                contentColor = appColor(AppColorRole.OnError),
            )
        }
    }
}

@Composable
private fun MatrixE2eeLoadFailedCard() {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        color = appColor(AppColorRole.ErrorContainer),
        contentColor = appColor(AppColorRole.OnErrorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AppText(
                text = stringResource(R.string.matrix_e2ee_feature_title),
                role = AppTextRole.Subtitle,
                color = appColor(AppColorRole.OnErrorContainer),
            )
            Spacer(modifier = Modifier.height(4.dp))
            AppText(
                text = stringResource(
                    R.string.matrix_e2ee_install_failed,
                    stringResource(R.string.matrix_e2ee_load_failed),
                ),
                role = AppTextRole.BodySmall,
                color = appColor(AppColorRole.OnErrorContainer),
            )
        }
    }
}

private fun triggerInstall(
    availability: MatrixE2eeAvailability,
    onProgress: (Int) -> Unit,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
) {
    availability.requestInstall(
        onProgress = onProgress,
        onSuccess = onSuccess,
        onFailure = onFailure,
    )
}

/**
 * Triggers E2EE module uninstall via the availability interface.
 * On Play builds this calls PlayFeatureLoader.requestUninstall() (deferred
 * SplitInstall uninstall). On GitHub builds this calls
 * GithubPluginFeatureLoader.requestUninstall(), which deletes the plugin APK
 * and its caches and restores the plaintext stubs.
 * On builds without E2EE the default no-op implementation is used.
 */
private fun triggerUninstall(
    availability: MatrixE2eeAvailability,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
) {
    availability.requestUninstall(
        onSuccess = onSuccess,
        onFailure = onFailure,
    )
}
