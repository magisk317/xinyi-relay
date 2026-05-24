package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.runtime.Composable
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.sender.SenderSettingDraft
import io.github.magisk317.relay.ui.sender.SenderViewModel

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
        name = "pwd",
        labelRes = R.string.sender_form_label_auth_code_or_password,
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
        placeholderRes = R.string.sender_form_title_template_placeholder,
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
    )
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
