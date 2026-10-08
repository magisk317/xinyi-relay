package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.runtime.Composable
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.sender.SenderSettingDraft
import io.github.magisk317.relay.ui.sender.SenderViewModel

private val PushdeerVisibleFields = listOf(
    SchemaSenderFormFieldSpec(
        name = "server",
        labelRes = R.string.sender_form_label_server_required,
        placeholderRes = R.string.sender_form_label_server_example,
    ),
    SchemaSenderFormFieldSpec(
        name = "pushkey",
        labelRes = R.string.sender_form_label_pushkey_required,
    ),
    SchemaSenderFormFieldSpec(
        name = "type",
        labelRes = R.string.sender_form_label_message_type,
    ),
    SchemaSenderFormFieldSpec(
        name = "titleTemplate",
        labelRes = R.string.sender_form_title_template_label,
        placeholderRes = R.string.sender_form_title_template_placeholder,
    ),
)

@Composable
fun PushdeerConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    SchemaSenderConfigForm(
        senderId = senderId,
        senderType = SenderType.PUSHDEER,
        channel = "PushDeer",
        fields = PushdeerVisibleFields,
        onBack = onBack,
        viewModel = viewModel,
        normalizeDraft = ::pushdeerVisibleDraft,
    )
}

private fun pushdeerVisibleDraft(draft: SenderSettingDraft): SenderSettingDraft {
    return draft.keepOnlyFields(PushdeerVisibleFields.map { it.name })
}
