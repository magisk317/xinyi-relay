package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.sender.E2eeModuleStatus
import io.github.magisk317.relay.sender.MatrixE2eeAvailability
import io.github.magisk317.relay.sender.MatrixE2eeAvailabilityProvider
import io.github.magisk317.relay.sender.MatrixE2eeVerificationProvider
import io.github.magisk317.relay.sender.MatrixE2eeVerificationState
import io.github.magisk317.relay.sender.MatrixE2eeVerificationStatus
import io.github.magisk317.relay.sender.SenderSettingJson
import io.github.magisk317.relay.sender.SenderSettingDraft
import io.github.magisk317.relay.sender.config.MatrixSetting
import io.github.magisk317.relay.ui.sender.SenderViewModel
import kotlinx.coroutines.launch

private val MatrixVisibleFields = listOf(
    SchemaSenderFormFieldSpec(
        name = "homeserver",
        labelRes = R.string.sender_form_label_matrix_homeserver_required,
        supportingTextRes = R.string.sender_form_label_matrix_homeserver_example,
    ),
    SchemaSenderFormFieldSpec(
        name = "username",
        labelRes = R.string.sender_form_label_matrix_username_required,
        supportingTextRes = R.string.sender_form_label_matrix_username_example,
    ),
    SchemaSenderFormFieldSpec(
        name = "password",
        labelRes = R.string.sender_form_label_matrix_password_required,
        isSecret = true,
    ),
    SchemaSenderFormFieldSpec(
        name = "roomId",
        labelRes = R.string.sender_form_label_matrix_room_id_required,
        placeholderRes = R.string.sender_form_label_matrix_room_id_share_link_hint,
        supportingTextRes = R.string.sender_form_label_matrix_room_id_share_link_hint,
    ),
    SchemaSenderFormFieldSpec(
        name = "messageType",
        labelRes = R.string.sender_form_label_message_type,
        optionLabelRes = MessageTypeOptionLabels,
    ),
    SchemaSenderFormFieldSpec(
        name = "titleTemplate",
        labelRes = R.string.sender_form_title_template_label,
        placeholderRes = R.string.sender_form_title_template_placeholder,
    ),
    SchemaSenderFormFieldSpec(
        name = "proxyType",
        labelRes = R.string.sender_form_label_proxy_type,
    ),
    SchemaSenderFormFieldSpec(
        name = "proxyHost",
        labelRes = R.string.sender_form_label_proxy_host,
    ),
    SchemaSenderFormFieldSpec(
        name = "proxyPort",
        labelRes = R.string.sender_form_label_proxy_port,
    ),
    SchemaSenderFormFieldSpec(
        name = "proxyAuthenticator",
        labelRes = R.string.sender_form_label_proxy_authenticator,
    ),
    SchemaSenderFormFieldSpec(
        name = "proxyUsername",
        labelRes = R.string.sender_form_label_proxy_username,
    ),
    SchemaSenderFormFieldSpec(
        name = "proxyPassword",
        labelRes = R.string.sender_form_label_proxy_password,
    ),
)

@Composable
fun MatrixConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    var e2eeInstallGeneration by remember { mutableIntStateOf(0) }

    SchemaSenderConfigForm(
        senderId = senderId,
        senderType = SenderType.MATRIX,
        channel = "Matrix",
        fields = MatrixVisibleFields,
        onBack = onBack,
        viewModel = viewModel,
        normalizeDraft = ::matrixVisibleDraft,
        extraContent = { draft, _ ->
            MatrixE2eeStatusSection(
                availability = MatrixE2eeAvailabilityProvider.get(),
                onInstalled = {
                    e2eeInstallGeneration++
                },
            )
            e2eeInstallGeneration
            MatrixE2eeVerificationSection(
                availability = MatrixE2eeAvailabilityProvider.get(),
                draft = draft,
            )
        },
    )
}

/**
 * Displays the appropriate E2EE status indicator based on module availability:
 * - AVAILABLE → green "E2EE Enabled" card
 * - NOT_APPLICABLE (GitHub noE2ee) → info banner suggesting E2EE variant
 * - NOT_INSTALLED (Play) → install button
 * - DOWNLOADING → progress bar with percentage
 * - INSTALL_FAILED → error message + retry button
 * - LOAD_FAILED → error message
 */
@Composable
internal fun MatrixE2eeStatusSection(
    availability: MatrixE2eeAvailability = MatrixE2eeAvailabilityProvider.get(),
    onInstalled: (() -> Unit)? = null,
) {
    var currentStatus by remember { mutableStateOf(availability.status) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    when (currentStatus) {
        E2eeModuleStatus.AVAILABLE -> MatrixE2eeEnabledCard()
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
}

@Composable
private fun MatrixE2eeEnabledCard() {
    val greenContainer = Color(0xFFD7F5E3)
    val greenContent = Color(0xFF1B5E20)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = greenContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = greenContent,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.matrix_e2ee_status_enabled),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = greenContent,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.matrix_e2ee_status_enabled_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = greenContent,
                )
            }
        }
    }
}

