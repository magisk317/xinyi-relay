package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.sender.AesUtils
import io.github.magisk317.relay.sender.SenderSettingDraft
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState
import io.github.magisk317.relay.ui.sender.SenderViewModel
import kotlinx.coroutines.launch

private val BarkVisibleFields = listOf(
    SchemaSenderFormFieldSpec(
        name = "server",
        labelRes = R.string.sender_form_label_bark_server,
    ),
    SchemaSenderFormFieldSpec(
        name = "title",
        labelRes = R.string.sender_form_title_template_label,
        placeholderRes = R.string.sender_form_title_template_placeholder,
    ),
    SchemaSenderFormFieldSpec(
        name = "transformation",
        labelRes = R.string.sender_form_label_bark_encryption_type,
        optionLabelRes = mapOf(
            "none" to R.string.sender_form_label_bark_encryption_none,
            "AES/GCM/NoPadding" to R.string.sender_form_label_bark_encryption_gcm,
            "AES/CBC/PKCS5Padding" to R.string.sender_form_label_bark_encryption_cbc,
        ),
    ),
)

@Composable
fun BarkConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    SchemaSenderConfigForm(
        senderId = senderId,
        senderType = SenderType.BARK,
        channel = "Bark",
        fields = BarkVisibleFields,
        onBack = onBack,
        viewModel = viewModel,
        normalizeDraft = ::barkVisibleDraft,
        extraContent = { draft, onDraftChange ->
            BarkEncryptionFields(draft = draft, onDraftChange = onDraftChange)
        },
    )
}

private fun barkVisibleDraft(draft: SenderSettingDraft): SenderSettingDraft {
    val transformation = draft.string("transformation").ifBlank { "none" }
    val normalized = draft.withString("transformation", transformation)
    return if (transformation == "AES/GCM/NoPadding") {
        normalized.withString("iv", "")
    } else {
        normalized
    }.keepOnlyFields(BarkVisibleFields.map { it.name } + listOf("key", "iv"))
}

@Composable
private fun BarkEncryptionFields(
    draft: SenderSettingDraft,
    onDraftChange: (SenderSettingDraft) -> Unit,
) {
    val transformation = draft.string("transformation").ifBlank { "none" }
    if (transformation == "none") return

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val keyGeneratedLabel = stringResource(R.string.sender_form_label_bark_encryption_key_generated)
    val ivGeneratedLabel = stringResource(R.string.sender_form_label_bark_encryption_iv_generated)

    Text(
        text = stringResource(R.string.sender_form_label_bark_encryption),
        style = MaterialTheme.typography.titleMedium,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = draft.string("key"),
            onValueChange = { onDraftChange(draft.withString("key", it)) },
            label = { Text(stringResource(R.string.sender_form_label_bark_encryption_key)) },
            placeholder = { Text(stringResource(R.string.sender_form_label_bark_encryption_key_hint)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        IconButton(
            onClick = {
                onDraftChange(draft.withString("key", AesUtils.generateKey()))
                coroutineScope.launch { snackbarHostState.showSnackbar(keyGeneratedLabel) }
            },
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = stringResource(R.string.sender_form_label_bark_encryption_key),
            )
        }
    }

    if (transformation == "AES/CBC/PKCS5Padding") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft.string("iv"),
                onValueChange = { onDraftChange(draft.withString("iv", it)) },
                label = { Text(stringResource(R.string.sender_form_label_bark_encryption_iv)) },
                placeholder = { Text(stringResource(R.string.sender_form_label_bark_encryption_iv_hint)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            IconButton(
                onClick = {
                    onDraftChange(draft.withString("iv", AesUtils.generateIv(transformation)))
                    coroutineScope.launch { snackbarHostState.showSnackbar(ivGeneratedLabel) }
                },
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.sender_form_label_bark_encryption_iv),
                )
            }
        }
    }

    Text(
        text = when (transformation) {
            "AES/GCM/NoPadding" -> stringResource(R.string.sender_form_label_bark_encryption_gcm_desc)
            "AES/CBC/PKCS5Padding" -> stringResource(R.string.sender_form_label_bark_encryption_cbc_desc)
            else -> ""
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
