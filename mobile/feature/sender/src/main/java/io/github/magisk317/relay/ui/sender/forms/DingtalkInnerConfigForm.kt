package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.runtime.Composable
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.sender.SenderSettingDraft
import io.github.magisk317.relay.ui.sender.SenderViewModel

private val DingtalkInnerVisibleFields = listOf(
    SchemaSenderFormFieldSpec(
        name = "agentID",
        labelRes = R.string.sender_form_label_agent_id,
    ),
    SchemaSenderFormFieldSpec(
        name = "appKey",
        labelRes = R.string.sender_form_label_app_key,
    ),
    SchemaSenderFormFieldSpec(
        name = "appSecret",
        labelRes = R.string.sender_form_label_app_secret,
    ),
    SchemaSenderFormFieldSpec(
        name = "userIds",
        labelRes = R.string.sender_form_label_user_ids_comma,
    ),
    SchemaSenderFormFieldSpec(
        name = "msgKey",
        labelRes = R.string.sender_form_label_message_type,
        optionLabelRes = mapOf(
            "sampleText" to R.string.sender_segment_text,
            "sampleMarkdown" to R.string.sender_segment_markdown,
        ),
    ),
    SchemaSenderFormFieldSpec(
        name = "titleTemplate",
        labelRes = R.string.sender_form_title_template_label,
        placeholderRes = R.string.sender_form_title_template_placeholder,
    ),
)

@Composable
fun DingtalkInnerConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    SchemaSenderConfigForm(
        senderId = senderId,
        senderType = SenderType.DINGTALK_INNER_ROBOT,
        channel = "DingtalkInner",
        fields = DingtalkInnerVisibleFields,
        onBack = onBack,
        viewModel = viewModel,
        normalizeDraft = ::dingtalkInnerVisibleDraft,
    )
}

private fun dingtalkInnerVisibleDraft(draft: SenderSettingDraft): SenderSettingDraft {
    return draft.keepOnlyFields(DingtalkInnerVisibleFields.map { it.name } + "proxyType")
}
