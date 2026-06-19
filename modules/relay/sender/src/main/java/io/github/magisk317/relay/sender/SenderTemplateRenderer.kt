package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.MsgInfo
import java.text.SimpleDateFormat
import java.util.Locale

object SenderTemplateRenderer {
    private const val TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"

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
        return mapOf(
            "FROM" to msgInfo.from,
            "SMS" to msgInfo.content,
            "CONTENT" to msgInfo.content,
            "MSG" to msgInfo.content,
            "ORG_CONTENT" to msgInfo.content,
            "SMS_CODE" to msgInfo.smsCode,
            "CODE" to msgInfo.smsCode,
            "TITLE" to msgInfo.simInfo,
            "CARD_SLOT" to msgInfo.simInfo,
            "APP_ICON" to msgInfo.appIcon,
            "TIMESTAMP" to timestamp.toString(),
            "RECEIVE_TIME" to receiveTime,
            "PACKAGE_NAME" to msgInfo.packageName,
            "APP_NAME" to msgInfo.appName,
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
            "title" to msgInfo.simInfo,
            "card_slot" to msgInfo.simInfo,
            "app_icon" to msgInfo.appIcon,
            "timestamp" to timestamp.toString(),
            "receive_time" to receiveTime,
            "package_name" to msgInfo.packageName,
            "app_name" to msgInfo.appName,
        )
    }

    private fun defaultReceiveTime(msgInfo: MsgInfo): String {
        return SimpleDateFormat(TIME_PATTERN, Locale.getDefault()).format(msgInfo.date)
    }
}
