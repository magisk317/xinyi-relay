package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.runtime.Composable
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.ui.sender.SenderViewModel

private val ServerchanVisibleFields = listOf(
    SchemaSenderFormFieldSpec(
        name = "sendKey",
        labelRes = R.string.sender_form_label_send_key,
    ),
    SchemaSenderFormFieldSpec(
        name = "channel",
        labelRes = R.string.sender_form_label_channel,
    ),
    SchemaSenderFormFieldSpec(
        name = "openid",
        labelRes = R.string.sender_form_label_openid,
    ),
    SchemaSenderFormFieldSpec(
        name = "titleTemplate",
        labelRes = R.string.sender_form_title_template_label,
        placeholderRes = R.string.sender_form_title_template_placeholder,
    ),
)

@Composable
fun ServerchanConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    SchemaSenderConfigForm(
        senderId = senderId,
        senderType = SenderType.SERVERCHAN,
        channel = "Serverchan",
        fields = ServerchanVisibleFields,
        onBack = onBack,
        viewModel = viewModel,
    )
}
