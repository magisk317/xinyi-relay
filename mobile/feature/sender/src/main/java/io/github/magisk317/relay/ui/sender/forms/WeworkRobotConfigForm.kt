package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.runtime.Composable
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.ui.sender.SenderViewModel

private val WeworkRobotVisibleFields = listOf(
    SchemaSenderFormFieldSpec(
        name = "webHook",
        labelRes = R.string.sender_form_label_webhook_url,
    ),
    SchemaSenderFormFieldSpec(
        name = "msgType",
        labelRes = R.string.sender_form_label_message_type,
        optionLabelRes = MessageTypeOptionLabels,
    ),
    SchemaSenderFormFieldSpec(
        name = "atAll",
        labelRes = R.string.sender_form_label_at_all,
    ),
    SchemaSenderFormFieldSpec(
        name = "atUserIds",
        labelRes = R.string.sender_form_label_at_user_ids_comma,
    ),
    SchemaSenderFormFieldSpec(
        name = "atMobiles",
        labelRes = R.string.sender_form_label_at_mobile_numbers_comma,
    ),
)

@Composable
fun WeworkRobotConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    SchemaSenderConfigForm(
        senderId = senderId,
        senderType = SenderType.WEWORK_ROBOT,
        channel = "WeworkRobot",
        fields = WeworkRobotVisibleFields,
        onBack = onBack,
        viewModel = viewModel,
        normalizeDraft = { it.keepOnlyFields(WeworkRobotVisibleFields.map { field -> field.name }) },
    )
}
