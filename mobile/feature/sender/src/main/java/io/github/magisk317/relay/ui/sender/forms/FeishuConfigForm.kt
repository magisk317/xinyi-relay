package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.runtime.Composable
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.sender.SenderSettingDraft
import io.github.magisk317.relay.ui.sender.SenderViewModel

private val FeishuVisibleFields = listOf(
    SchemaSenderFormFieldSpec(
        name = "webhook",
        labelRes = R.string.sender_form_label_webhook_url,
    ),
    SchemaSenderFormFieldSpec(
        name = "secret",
        labelRes = R.string.sender_form_label_secret_optional,
    ),
    SchemaSenderFormFieldSpec(
        name = "msgType",
        labelRes = R.string.sender_form_label_message_type,
        optionLabelRes = InteractiveMessageTypeOptionLabels,
    ),
    SchemaSenderFormFieldSpec(
        name = "titleTemplate",
        labelRes = R.string.sender_form_title_template_label,
        placeholderRes = R.string.sender_form_title_template_placeholder,
    ),
    SchemaSenderFormFieldSpec(
        name = "messageCard",
        labelRes = R.string.sender_form_label_message_card_json_optional,
        minLines = 4,
    ),
)

@Composable
fun FeishuConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    SchemaSenderConfigForm(
        senderId = senderId,
        senderType = SenderType.FEISHU,
        channel = "Feishu",
        fields = FeishuVisibleFields,
        onBack = onBack,
        viewModel = viewModel,
        normalizeDraft = ::feishuVisibleDraft,
    )
}

private fun feishuVisibleDraft(draft: SenderSettingDraft): SenderSettingDraft {
    return draft.keepOnlyFields(FeishuVisibleFields.map { it.name })
}
