package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.CallTypeLabelFormatter
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.smscode.runtime.contract.sim.SimSlotLabelFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SenderTemplateRenderer {
    // 与 MessageFormatter.TIME_PATTERN 保持一致，避免同名变量在标题/正文两条管线输出不同格式
    private const val TIME_PATTERN = "yyyy.MM.dd HH:mm:ss"

    fun renderTitle(template: String, msgInfo: MsgInfo): String {
        return render(
            raw = template.ifBlank { "信息驿站: ${msgInfo.from}" },
            msgInfo = msgInfo,
        )
    }

    fun render(
        raw: String,
        msgInfo: MsgInfo,
        timestamp: Long = System.currentTimeMillis(),
        receiveTime: String = defaultReceiveTime(msgInfo),
        valueTransform: (String) -> String = { it },
    ): String {
        var rendered = raw
        braceValues(msgInfo, timestamp, receiveTime).forEach { (name, value) ->
            rendered = rendered
                .replace("{{$name}}", valueTransform(value))
                .replace("{{${name.lowercase(Locale.ROOT)}}}", valueTransform(value))
        }
        bracketValues(msgInfo, timestamp, receiveTime).forEach { (name, value) ->
            rendered = rendered.replace("[$name]", valueTransform(value))
        }
        return rendered
    }

    private fun braceValues(
        msgInfo: MsgInfo,
        timestamp: Long,
        receiveTime: String,
    ): Map<String, String> {
        val env = msgInfo.systemEnv
        return mapOf(
            "FROM" to msgInfo.from,
            "SMS" to msgInfo.content,
            "CONTENT" to msgInfo.content,
            "MSG" to msgInfo.content,
            "ORG_CONTENT" to msgInfo.content,
            "SMS_CODE" to msgInfo.smsCode,
            "CODE" to msgInfo.smsCode,
            "TITLE" to titleValue(msgInfo),
            "CARD_SLOT" to cardSlotValue(msgInfo),
            "CARD_SUBID" to if (msgInfo.subId > 0) msgInfo.subId.toString() else "",
            "CALL_TYPE" to CallTypeLabelFormatter.format(msgInfo.callType),
            "CONTACT_NAME" to msgInfo.contactName,
            "PHONE_AREA" to msgInfo.phoneArea,
            "APP_ICON" to msgInfo.appIcon,
            "TIMESTAMP" to timestamp.toString(),
            "RECEIVE_TIME" to receiveTime,
            "CURRENT_TIME" to env?.currentTime?.let(::formatTime).orEmpty(),
            "PACKAGE_NAME" to msgInfo.packageName,
            "APP_NAME" to msgInfo.appName.ifBlank { msgInfo.packageName },
            "DEVICE_NAME" to env?.deviceName.orEmpty(),
            "APP_VERSION" to env?.appVersion.orEmpty(),
            "BATTERY_PCT" to env?.battery?.percent.orEmpty(),
            "BATTERY_STATUS" to env?.battery?.status.orEmpty(),
            "BATTERY_PLUGGED" to env?.battery?.plugged.orEmpty(),
            "BATTERY_INFO" to env?.battery?.fullInfo.orEmpty(),
            "BATTERY_INFO_SIMPLE" to env?.battery?.simpleInfo.orEmpty(),
            "IPV4" to env?.network?.ipv4.orEmpty(),
            "IPV6" to env?.network?.ipv6.orEmpty(),
            "IP_LIST" to env?.network?.ipList.orEmpty(),
            "NET_TYPE" to env?.network?.netType.orEmpty(),
            "NOTIFICATION_TITLE" to msgInfo.title,
            "NOTIFICATION_BODY" to msgInfo.message,
        )
    }

    private fun bracketValues(
        msgInfo: MsgInfo,
        timestamp: Long,
        receiveTime: String,
    ): Map<String, String> {
        return mapOf(
            "from" to msgInfo.from,
            "content" to msgInfo.content,
            "msg" to msgInfo.content,
            "org_content" to msgInfo.content,
            "sms_code" to msgInfo.smsCode,
            "code" to msgInfo.smsCode,
            "title" to titleValue(msgInfo),
            "card_slot" to cardSlotValue(msgInfo),
            "card_subid" to if (msgInfo.subId > 0) msgInfo.subId.toString() else "",
            "call_type" to CallTypeLabelFormatter.format(msgInfo.callType),
            "contact_name" to msgInfo.contactName,
            "phone_area" to msgInfo.phoneArea,
            "app_icon" to msgInfo.appIcon,
            "timestamp" to timestamp.toString(),
            "receive_time" to receiveTime,
            "package_name" to msgInfo.packageName,
            "app_name" to msgInfo.appName.ifBlank { msgInfo.packageName },
        )
    }

    /**
     * 真实卡槽：simSlot 有效时输出 SIM1/SIM2（或卡槽备注，与正文 MessageFormatter 语义一致）；
     * 无卡槽信息时回退 simInfo 原语义（短信签名/应用名），原样输出不加工。
     */
    private fun cardSlotValue(msgInfo: MsgInfo): String {
        if (msgInfo.simSlot >= 0) return SimSlotLabelFormatter.format(msgInfo.simSlot)
        return simInfoValue(msgInfo)
    }

    /**
     * TITLE 的标题语义：优先通知自身标题（应用通知）；短信回退到签名原样输出
     * （需要【】由用户在模板里自己输入）；最终回退发件人。
     */
    private fun titleValue(msgInfo: MsgInfo): String {
        return msgInfo.title.ifBlank { simInfoValue(msgInfo).ifBlank { msgInfo.from } }
    }

    private fun simInfoValue(msgInfo: MsgInfo): String = msgInfo.simInfo.trim()

    private fun formatTime(epochMillis: Long): String {
        return SimpleDateFormat(TIME_PATTERN, Locale.getDefault()).format(Date(epochMillis))
    }

    private fun defaultReceiveTime(msgInfo: MsgInfo): String {
        return formatTime(msgInfo.date.toEpochMilliseconds())
    }
}
