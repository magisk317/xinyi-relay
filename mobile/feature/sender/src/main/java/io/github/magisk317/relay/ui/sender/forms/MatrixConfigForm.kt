package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.sender.E2eeModuleStatus
import io.github.magisk317.relay.sender.MatrixE2eeAvailability
import io.github.magisk317.relay.sender.MatrixE2eeAvailabilityProvider
import io.github.magisk317.relay.sender.SenderSettingDraft
import io.github.magisk317.relay.ui.sender.SenderViewModel

private val MatrixVisibleFields = listOf(
    SchemaSenderFormFieldSpec(
        name = "homeserver",
        labelRes = R.string.sender_form_label_matrix_homeserver_required,
        supportingTextRes = R.string.sender_form_label_matrix_homeserver_example,
    ),
    SchemaSenderFormFieldSpec(
        name = "accessToken",
        labelRes = R.string.sender_form_label_matrix_access_token_required,
    ),
    SchemaSenderFormFieldSpec(
        name = "roomId",
        labelRes = R.string.sender_form_label_matrix_room_id_required,
        placeholderRes = R.string.sender_form_label_matrix_room_id_placeholder,
        supportingTextRes = R.string.sender_form_label_matrix_room_id_placeholder,
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
    SchemaSenderConfigForm(
        senderId = senderId,
        senderType = SenderType.MATRIX,
        channel = "Matrix",
        fields = MatrixVisibleFields,
        onBack = onBack,
        viewModel = viewModel,
        normalizeDraft = ::matrixVisibleDraft,
        extraContent = { _, _ ->
            MatrixE2eeStatusSection(
                availability = MatrixE2eeAvailabilityProvider.get(),
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
                    errorMessage ?: "Unknown error",
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
                    "Module loaded but failed to initialize",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
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
    return draft.keepOnlyFields(MatrixVisibleFields.map { it.name })
}
