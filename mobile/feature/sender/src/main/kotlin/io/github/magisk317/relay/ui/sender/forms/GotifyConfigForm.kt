package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.runtime.Composable
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.ui.sender.SenderViewModel

private val GotifyVisibleFields = listOf(
    SchemaSenderFormFieldSpec(
        name = "webServer",
        labelRes = R.string.sender_form_label_gotify_server,
    ),
    SchemaSenderFormFieldSpec(
        name = "title",
        labelRes = R.string.sender_form_label_title,
        placeholderRes = R.string.sender_form_title_template_placeholder,
    ),
    SchemaSenderFormFieldSpec(
        name = "priority",
        labelRes = R.string.sender_form_label_priority,
    ),
)

@Composable
fun GotifyConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    SchemaSenderConfigForm(
        senderId = senderId,
        senderType = SenderType.GOTIFY,
        channel = "Gotify",
        fields = GotifyVisibleFields,
        onBack = onBack,
        viewModel = viewModel,
    )
}
