package io.github.magisk317.relay.domain.pipeline

import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.domain.event.RelayEvent
import io.github.magisk317.relay.domain.model.Sender
import io.github.magisk317.relay.domain.sender.SenderActiveScheduleEvaluator
import java.time.LocalDateTime

class SenderSelector(
    private val nowProvider: () -> LocalDateTime = { LocalDateTime.now() },
) {
    fun selectBaseSenders(
        enabledSenders: List<Sender>,
        event: RelayEvent,
    ): List<Sender> {
        val now = nowProvider()
        val scopedSenders = event.targetSenderIds
            ?.takeIf { it.isNotEmpty() }
            ?.let { targetIds -> enabledSenders.filter { it.id in targetIds } }
            ?: enabledSenders
        return scopedSenders.filter { sender ->
            when (event.messageType) {
                MessageType.SMS_CODE -> sender.receiveCode == 1
                MessageType.SMS_PLAIN -> sender.receiveNonCode == 1
                MessageType.APP_NOTIFY -> sender.receiveAppNotify == 1
                MessageType.CALL_NOTIFY -> sender.receiveCallNotify == 1
            }
        }.filter { sender ->
            SenderActiveScheduleEvaluator.isAllowed(sender.activeSchedule, event.messageType, now)
        }
    }

    fun buildNoEligibleReason(
        allSenders: List<Sender>,
        enabledSenders: List<Sender>,
        event: RelayEvent,
    ): String {
        val now = nowProvider()
        if (allSenders.isEmpty()) return "未配置任何转发通道"
        if (enabledSenders.isEmpty()) {
            val allNames = allSenders.joinToString(",") { it.name.ifBlank { fallbackSenderTypeName(it.type) } }
            return "所有转发通道均未启用（$allNames）"
        }
        val scopedSenders = event.targetSenderIds
            ?.takeIf { it.isNotEmpty() }
            ?.let { targetIds -> enabledSenders.filter { it.id in targetIds } }
            ?: enabledSenders
        val flagMatchedSenders = scopedSenders.filter { sender ->
            when (event.messageType) {
                MessageType.SMS_CODE -> sender.receiveCode == 1
                MessageType.SMS_PLAIN -> sender.receiveNonCode == 1
                MessageType.APP_NOTIFY -> sender.receiveAppNotify == 1
                MessageType.CALL_NOTIFY -> sender.receiveCallNotify == 1
            }
        }
        if (flagMatchedSenders.isNotEmpty() && flagMatchedSenders.none {
                SenderActiveScheduleEvaluator.isAllowed(it.activeSchedule, event.messageType, now)
            }
        ) {
            return when (event.messageType) {
                MessageType.SMS_CODE,
                MessageType.SMS_PLAIN,
                -> "已启用通道均不在短信生效时间段内"
                MessageType.APP_NOTIFY -> "已启用通道均不在应用通知生效时间段内"
                MessageType.CALL_NOTIFY -> "已启用通道均不在通话通知生效时间段内"
            }
        }
        return when (event.messageType) {
            MessageType.APP_NOTIFY -> {
                val allowed = enabledSenders.filter { it.receiveAppNotify == 1 }
                val blockedNames = enabledSenders
                    .filter { it.receiveAppNotify != 1 }
                    .joinToString(",") { it.name.ifBlank { fallbackSenderTypeName(it.type) } }
                "已启用通道均关闭了“转发应用通知”开关（enabled=${enabledSenders.size}, matched=${allowed.size}, blocked=$blockedNames）"
            }

            MessageType.CALL_NOTIFY -> {
                val allowed = enabledSenders.filter { it.receiveCallNotify == 1 }
                val blockedNames = enabledSenders
                    .filter { it.receiveCallNotify != 1 }
                    .joinToString(",") { it.name.ifBlank { fallbackSenderTypeName(it.type) } }
                "已启用通道均关闭了“转发通话通知”开关（enabled=${enabledSenders.size}, matched=${allowed.size}, blocked=$blockedNames）"
            }

            MessageType.SMS_CODE -> {
                val allowed = enabledSenders.filter { it.receiveCode == 1 }
                val blockedNames = enabledSenders
                    .filter { it.receiveCode != 1 }
                    .joinToString(",") { it.name.ifBlank { fallbackSenderTypeName(it.type) } }
                "已启用通道均关闭了“转发验证码短信”开关（enabled=${enabledSenders.size}, matched=${allowed.size}, blocked=$blockedNames）"
            }

            MessageType.SMS_PLAIN -> {
                val allowed = enabledSenders.filter { it.receiveNonCode == 1 }
                val blockedNames = enabledSenders
                    .filter { it.receiveNonCode != 1 }
                    .joinToString(",") { it.name.ifBlank { fallbackSenderTypeName(it.type) } }
                "已启用通道均关闭了“转发非验证码短信”开关（enabled=${enabledSenders.size}, matched=${allowed.size}, blocked=$blockedNames）"
            }
        }
    }

    private fun fallbackSenderTypeName(type: Int): String = "通道$type"
}
