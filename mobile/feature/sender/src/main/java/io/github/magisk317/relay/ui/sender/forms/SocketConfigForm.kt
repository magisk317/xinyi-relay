package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.runtime.Composable
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.sender.SenderSettingDraft
import io.github.magisk317.relay.ui.sender.SenderViewModel

private val SocketVisibleFields = listOf(
    SchemaSenderFormFieldSpec(
        name = "method",
        labelRes = R.string.sender_form_label_method,
        optionLabelRes = mapOf(
            "TCP" to R.string.sender_segment_tcp,
            "UDP" to R.string.sender_segment_udp,
            "MQTT" to R.string.sender_segment_mqtt,
        ),
    ),
    SchemaSenderFormFieldSpec(
        name = "address",
        labelRes = R.string.sender_form_label_address,
    ),
    SchemaSenderFormFieldSpec(
        name = "port",
        labelRes = R.string.sender_form_label_port,
    ),
    SchemaSenderFormFieldSpec(
        name = "msgTemplate",
        labelRes = R.string.sender_form_label_message_template,
        minLines = 3,
    ),
    SchemaSenderFormFieldSpec(
        name = "outMessageTopic",
        labelRes = R.string.sender_form_label_mqtt_output_topic,
    ),
)

@Composable
fun SocketConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    SchemaSenderConfigForm(
        senderId = senderId,
        senderType = SenderType.SOCKET,
        channel = "Socket",
        fields = SocketVisibleFields,
        onBack = onBack,
        viewModel = viewModel,
        normalizeDraft = ::socketVisibleDraft,
    )
}

private fun socketVisibleDraft(draft: SenderSettingDraft): SenderSettingDraft {
    return draft.keepOnlyFields(SocketVisibleFields.map { it.name })
}
