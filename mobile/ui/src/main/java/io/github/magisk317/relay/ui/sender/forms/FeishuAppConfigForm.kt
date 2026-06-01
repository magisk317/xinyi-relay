package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.runtime.Composable
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.sender.SenderSettingDraft
import io.github.magisk317.relay.ui.sender.SenderViewModel

private val FeishuAppVisibleFields = listOf(
    SchemaSenderFormFieldSpec(
        name = "authType",
        labelRes = R.string.sender_form_label_auth_type,
        optionLabelRes = mapOf(
            "app_id" to R.string.sender_form_auth_type_app_id,
            "token" to R.string.sender_form_auth_type_token,
        ),
    ),
    SchemaSenderFormFieldSpec(
        name = "appId",
        labelRes = R.string.sender_form_label_app_id,
        visible = { it.string("authType").ifBlank { "app_id" } == "app_id" },
    ),
    SchemaSenderFormFieldSpec(
        name = "appSecret",
        labelRes = R.string.sender_form_label_app_secret,
        visible = { it.string("authType").ifBlank { "app_id" } == "app_id" },
    ),
    SchemaSenderFormFieldSpec(
        name = "botToken",
        labelRes = R.string.sender_form_label_bot_token,
        visible = { it.string("authType").ifBlank { "app_id" } == "token" },
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
fun FeishuAppConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    SchemaSenderConfigForm(
        senderId = senderId,
        senderType = SenderType.FEISHU_APP,
        channel = "FeishuApp",
        fields = FeishuAppVisibleFields,
        onBack = onBack,
        viewModel = viewModel,
        normalizeDraft = ::feishuAppVisibleDraft,
    )
}

private fun feishuAppVisibleDraft(draft: SenderSettingDraft): SenderSettingDraft {
    return draft.keepOnlyFields(FeishuAppVisibleFields.map { it.name })
}
