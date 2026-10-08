package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.runtime.Composable
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.ui.sender.SenderViewModel

private val NtfyVisibleFields = listOf(
    SchemaSenderFormFieldSpec(
        name = "server",
        labelRes = R.string.sender_form_label_server_required,
        supportingTextRes = R.string.sender_form_label_server_example,
    ),
    SchemaSenderFormFieldSpec(
        name = "topic",
        labelRes = R.string.sender_form_label_topic_required,
    ),
    SchemaSenderFormFieldSpec(
        name = "token",
        labelRes = R.string.sender_form_label_bearer_token_optional,
    ),
    SchemaSenderFormFieldSpec(
        name = "title",
        labelRes = R.string.sender_form_label_title_optional,
        placeholderRes = R.string.sender_form_title_template_placeholder,
    ),
    SchemaSenderFormFieldSpec(
        name = "priority",
        labelRes = R.string.sender_form_label_priority_1_5,
    ),
    SchemaSenderFormFieldSpec(
        name = "tags",
        labelRes = R.string.sender_form_label_tags_optional,
        supportingTextRes = R.string.sender_form_label_tags_example,
    ),
)

@Composable
fun NtfyConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    SchemaSenderConfigForm(
        senderId = senderId,
        senderType = SenderType.NTFY,
        channel = "Ntfy",
        fields = NtfyVisibleFields,
        onBack = onBack,
        viewModel = viewModel,
    )
}
