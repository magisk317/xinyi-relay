package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.runtime.Composable
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.sender.SenderSettingDraft
import io.github.magisk317.relay.ui.sender.SenderViewModel

private val WeworkAgentVisibleFields = listOf(
    SchemaSenderFormFieldSpec(
        name = "corpID",
        labelRes = R.string.sender_form_label_corp_id,
    ),
    SchemaSenderFormFieldSpec(
        name = "agentID",
        labelRes = R.string.sender_form_label_agent_id,
    ),
    SchemaSenderFormFieldSpec(
        name = "secret",
        labelRes = R.string.sender_form_label_secret,
    ),
    SchemaSenderFormFieldSpec(
        name = "toUser",
        labelRes = R.string.sender_form_label_to_user,
    ),
    SchemaSenderFormFieldSpec(
        name = "customizeAPI",
        labelRes = R.string.sender_form_label_api_base,
    ),
)

@Composable
fun WeworkAgentConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    SchemaSenderConfigForm(
        senderId = senderId,
        senderType = SenderType.WEWORK_AGENT,
        channel = "WeworkAgent",
        fields = WeworkAgentVisibleFields,
        onBack = onBack,
        viewModel = viewModel,
        normalizeDraft = ::weworkAgentVisibleDraft,
    )
}

private fun weworkAgentVisibleDraft(draft: SenderSettingDraft): SenderSettingDraft {
    return draft.keepOnlyFields(WeworkAgentVisibleFields.map { it.name } + "proxyType")
}
