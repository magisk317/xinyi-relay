package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.runtime.Composable
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.sender.SenderSettingDraft
import io.github.magisk317.relay.ui.sender.SenderViewModel

private val YunhuRecvTypeOptionLabels = mapOf(
    "user" to R.string.sender_segment_yunhu_user,
    "group" to R.string.sender_segment_yunhu_group,
)

private val YunhuVisibleFields = listOf(
    SchemaSenderFormFieldSpec(
        name = "token",
        labelRes = R.string.sender_form_label_token_required,
    ),
    SchemaSenderFormFieldSpec(
        name = "recvId",
        labelRes = R.string.sender_form_label_yunhu_recv_id_required,
        supportingTextRes = R.string.sender_form_label_yunhu_recv_id_hint,
    ),
    SchemaSenderFormFieldSpec(
        name = "recvType",
        labelRes = R.string.sender_form_label_yunhu_recv_type,
        optionLabelRes = YunhuRecvTypeOptionLabels,
    ),
    SchemaSenderFormFieldSpec(
        name = "contentType",
        labelRes = R.string.sender_form_label_message_type,
        optionLabelRes = MessageTypeOptionLabels,
    ),
    SchemaSenderFormFieldSpec(
        name = "titleTemplate",
        labelRes = R.string.sender_form_title_template_label,
        placeholderRes = R.string.sender_form_title_template_placeholder,
    ),
)

@Composable
fun YunhuConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    SchemaSenderConfigForm(
        senderId = senderId,
        senderType = SenderType.YUNHU,
        channel = "Yunhu",
        fields = YunhuVisibleFields,
        onBack = onBack,
        viewModel = viewModel,
        normalizeDraft = ::yunhuVisibleDraft,
    )
}

private fun yunhuVisibleDraft(draft: SenderSettingDraft): SenderSettingDraft {
    return draft.keepOnlyFields(YunhuVisibleFields.map { it.name })
}
