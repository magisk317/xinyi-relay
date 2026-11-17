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
internal fun MatrixE2eeVerificationSection(
    availability: MatrixE2eeAvailability,
    draft: SenderSettingDraft,
) {
    if (!availability.isAvailable) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val verification = MatrixE2eeVerificationProvider.get()
    val state by verification.state.collectAsState()
    val setting = remember(draft) {
        runCatching {
            SenderSettingJson.decode(MatrixSetting.serializer(), draft.toJson())
        }.getOrNull()
    }
    val credentialsReady = setting?.username?.isNotBlank() == true && setting.password.isNotBlank()
    DisposableEffect(verification) {
        // Refresh verification state when entering the page
        if (credentialsReady) {
            scope.launch {
                runCatching { verification.prepare(context, setting) }.onFailure {
                    // ignored here: no logger is reachable from this file and the failure has no remedy at this point
                }
            }
        }
        onDispose {
            // Clean up listeners and active sync, but avoid blowing away verified state if already established
            verification.stop()
        }
    }

    fun launchVerification(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        color = appColor(AppColorRole.SurfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(
                    imageVector = Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    tint = appColor(AppColorRole.Primary),
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                AppText(
                    text = stringResource(R.string.matrix_e2ee_verification_title),
                    role = AppTextRole.Subtitle,
                )
            }

            MatrixVerificationStateText(state = state)
            MatrixVerificationSas(state = state)

            if (!credentialsReady) {
                AppText(
                    text = stringResource(R.string.matrix_e2ee_verification_login_required),
                    role = AppTextRole.BodySmall,
                    color = appColor(AppColorRole.Error),
                )
            }

            MatrixVerificationActions(
                state = state,
                credentialsReady = credentialsReady,
                onPrepare = {
                    val currentSetting = setting ?: return@MatrixVerificationActions
                    launchVerification {
                        verification.prepare(context.applicationContext, currentSetting)
                    }
                },
                onRequest = {
                    val currentSetting = setting ?: return@MatrixVerificationActions
                    launchVerification {
                        verification.prepare(context.applicationContext, currentSetting)
                        verification.requestVerification()
                    }
                },
                onAccept = {
                    launchVerification { verification.acceptRequest() }
                },
                onStartSas = {
                    launchVerification { verification.startSas() }
                },
                onApprove = {
                    launchVerification { verification.approve() }
                },
                onDecline = {
                    launchVerification { verification.decline() }
                },
                onCancel = {
                    launchVerification { verification.cancel() }
                },
                onReset = {
                    verification.reset()
                },
                onRevokeDevice = {
                    val currentSetting = setting ?: return@MatrixVerificationActions
                    launchVerification { verification.revokeDevice(context.applicationContext, currentSetting) }
                },
            )
        }
    }
}

@Composable
private fun MatrixVerificationStateText(
    state: MatrixE2eeVerificationState,
) {
    if (state.userId.isNotBlank() || state.deviceId.isNotBlank()) {
        AppText(
            text = stringResource(
                R.string.matrix_e2ee_verification_device,
                state.userId.ifBlank { "-" },
                state.deviceId.ifBlank { "-" },
            ),
            role = AppTextRole.BodySmall,
        )
    }
    if (state.verificationState.isNotBlank()) {
        AppText(
            text = stringResource(
                R.string.matrix_e2ee_verification_state,
                matrixVerificationTrustStateText(state.verificationState),
            ),
            role = AppTextRole.BodySmall,
        )
    }
    if (state.status == MatrixE2eeVerificationStatus.REQUEST_RECEIVED) {
        AppText(
            text = stringResource(
                R.string.matrix_e2ee_verification_request_from,
                state.requestUserId.ifBlank { "-" },
                state.requestDeviceDisplayName.ifBlank { state.requestDeviceId.ifBlank { "-" } },
            ),
            role = AppTextRole.BodySmall,
        )
    }
    matrixVerificationStatusMessage(state)?.let { message ->
        AppText(
            text = message,
            role = AppTextRole.BodySmall,
            color = matrixVerificationStatusColor(state.status),
        )
    }
}

@Composable
private fun matrixVerificationTrustStateText(value: String): String {
    return when (value.uppercase()) {
        "VERIFIED" -> stringResource(R.string.matrix_e2ee_verification_trust_verified)
        "UNVERIFIED" -> stringResource(R.string.matrix_e2ee_verification_trust_unverified)
        "UNKNOWN" -> stringResource(R.string.matrix_e2ee_verification_trust_unknown)
        else -> value
    }
}

