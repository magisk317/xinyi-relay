package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.runtime.Composable
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.ui.sender.SenderViewModel

private val DingtalkVisibleFields = listOf(
    SchemaSenderFormFieldSpec(
        name = "token",
        labelRes = R.string.sender_form_label_token_required,
    ),
    SchemaSenderFormFieldSpec(
        name = "secret",
        labelRes = R.string.sender_form_label_secret_signature_key_optional,
    ),
    SchemaSenderFormFieldSpec(
        name = "msgtype",
        labelRes = R.string.sender_form_label_message_type,
        optionLabelRes = MessageTypeOptionLabels,
    ),
    SchemaSenderFormFieldSpec(
        name = "atAll",
        labelRes = R.string.sender_form_label_at_all,
    ),
    SchemaSenderFormFieldSpec(
        name = "titleTemplate",
        labelRes = R.string.sender_form_title_template_optional_label,
        placeholderRes = R.string.sender_form_title_template_placeholder,
    ),
)

@Composable
fun DingtalkConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    SchemaSenderConfigForm(
        senderId = senderId,
        senderType = SenderType.DINGTALK_GROUP_ROBOT,
        channel = "DingtalkGroup",
        fields = DingtalkVisibleFields,
        onBack = onBack,
        viewModel = viewModel,
        normalizeDraft = { it.keepOnlyFields(DingtalkVisibleFields.map { field -> field.name }) },
    )
}
