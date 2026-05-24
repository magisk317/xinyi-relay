package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.mobileui.BuildConfig
import io.github.magisk317.relay.sender.SenderSettingDraft
import io.github.magisk317.relay.ui.sender.SenderViewModel

private val WebhookVisibleFields = listOf(
    SchemaSenderFormFieldSpec(
        name = "webServer",
        labelRes = R.string.sender_form_label_full_webhook_url_required,
        supportingTextRes = if (BuildConfig.ALLOW_HTTP_WEBHOOK) {
            R.string.sender_form_webhook_hint_http_https
        } else {
            R.string.sender_form_webhook_hint_https_only
        },
    ),
    SchemaSenderFormFieldSpec(
        name = "secret",
        labelRes = R.string.sender_form_label_signature_secret_optional,
    ),
    SchemaSenderFormFieldSpec(
        name = "method",
        labelRes = R.string.sender_form_label_method,
        optionLabelRes = mapOf(
            "GET" to R.string.sender_segment_get,
            "POST" to R.string.sender_segment_post,
        ),
    ),
    SchemaSenderFormFieldSpec(
        name = "webParams",
        labelRes = R.string.sender_form_label_custom_webparams_optional,
        minLines = 3,
    ),
    SchemaSenderFormFieldSpec(
        name = "headers",
        labelRes = R.string.sender_form_label_custom_headers_json_optional,
        supportingTextRes = R.string.sender_form_label_custom_headers_json_example,
        minLines = 2,
    ),
)

@Composable
fun WebhookConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    val context = LocalContext.current
    SchemaSenderConfigForm(
        senderId = senderId,
        senderType = SenderType.WEBHOOK,
        channel = "Webhook",
        fields = WebhookVisibleFields,
        onBack = onBack,
        viewModel = viewModel,
        normalizeDraft = ::webhookVisibleDraft,
        validateDraft = { draft, status ->
            val webServer = draft.string("webServer").trim()
            if (status != 0 && !BuildConfig.ALLOW_HTTP_WEBHOOK && webServer.startsWith("http://", ignoreCase = true)) {
                context.getString(R.string.sender_form_https_only_webhook)
            } else {
                null
            }
        },
    )
}

private fun webhookVisibleDraft(draft: SenderSettingDraft): SenderSettingDraft {
    return draft
        .withString("proxyType", "DIRECT")
        .keepOnlyFields(WebhookVisibleFields.map { it.name } + "proxyType")
}