@Composable
private fun matrixVerificationStatusMessage(state: MatrixE2eeVerificationState): String? {
    if (state.status == MatrixE2eeVerificationStatus.FAILED && !state.message.isNullOrBlank()) {
        return stringResource(
            R.string.matrix_e2ee_verification_status_failed_with_reason,
            state.message.orEmpty(),
        )
    }
    val messageRes = when (state.status) {
        MatrixE2eeVerificationStatus.NOT_PREPARED -> null
        MatrixE2eeVerificationStatus.PREPARING -> R.string.matrix_e2ee_verification_status_preparing
        MatrixE2eeVerificationStatus.READY -> R.string.matrix_e2ee_verification_status_ready
        MatrixE2eeVerificationStatus.REQUESTING -> R.string.matrix_e2ee_verification_status_requesting
        MatrixE2eeVerificationStatus.REQUEST_SENT -> R.string.matrix_e2ee_verification_status_request_sent
        MatrixE2eeVerificationStatus.REQUEST_RECEIVED -> R.string.matrix_e2ee_verification_status_request_received
        MatrixE2eeVerificationStatus.ACCEPTED -> {
            if (state.requestUserId.isNotBlank()) {
                R.string.matrix_e2ee_verification_status_accepted_incoming
            } else {
                R.string.matrix_e2ee_verification_status_accepted
            }
        }
        MatrixE2eeVerificationStatus.SAS_STARTED -> R.string.matrix_e2ee_verification_status_sas_started
        MatrixE2eeVerificationStatus.SAS_READY -> R.string.matrix_e2ee_verification_status_sas_ready
        MatrixE2eeVerificationStatus.VERIFIED -> R.string.matrix_e2ee_verification_status_verified
        MatrixE2eeVerificationStatus.CANCELLED -> {
            val info = state.cancelInfo
            if (info != null && (info.reason.isNotBlank() || info.code.isNotBlank())) {
                return stringResource(
                    R.string.matrix_e2ee_verification_status_cancelled_with_reason,
                    stringResource(
                        if (info.cancelledByUs) {
                            R.string.matrix_e2ee_verification_cancelled_by_us
                        } else {
                            R.string.matrix_e2ee_verification_cancelled_by_them
                        },
                    ),
                    listOf(info.code, info.reason).filter { it.isNotBlank() }.joinToString(" - "),
                )
            }
            R.string.matrix_e2ee_verification_status_cancelled
        }
        MatrixE2eeVerificationStatus.FAILED -> R.string.matrix_e2ee_verification_status_failed
        MatrixE2eeVerificationStatus.UNAVAILABLE -> R.string.matrix_e2ee_verification_status_unavailable
        MatrixE2eeVerificationStatus.UNSUPPORTED_AUTH -> R.string.matrix_e2ee_verification_status_unsupported_auth
    }
    return messageRes?.let { stringResource(it) }
}

@Composable
private fun matrixVerificationStatusColor(
    status: MatrixE2eeVerificationStatus,
): Color {
    return when (status) {
        MatrixE2eeVerificationStatus.FAILED,
        MatrixE2eeVerificationStatus.UNAVAILABLE,
        MatrixE2eeVerificationStatus.UNSUPPORTED_AUTH -> appColor(AppColorRole.Error)
        MatrixE2eeVerificationStatus.VERIFIED -> appColor(AppColorRole.Primary)
        else -> appColor(AppColorRole.OnSurfaceVariant)
    }
}

