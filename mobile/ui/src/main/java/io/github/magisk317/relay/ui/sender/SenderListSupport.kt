@file:Suppress("NonObservableLocale")

package io.github.magisk317.relay.ui.sender

import android.content.Context
import io.github.magisk317.relay.contract.constant.DispatchStrategy
import io.github.magisk317.relay.contract.constant.MessageType
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.engine.model.Sender
import java.util.Date

internal data class TemplateVariable(
    val labelRes: Int,
    val token: String,
)

internal val forwardTemplateVariables = listOf(
    TemplateVariable(R.string.sender_template_var_sender, "{{FROM}}"),
    TemplateVariable(R.string.sender_template_var_sms_body, "{{SMS}}"),
    TemplateVariable(R.string.sender_template_var_call_type, "{{CALL_TYPE}}"),
    TemplateVariable(R.string.sender_template_var_sim_note, "{{CARD_SLOT}}"),
    TemplateVariable(R.string.sender_template_var_sim_sub_id, "{{CARD_SUBID}}"),
    TemplateVariable(R.string.sender_template_var_contact_name, "{{CONTACT_NAME}}"),
    TemplateVariable(R.string.sender_template_var_phone_area, "{{PHONE_AREA}}"),
    TemplateVariable(R.string.sender_template_var_app_package, "{{PACKAGE_NAME}}"),
    TemplateVariable(R.string.sender_template_var_app_name, "{{APP_NAME}}"),
    TemplateVariable(R.string.sender_template_var_notification_body, "{{MSG}}"),
    TemplateVariable(R.string.sender_template_var_battery_percent, "{{BATTERY_PCT}}"),
    TemplateVariable(R.string.sender_template_var_battery_status, "{{BATTERY_STATUS}}"),
    TemplateVariable(R.string.sender_template_var_charging_source, "{{BATTERY_PLUGGED}}"),
    TemplateVariable(R.string.sender_template_var_battery_info, "{{BATTERY_INFO}}"),
    TemplateVariable(R.string.sender_template_var_battery_info_brief, "{{BATTERY_INFO_SIMPLE}}"),
    TemplateVariable(R.string.sender_template_var_public_ipv4, "{{IPV4}}"),
    TemplateVariable(R.string.sender_template_var_public_ipv6, "{{IPV6}}"),
    TemplateVariable(R.string.sender_template_var_ip_list, "{{IP_LIST}}"),
    TemplateVariable(R.string.sender_template_var_network_state, "{{NET_TYPE}}"),
    TemplateVariable(R.string.sender_template_var_received_at, "{{RECEIVE_TIME}}"),
    TemplateVariable(R.string.sender_template_var_current_time, "{{CURRENT_TIME}}"),
    TemplateVariable(R.string.sender_template_var_device_name, "{{DEVICE_NAME}}"),
    TemplateVariable(R.string.sender_template_var_app_version, "{{APP_VERSION}}"),
)

internal val appNotifyTemplateVariables = forwardTemplateVariables.map { variable ->
    when (variable.token) {
        "{{CARD_SLOT}}" -> variable.copy(labelRes = R.string.sender_template_var_app_note)
        "{{CARD_SUBID}}" -> variable.copy(labelRes = R.string.sender_template_var_app_key)
        else -> variable
    }
}

internal val callNotifyTemplateVariables = forwardTemplateVariables.map { variable ->
    when (variable.token) {
        "{{SMS}}" -> variable.copy(labelRes = R.string.sender_template_var_call_details)
        "{{CARD_SLOT}}" -> variable.copy(labelRes = R.string.sender_template_var_call_source)
        "{{CARD_SUBID}}" -> variable.copy(labelRes = R.string.sender_template_var_call_key)
        else -> variable
    }
}

internal val templateTokenRegex = Regex("\\{\\{[^{}]+\\}\\}")

internal fun appNotifyDefaultTemplate(): String =
    toAppNotifyTemplate(ForwardCommonConfigStore.defaultTemplate())

