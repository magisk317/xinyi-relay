package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.runtime.Composable
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.ui.sender.SenderViewModel

private val UrlSchemeVisibleFields = listOf(
    SchemaSenderFormFieldSpec(
        name = "urlScheme",
        labelRes = R.string.sender_form_label_url_scheme,
        minLines = 4,
    ),
)

@Composable
fun UrlSchemeConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    SchemaSenderConfigForm(
        senderId = senderId,
        senderType = SenderType.URL_SCHEME,
        channel = "UrlScheme",
        fields = UrlSchemeVisibleFields,
        onBack = onBack,
        viewModel = viewModel,
    )
}
