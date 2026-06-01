package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.runtime.Composable
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.sender.SenderSettingDraft
import io.github.magisk317.relay.ui.sender.SenderViewModel

private val FeishuBotTokenVisibleFields = listOf(
    SchemaSenderFormFieldSpec(
        name = "token",
        labelRes = R.string.sender_form_label_token_required,
    ),
    SchemaSenderFormFieldSpec(
        name = "receiveId",
        labelRes = R.string.sender_form_label_receive_id,
    ),
    SchemaSenderFormFieldSpec(
        name = "receiveIdType",
        labelRes = R.string.sender_form_label_receive_id_type,
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
fun FeishuBotTokenConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    SchemaSenderConfigForm(
        senderId = senderId,
        senderType = SenderType.FEISHU_BOT_TOKEN,
        channel = "FeishuBotToken",
        fields = FeishuBotTokenVisibleFields,
        onBack = onBack,
        viewModel = viewModel,
        normalizeDraft = ::feishuBotTokenVisibleDraft,
    )
}

private fun feishuBotTokenVisibleDraft(draft: SenderSettingDraft): SenderSettingDraft {
    return draft.keepOnlyFields(FeishuBotTokenVisibleFields.map { it.name })
}
