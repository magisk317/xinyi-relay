package io.github.magisk317.relay.engine.service

import io.github.magisk317.relay.contract.constant.MessageType
import io.github.magisk317.relay.engine.event.RelayEvent
import io.github.magisk317.relay.engine.model.ForwardCommonConfig
import io.github.magisk317.relay.engine.model.SystemEnvironment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageFormatter(
    private val systemInfoProvider: SystemInfoProvider,
    private val simSlotRemarkResolver: (Int) -> String = { "" },
) {
    private companion object {
        private const val CALL_TYPE_ANSWERED_EXTERNALLY = 7
        private const val TIME_PATTERN = "yyyy.MM.dd HH:mm:ss"
        private val EMPTY_VALUE_LINE_REGEX = Regex("^[^:：\\n]+[:：]\\s*$")
        private val DEFAULT_TEMPLATE = """
            来自：{{FROM}}
            内容：{{SMS}}
            卡槽：{{CARD_SLOT}}
            SubId：{{CARD_SUBID}}
            接收时间：{{RECEIVE_TIME}}
            设备：{{DEVICE_NAME}}
        """.trimIndent()
    }

    fun format(
        event: RelayEvent,
        payloadContext: DispatchPayloadContext,
        config: ForwardCommonConfig,
        env: SystemEnvironment,
        customTemplate: String? = null,
    ): String {
        val template = when {
            customTemplate?.isNotBlank() == true -> customTemplate
            config.messageTemplate.isNotBlank() -> config.messageTemplate
            config.includeSender || config.includeTime || !config.includeDeviceName -> {
                buildString {
                    append("{{SMS}}")
                    if (config.includeSender) append("\n发件人: {{FROM}}")
                    if (config.includeTime) append("\n时间: {{RECEIVE_TIME}}")
                    if (config.includeDeviceName) append("\n来自{{DEVICE_NAME}}设备")
                }
            }

            else -> DEFAULT_TEMPLATE
        }

        val appName = if (payloadContext.appName.isNotBlank()) payloadContext.appName else systemInfoProvider.resolveAppName(event.packageName)
        val receiveTime = SimpleDateFormat(TIME_PATTERN, Locale.getDefault()).format(Date(event.timestamp))
        val currentTimeStr = SimpleDateFormat(TIME_PATTERN, Locale.getDefault()).format(Date(env.currentTime))

        val variables = mapOf(
            "FROM" to event.sender,
            "SMS" to event.body,
            "CARD_SLOT" to resolveCardSlot(event, payloadContext),
            "CARD_SUBID" to if (event.subId > 0) event.subId.toString() else "",
            "CALL_TYPE" to resolveCallTypeLabel(event.callType),
            "CONTACT_NAME" to event.contactName,
            "PHONE_AREA" to event.phoneArea,
            "PACKAGE_NAME" to event.packageName,
            "APP_NAME" to appName,
            "TITLE" to payloadContext.title.ifBlank { event.companyOrAppName.ifBlank { event.sender } },
            "MSG" to payloadContext.message.ifBlank { event.body },
            "BATTERY_PCT" to env.battery.percent,
            "BATTERY_STATUS" to env.battery.status,
            "BATTERY_PLUGGED" to env.battery.plugged,
            "BATTERY_INFO" to env.battery.fullInfo,
            "BATTERY_INFO_SIMPLE" to env.battery.simpleInfo,
            "IPV4" to env.network.ipv4,
            "IPV6" to env.network.ipv6,
            "IP_LIST" to env.network.ipList,
            "NET_TYPE" to env.network.netType,
            "RECEIVE_TIME" to receiveTime,
            "CURRENT_TIME" to currentTimeStr,
            "DEVICE_NAME" to env.deviceName,
            "APP_VERSION" to env.appVersion,
        )

        var rendered = template
        variables.forEach { (name, value) ->
            rendered = rendered.replace("{{$name}}", value)
        }

        rendered = when (event.messageType) {
            MessageType.APP_NOTIFY -> rendered
                .replace(Regex("(?m)^(\\s*)卡槽([:：])"), "$1应用$2")
                .replace("【卡槽与来源】", "【应用与来源】")
            MessageType.CALL_NOTIFY -> rendered
                .replace(Regex("(?m)^(\\s*)卡槽([:：])"), "$1通话$2")
                .replace("【卡槽与来源】", "【通话与来源】")
            else -> rendered
        }

        return removeEmptyValueLines(rendered)
    }

    private fun resolveCardSlot(
        event: RelayEvent,
        payloadContext: DispatchPayloadContext,
    ): String {
        if (event.simSlot >= 0) {
            val remark = simSlotRemarkResolver(event.simSlot).trim()
            if (remark.isNotBlank()) return remark
            return "SIM${event.simSlot + 1}"
        }
        if (payloadContext.appName.isNotBlank() && event.companyOrAppName.isNotBlank()) return event.companyOrAppName
        return ""
    }

    private fun resolveCallTypeLabel(callType: Int): String {
        return when (callType) {
            1 -> "来电"
            2 -> "去电"
            3 -> "未接"
            4 -> "语音信箱"
            5 -> "拒接"
            6 -> "拦截"
            CALL_TYPE_ANSWERED_EXTERNALLY -> "异地接听"
            else -> ""
        }
    }

    private fun removeEmptyValueLines(text: String): String {
        return text.lineSequence()
            .map { it.trimEnd() }
            .filter { line ->
                val trimmed = line.trim()
                if (trimmed.isBlank()) return@filter true
                !EMPTY_VALUE_LINE_REGEX.matches(trimmed)
            }
            .toList()
            .joinToString("\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trimEnd()
    }
}