@Composable
private fun MatrixVerificationSas(state: MatrixE2eeVerificationState) {
    if (state.status != MatrixE2eeVerificationStatus.SAS_READY) return
    if (state.sasDecimals.isNotEmpty()) {
        AppText(
            text = state.sasDecimals.joinToString(" "),
            role = AppTextRole.Subtitle,
        )
    }
    if (state.sasEmojis.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.sasEmojis.forEach { emoji ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppText(
                        text = emoji.symbol,
                        role = AppTextRole.Subtitle,
                        modifier = Modifier.width(40.dp),
                    )
                    AppText(
                        text = emoji.description,
                        role = AppTextRole.BodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun MatrixVerificationActions(
    state: MatrixE2eeVerificationState,
    credentialsReady: Boolean,
    onPrepare: () -> Unit,
    onRequest: () -> Unit,
    onAccept: () -> Unit,
    onStartSas: () -> Unit,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onRevokeDevice: () -> Unit,
) {
    val enabled = credentialsReady && !state.isBusy
    when (state.status) {
        MatrixE2eeVerificationStatus.NOT_PREPARED,
        MatrixE2eeVerificationStatus.FAILED,
        MatrixE2eeVerificationStatus.CANCELLED,
        MatrixE2eeVerificationStatus.UNSUPPORTED_AUTH -> {
            MatrixVerificationButton(
                text = stringResource(R.string.matrix_e2ee_verification_listen),
                icon = Icons.Filled.Refresh,
                enabled = enabled,
                onClick = onPrepare,
            )
            MatrixVerificationButton(
                text = stringResource(R.string.matrix_e2ee_verification_request),
                icon = Icons.Filled.VerifiedUser,
                enabled = enabled,
                onClick = onRequest,
            )
        }
        MatrixE2eeVerificationStatus.READY -> {
            MatrixVerificationButton(
                text = stringResource(R.string.matrix_e2ee_verification_request),
                icon = Icons.Filled.VerifiedUser,
                enabled = enabled,
                onClick = onRequest,
            )
            MatrixVerificationButton(
                text = stringResource(R.string.matrix_e2ee_verification_stop_listening),
                icon = Icons.Filled.Close,
                enabled = enabled,
                secondary = true,
                onClick = onReset,
            )
        }
        MatrixE2eeVerificationStatus.REQUEST_RECEIVED -> {
            MatrixVerificationButton(
                text = stringResource(R.string.matrix_e2ee_verification_accept),
                icon = Icons.Filled.Check,
                enabled = enabled,
                onClick = onAccept,
            )
            MatrixVerificationButton(
                text = stringResource(R.string.matrix_e2ee_verification_cancel),
                icon = Icons.Filled.Close,
                enabled = enabled,
                secondary = true,
                onClick = onCancel,
            )
        }
        MatrixE2eeVerificationStatus.REQUEST_SENT,
        MatrixE2eeVerificationStatus.SAS_STARTED -> {
            MatrixVerificationButton(
                text = stringResource(R.string.matrix_e2ee_verification_cancel),
                icon = Icons.Filled.Close,
                enabled = enabled,
                secondary = true,
                onClick = onCancel,
            )
        }
        MatrixE2eeVerificationStatus.ACCEPTED -> {
            if (state.requestUserId.isBlank()) {
                MatrixVerificationButton(
                    text = stringResource(R.string.matrix_e2ee_verification_start_sas),
                    icon = Icons.Filled.PlayArrow,
                    enabled = enabled,
                    onClick = onStartSas,
                )
            }
            MatrixVerificationButton(
                text = stringResource(R.string.matrix_e2ee_verification_cancel),
                icon = Icons.Filled.Close,
                enabled = enabled,
                secondary = true,
                onClick = onCancel,
            )
        }
        MatrixE2eeVerificationStatus.SAS_READY -> {
            MatrixVerificationButton(
                text = stringResource(R.string.matrix_e2ee_verification_confirm),
                icon = Icons.Filled.Check,
                enabled = enabled,
                onClick = onApprove,
            )
            MatrixVerificationButton(
                text = stringResource(R.string.matrix_e2ee_verification_decline),
                icon = Icons.Filled.Close,
                enabled = enabled,
                secondary = true,
                onClick = onDecline,
            )
        }
        MatrixE2eeVerificationStatus.PREPARING,
        MatrixE2eeVerificationStatus.REQUESTING,
        MatrixE2eeVerificationStatus.VERIFIED,
        MatrixE2eeVerificationStatus.UNAVAILABLE -> Unit
    }

    if (state.deviceId.isNotBlank() && !state.isBusy && state.verificationState == "VERIFIED") {
        MatrixVerificationButton(
            text = stringResource(R.string.matrix_e2ee_verification_revoke_device),
            icon = Icons.Filled.Close,
            enabled = enabled,
            isError = true,
            onClick = onRevokeDevice,
        )
    }
}

@Composable
private fun MatrixVerificationButton(
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    secondary: Boolean = false,
    isError: Boolean = false,
    onClick: () -> Unit,
) {
    if (secondary) {
        AppSecondaryButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            contentColor = if (isError) appColor(AppColorRole.Error) else Color.Unspecified,
        ) {
            MatrixVerificationButtonContent(text = text, icon = icon)
        }
    } else {
        AppPrimaryButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            containerColor = if (isError) appColor(AppColorRole.ErrorContainer) else Color.Unspecified,
            contentColor = if (isError) appColor(AppColorRole.OnErrorContainer) else Color.Unspecified,
        ) {
            MatrixVerificationButtonContent(text = text, icon = icon)
        }
    }
}

@Composable
private fun MatrixVerificationButtonContent(text: String, icon: ImageVector) {
    AppIcon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(modifier = Modifier.width(8.dp))
    AppText(text = text)
}

/**
 * Triggers E2EE module install via the availability interface.
 * On Play builds this calls PlayFeatureLoader.requestInstall() (SplitInstall).
 * On GitHub builds this calls GithubPluginFeatureLoader.requestInstall(), which
 * downloads the versioned plugin APK and DexClassLoader-loads it.
 * On builds without E2EE the default no-op implementation is used.
 */