@Composable
private fun MatrixE2eeInfoBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.matrix_e2ee_banner_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.matrix_e2ee_banner_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun MatrixE2eeInstallCard(onInstallClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.matrix_e2ee_feature_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onInstallClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.matrix_e2ee_install_button))
            }
        }
    }
}

@Composable
private fun MatrixE2eeDownloadingCard(progress: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.matrix_e2ee_feature_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.matrix_e2ee_downloading, progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { /* disabled during download */ },
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
            ) {
                Text(text = stringResource(R.string.matrix_e2ee_install_button))
            }
        }
    }
}

@Composable
private fun MatrixE2eeInstallFailedCard(errorMessage: String?, onRetryClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.matrix_e2ee_feature_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.matrix_e2ee_install_failed,
                    errorMessage ?: stringResource(R.string.matrix_e2ee_unknown_error),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRetryClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(text = stringResource(R.string.matrix_e2ee_install_button))
            }
        }
    }
}

@Composable
private fun MatrixE2eeLoadFailedCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.matrix_e2ee_feature_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.matrix_e2ee_install_failed,
                    stringResource(R.string.matrix_e2ee_load_failed),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun MatrixE2eeVerificationSection(
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
                runCatching { verification.prepare(context, setting) }
            }
        }
        onDispose { verification.reset() }
    }

    fun launchVerification(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.matrix_e2ee_verification_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            MatrixVerificationStateText(state = state)
            MatrixVerificationSas(state = state)

            if (!credentialsReady) {
                Text(
                    text = stringResource(R.string.matrix_e2ee_verification_login_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
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
        Text(
            text = stringResource(
                R.string.matrix_e2ee_verification_device,
                state.userId.ifBlank { "-" },
                state.deviceId.ifBlank { "-" },
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (state.verificationState.isNotBlank()) {
        Text(
            text = stringResource(
                R.string.matrix_e2ee_verification_state,
                matrixVerificationTrustStateText(state.verificationState),
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (state.status == MatrixE2eeVerificationStatus.REQUEST_RECEIVED) {
        Text(
            text = stringResource(
                R.string.matrix_e2ee_verification_request_from,
                state.requestUserId.ifBlank { "-" },
                state.requestDeviceDisplayName.ifBlank { state.requestDeviceId.ifBlank { "-" } },
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    matrixVerificationStatusMessage(state)?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
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
        MatrixE2eeVerificationStatus.UNSUPPORTED_AUTH -> MaterialTheme.colorScheme.error
        MatrixE2eeVerificationStatus.VERIFIED -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun MatrixVerificationSas(state: MatrixE2eeVerificationState) {
    if (state.status != MatrixE2eeVerificationStatus.SAS_READY) return
    if (state.sasDecimals.isNotEmpty()) {
        Text(
            text = state.sasDecimals.joinToString(" "),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
    if (state.sasEmojis.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.sasEmojis.forEach { emoji ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = emoji.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.width(40.dp),
                    )
                    Text(
                        text = emoji.description,
                        style = MaterialTheme.typography.bodySmall,
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
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            colors = if (isError) {
                ButtonDefaults.filledTonalButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                )
            } else {
                ButtonDefaults.filledTonalButtonColors()
            },
        ) {
            MatrixVerificationButtonContent(text = text, icon = icon)
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            colors = if (isError) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
        ) {
            MatrixVerificationButtonContent(text = text, icon = icon)
        }
    }
}

@Composable
private fun MatrixVerificationButtonContent(text: String, icon: ImageVector) {
    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(modifier = Modifier.width(8.dp))
    Text(text)
}

/**
 * Triggers DFM install via the availability interface.
 * On Play builds, this calls PlayFeatureLoader.requestInstall().
 * On other builds, the default no-op implementation is used.
 */
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

private fun matrixVisibleDraft(draft: SenderSettingDraft): SenderSettingDraft {
    var result = draft.keepOnlyFields(MatrixVisibleFields.map { it.name } + "accessToken")
    // Normalize room ID: extract from matrix.to share links
    val rawRoomId = result.string("roomId")
    val normalizedRoomId = extractMatrixRoomId(rawRoomId)
    if (normalizedRoomId != rawRoomId) {
        result = result.withString("roomId", normalizedRoomId)
    }
    return result
}

private fun extractMatrixRoomId(input: String): String {
    if (input.isBlank()) return input
    // Handle https://matrix.to/#/!roomId:server?via=server
    val matrixToRegex = Regex("""https://matrix\.to/#/(![^?\s]+)""")
    matrixToRegex.find(input)?.let { return it.groupValues[1] }
    // Already a valid room ID
    return input.trim()
}
