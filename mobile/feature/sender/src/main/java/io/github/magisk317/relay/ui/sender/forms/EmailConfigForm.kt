package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.engine.service.SenderRuntimeServiceRegistry
import io.github.magisk317.relay.sender.DeviceCodePollResult
import io.github.magisk317.relay.sender.EmailOAuthService
import io.github.magisk317.relay.sender.SenderSettingDraft
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState
import io.github.magisk317.relay.ui.sender.SenderViewModel
import io.github.magisk317.uikit.common.showLatestSnackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val EmailVisibleFields = listOf(
    SchemaSenderFormFieldSpec(
        name = "mailType",
        labelRes = R.string.sender_form_label_mail_type_example,
    ),
    SchemaSenderFormFieldSpec(
        name = "authEmail",
        labelRes = R.string.sender_form_label_auth_email,
    ),
    SchemaSenderFormFieldSpec(
        name = "fromEmail",
        labelRes = R.string.sender_form_label_from_email,
    ),
    SchemaSenderFormFieldSpec(
        name = "fromEmailAlias",
        labelRes = R.string.sender_form_label_from_email_alias,
    ),
    SchemaSenderFormFieldSpec(
        name = "authMethod",
        labelRes = R.string.sender_form_label_auth_method,
    ),
    SchemaSenderFormFieldSpec(
        name = "pwd",
        labelRes = R.string.sender_form_label_auth_code_or_password,
        visible = { it.string("authMethod") != "oauth2" },
    ),
    SchemaSenderFormFieldSpec(
        name = "oauth2ClientId",
        labelRes = R.string.sender_form_label_oauth2_client_id,
        visible = { it.string("authMethod") == "oauth2" },
    ),
    SchemaSenderFormFieldSpec(
        name = "oauth2TenantId",
        labelRes = R.string.sender_form_label_oauth2_tenant_id,
        supportingTextRes = R.string.sender_form_label_oauth2_tenant_id_supporting,
        visible = { it.string("authMethod") == "oauth2" },
    ),
    SchemaSenderFormFieldSpec(
        name = "host",
        labelRes = R.string.sender_form_label_smtp_host,
    ),
    SchemaSenderFormFieldSpec(
        name = "port",
        labelRes = R.string.sender_form_label_smtp_port,
    ),
    SchemaSenderFormFieldSpec(
        name = "toEmail",
        labelRes = R.string.sender_form_label_recipients_comma,
    ),
    SchemaSenderFormFieldSpec(
        name = "title",
        labelRes = R.string.sender_form_label_title,
        placeholderRes = R.string.sender_form_email_title_template_placeholder,
        supportingTextRes = R.string.sender_form_email_title_template_summary,
    ),
    SchemaSenderFormFieldSpec(
        name = "ssl",
        labelRes = R.string.sender_segment_ssl,
    ),
    SchemaSenderFormFieldSpec(
        name = "startTls",
        labelRes = R.string.sender_segment_starttls,
    ),
)

@Composable
fun EmailConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    SchemaSenderConfigForm(
        senderId = senderId,
        senderType = SenderType.EMAIL,
        channel = "Email",
        fields = EmailVisibleFields,
        onBack = onBack,
        viewModel = viewModel,
        normalizeDraft = ::emailVisibleDraft,
        extraContent = { draft, onDraftChange ->
            OAuth2DeviceAuthSection(draft, onDraftChange)
        },
    )
}

/**
 * In-app device-code authorization section for OAuth2 email senders.
 * Shown only when authMethod == "oauth2". Guides the user through the
 * Microsoft device-code flow and writes the obtained credentialId back
 * to the draft.
 */
