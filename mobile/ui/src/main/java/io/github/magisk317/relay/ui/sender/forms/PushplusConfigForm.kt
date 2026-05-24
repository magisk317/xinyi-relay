package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.runtime.Composable
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.sender.SenderSettingDraft
import io.github.magisk317.relay.ui.sender.SenderViewModel

private val PushplusVisibleFields = listOf(
    SchemaSenderFormFieldSpec(
        name = "token",
        labelRes = R.string.sender_form_label_token_required,
    ),
    SchemaSenderFormFieldSpec(
        name = "topic",
        labelRes = R.string.sender_form_label_topic_code_optional,
    ),
    SchemaSenderFormFieldSpec(
        name = "template",
        labelRes = R.string.sender_form_label_message_template,
    ),
    SchemaSenderFormFieldSpec(
        name = "channel",
        labelRes = R.string.sender_form_label_delivery_channel,
    ),
    SchemaSenderFormFieldSpec(
        name = "website",
        labelRes = R.string.sender_form_label_request_url,
    ),
    SchemaSenderFormFieldSpec(
        name = "titleTemplate",
        labelRes = R.string.sender_form_title_template_label,
        placeholderRes = R.string.sender_form_title_template_placeholder,
    ),
)

@Composable
fun PushplusConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    SchemaSenderConfigForm(
        senderId = senderId,
        senderType = SenderType.PUSHPLUS,
        channel = "Pushplus",
        fields = PushplusVisibleFields,
        onBack = onBack,
        viewModel = viewModel,
        normalizeDraft = ::pushplusVisibleDraft,
    )
}

private fun pushplusVisibleDraft(draft: SenderSettingDraft): SenderSettingDraft {
    return draft.keepOnlyFields(PushplusVisibleFields.map { it.name })
}
