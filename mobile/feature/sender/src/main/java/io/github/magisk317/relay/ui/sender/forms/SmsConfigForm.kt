package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.runtime.Composable
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.sender.SenderSettingDraft
import io.github.magisk317.relay.ui.sender.SenderViewModel

private val SmsVisibleFields = listOf(
    SchemaSenderFormFieldSpec(
        name = "mobiles",
        labelRes = R.string.sender_form_label_target_numbers_comma,
    ),
    SchemaSenderFormFieldSpec(
        name = "simSlot",
        labelRes = R.string.sender_form_label_sim_slot,
    ),
)

@Composable
fun SmsConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    SchemaSenderConfigForm(
        senderId = senderId,
        senderType = SenderType.SMS,
        channel = "SMS",
        fields = SmsVisibleFields,
        onBack = onBack,
        viewModel = viewModel,
        normalizeDraft = ::smsVisibleDraft,
    )
}

private fun smsVisibleDraft(draft: SenderSettingDraft): SenderSettingDraft {
    return draft.keepOnlyFields(SmsVisibleFields.map { it.name } + "onlyNoNetwork")
}