@Composable
private fun OAuth2DeviceAuthSection(
    draft: SenderSettingDraft,
    onDraftChange: (SenderSettingDraft) -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val coroutineScope = rememberCoroutineScope()

    val isOAuth2 = draft.string("authMethod") == "oauth2"
    if (!isOAuth2) return

    val clientId = draft.string("oauth2ClientId")
    val tenantId = draft.string("oauth2TenantId")
    val credentialId = draft.string("oauth2CredentialId")

    val oauthService = remember {
        SenderRuntimeServiceRegistry.installedOrNull()?.emailOAuth() as? EmailOAuthService
    }

    var isAuthorizing by remember { mutableStateOf(false) }
    var userCode by remember { mutableStateOf<String?>(null) }
    var verificationUri by remember { mutableStateOf<String?>(null) }

    val hasCredentials = credentialId.isNotBlank() &&
        (oauthService?.hasCredentials(credentialId) ?: false)

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.sender_form_oauth2_device_auth_section),
        style = MaterialTheme.typography.titleSmall,
    )

    if (hasCredentials) {
        Text(
            text = stringResource(R.string.sender_form_oauth2_status_authorized),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(
            onClick = {
                coroutineScope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) { oauthService.deleteCredentials(credentialId) }
                    }
                    onDraftChange(draft.withString("oauth2CredentialId", ""))
                    snackbarHostState.showLatestSnackbar(
                        context.getString(R.string.sender_form_oauth2_revoked),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.sender_form_oauth2_revoke))
        }
    } else {
        Text(
            text = stringResource(R.string.sender_form_oauth2_status_not_authorized),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        val pendingUserCode = userCode
        val pendingUri = verificationUri
        if (pendingUserCode != null && pendingUri != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(
                        R.string.sender_form_oauth2_user_code_instruction,
                        pendingUserCode,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = pendingUri,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        Button(
            onClick = {
                if (clientId.isBlank() || tenantId.isBlank()) {
                    coroutineScope.launch {
                        snackbarHostState.showLatestSnackbar(
                            context.getString(R.string.sender_form_oauth2_missing_config),
                        )
                    }
                    return@Button
                }
                if (oauthService == null) {
                    coroutineScope.launch {
                        snackbarHostState.showLatestSnackbar(
                            context.getString(R.string.sender_form_oauth2_service_unavailable),
                        )
                    }
                    return@Button
                }
                isAuthorizing = true
                coroutineScope.launch {
                    runCatching {
                        // Step 1: request device code (blocking network call on IO thread).
                        val result = withContext(Dispatchers.IO) {
                            oauthService.requestDeviceCode(clientId, tenantId)
                        }
                        userCode = result.userCode
                        verificationUri = result.verificationUri
                        snackbarHostState.showLatestSnackbar(
                            context.getString(R.string.sender_form_oauth2_browser_prompt),
                        )
                        // Step 2: poll until authorized or expired (IO-bound).
                        pollLoop(
                            service = oauthService,
                            deviceCode = result.deviceCode,
                            clientId = clientId,
                            tenantId = tenantId,
                            intervalMs = result.intervalMs,
                        )
                    }.onSuccess { pollResult ->
                        when (pollResult) {
                            is DeviceCodePollResult.Authorized -> {
                                onDraftChange(
                                    draft.withString(
                                        "oauth2CredentialId",
                                        pollResult.credentials.credentialId,
                                    ),
                                )
                                snackbarHostState.showLatestSnackbar(
                                    context.getString(R.string.sender_form_oauth2_auth_success),
                                )
                            }
                            is DeviceCodePollResult.Failed -> {
                                snackbarHostState.showLatestSnackbar(
                                    context.getString(
                                        R.string.sender_form_oauth2_auth_failed,
                                        pollResult.reason,
                                    ),
                                )
                            }
                            is DeviceCodePollResult.Pending -> {
                                // Should not happen after pollLoop returns.
                                snackbarHostState.showLatestSnackbar(
                                    context.getString(R.string.sender_form_oauth2_auth_timeout),
                                )
                            }
                        }
                    }.onFailure { error ->
                        snackbarHostState.showLatestSnackbar(
                            context.getString(
                                R.string.sender_form_oauth2_auth_error,
                                error.message.orEmpty(),
                            ),
                        )
                    }
                    isAuthorizing = false
                    userCode = null
                    verificationUri = null
                }
            },
            enabled = !isAuthorizing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (isAuthorizing) {
                    stringResource(R.string.sender_form_oauth2_authorizing)
                } else {
                    stringResource(R.string.sender_form_oauth2_start_auth)
                },
            )
        }
    }
}

/**
 * Poll the token endpoint until the user completes authorization, the code
 * expires, or an unrecoverable error occurs. Honors the interval requested
 * by the server (with a small safety margin).
 */
private suspend fun pollLoop(
    service: EmailOAuthService,
    deviceCode: String,
    clientId: String,
    tenantId: String,
    intervalMs: Long,
): DeviceCodePollResult {
    while (true) {
        delay(intervalMs + 1000L)
        val result = withContext(Dispatchers.IO) {
            service.pollForToken(deviceCode, clientId, tenantId, intervalMs)
        }
        when (result) {
            is DeviceCodePollResult.Pending -> continue
            else -> return result
        }
    }
}

private fun emailVisibleDraft(draft: SenderSettingDraft): SenderSettingDraft {
    val fromEmail = draft.string("fromEmail")
    val authEmail = draft.string("authEmail").ifBlank { fromEmail }
    val fromEmailAlias = draft.string("fromEmailAlias").ifBlank { draft.string("nickname") }
    return draft
        .withString("authEmail", authEmail)
        .withString("fromEmailAlias", fromEmailAlias)
        .keepOnlyFields(EmailVisibleFields.map { it.name })
}