internal fun appNotifyFullTemplate(): String =
    toAppNotifyTemplate(ForwardCommonConfigStore.fullInfoTemplate())

internal fun callNotifyDefaultTemplate(): String =
    toCallNotifyTemplate(ForwardCommonConfigStore.defaultTemplate())

internal fun callNotifyFullTemplate(): String =
    toCallNotifyTemplate(ForwardCommonConfigStore.fullInfoTemplate())

private fun toAppNotifyTemplate(template: String): String =
    ForwardCommonConfigStore.adaptTemplateForMessageType(template, MessageType.APP_NOTIFY)

private fun toCallNotifyTemplate(template: String): String =
    ForwardCommonConfigStore.adaptTemplateForMessageType(
        template.replace("{{SMS}}", "{{CALL_TYPE}} {{SMS}}"),
        MessageType.CALL_NOTIFY,
    )

internal fun List<Sender>.moveItem(fromIndex: Int, toIndex: Int): List<Sender> {
    if (fromIndex == toIndex || fromIndex !in indices || toIndex !in indices) return this
    return toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}

internal fun priorityMapForOrder(senders: List<Sender>): Map<Long, Int> {
    return senders.mapIndexed { index, sender -> sender.id to index }.toMap()
}

internal fun reorderSenderToPriority(
    senders: List<Sender>,
    senderId: Long,
    priority: Int,
): List<Sender> {
    if (senders.isEmpty()) return senders
    val fromIndex = senders.indexOfFirst { it.id == senderId }
    if (fromIndex < 0) return senders
    val toIndex = priority.coerceIn(0, senders.lastIndex)
    return senders.moveItem(fromIndex, toIndex)
}

internal fun normalizeDispatchStrategy(strategy: Int): Int {
    return when (strategy) {
        DispatchStrategy.PRIMARY_ONLY,
        DispatchStrategy.BROADCAST_ALL,
        DispatchStrategy.FAILOVER,
        -> strategy
        else -> DispatchStrategy.BROADCAST_ALL
    }
}

internal fun senderTypeGroupLabel(context: Context, key: String): String {
    return when (key) {
        "collaboration" -> context.getString(R.string.sender_group_collaboration)
        "push" -> context.getString(R.string.sender_group_push)
        else -> context.getString(R.string.sender_group_other)
    }
}

internal fun buildSmsPreviewMessage(context: Context): MsgInfo {
    return MsgInfo(
        type = "sms",
        from = context.getString(R.string.sender_preview_sms_from),
        content = context.getString(R.string.sender_preview_sms_content),
        date = Date(),
        simInfo = context.getString(R.string.sender_preview_sms_sim_info),
        simSlot = 0,
        subId = 1,
        contactName = context.getString(R.string.sender_preview_sms_contact_name),
        phoneArea = context.getString(R.string.sender_preview_sms_phone_area),
    )
}

internal fun buildAppNotifyPreviewMessage(context: Context): MsgInfo {
    return MsgInfo(
        type = "app_notify",
        from = context.getString(R.string.sender_preview_app_from),
        content = context.getString(R.string.sender_preview_app_content),
        date = Date(),
        simInfo = context.getString(R.string.sender_preview_app_sim_info),
        packageName = "com.tencent.mm",
        appName = context.getString(R.string.sender_preview_app_name),
        title = context.getString(R.string.sender_preview_app_title),
        message = context.getString(R.string.sender_preview_app_message),
        contactName = context.getString(R.string.sender_preview_app_contact_name),
    )
}

internal fun buildCallNotifyPreviewMessage(context: Context): MsgInfo {
    return MsgInfo(
        type = "call_notify",
        from = "10086",
        content = context.getString(R.string.sender_preview_call_content),
        date = Date(),
        simInfo = context.getString(R.string.sender_preview_call_sim_info),
        simSlot = 0,
        subId = 42,
        callType = 3,
        contactName = context.getString(R.string.sender_preview_call_contact_name),
        phoneArea = context.getString(R.string.sender_preview_call_phone_area),
    )
}
