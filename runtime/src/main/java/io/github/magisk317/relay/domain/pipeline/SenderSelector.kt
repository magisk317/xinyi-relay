package io.github.magisk317.relay.domain.pipeline

import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.domain.event.RelayEvent
import io.github.magisk317.relay.domain.model.Sender
class SenderSelector {
    fun selectBaseSenders(
        enabledSenders: List<Sender>,
        event: RelayEvent,
    ): List<Sender> {
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
        }
    }

    fun buildNoEligibleReason(
        allSenders: List<Sender>,
        enabledSenders: List<Sender>,
        event: RelayEvent,
    ): String {
        if (allSenders.isEmpty()) return "未配置任何转发通道"
        if (enabledSenders.isEmpty()) {
            val allNames = allSenders.joinToString(",") { it.name.ifBlank { fallbackSenderTypeName(it.type) } }
            return "所有转发通道均未启用（$allNames）"
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
