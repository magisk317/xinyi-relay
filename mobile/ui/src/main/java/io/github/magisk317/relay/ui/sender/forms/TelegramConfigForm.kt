package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.runtime.Composable
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.sender.SenderSettingDraft
import io.github.magisk317.relay.ui.sender.SenderViewModel

private val TelegramVisibleFields = listOf(
    SchemaSenderFormFieldSpec(
        name = "apiBase",
        labelRes = R.string.sender_form_label_api_base,
    ),
    SchemaSenderFormFieldSpec(
        name = "apiToken",
        labelRes = R.string.sender_form_label_bot_api_token_required,
    ),
    SchemaSenderFormFieldSpec(
        name = "chatId",
        labelRes = R.string.sender_form_label_chat_id_required,
    ),
    SchemaSenderFormFieldSpec(
        name = "messageThreadId",
        labelRes = R.string.sender_form_label_topic_id_optional,
        supportingTextRes = R.string.sender_form_label_group_thread_id,
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
        name = "parseMode",
        labelRes = R.string.sender_form_label_parse_mode,
        optionLabelRes = mapOf(
            "HTML" to R.string.sender_segment_html,
            "MarkdownV2" to R.string.sender_segment_markdown_v2,
        ),
    ),
    SchemaSenderFormFieldSpec(
        name = "proxyHost",
        labelRes = R.string.sender_form_label_proxy_host,
    ),
    SchemaSenderFormFieldSpec(
        name = "proxyPort",
        labelRes = R.string.sender_form_label_proxy_port,
    ),
)

@Composable
fun TelegramConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    SchemaSenderConfigForm(
        senderId = senderId,
        senderType = SenderType.TELEGRAM,
        channel = "Telegram",
        fields = TelegramVisibleFields,
        onBack = onBack,
        viewModel = viewModel,
        normalizeDraft = ::telegramVisibleDraft,
    )
}

private fun telegramVisibleDraft(draft: SenderSettingDraft): SenderSettingDraft {
    return draft
        .withString("proxyType", "DIRECT")
        .keepOnlyFields(TelegramVisibleFields.map { it.name } + "proxyType")
}
