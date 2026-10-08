package io.github.magisk317.relay.engine.pipeline

import io.github.magisk317.relay.contract.constant.MessageType
import io.github.magisk317.relay.engine.event.RelayEvent
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.engine.sender.SenderActiveSchedule
import io.github.magisk317.relay.engine.sender.SenderActiveScheduleConst
import io.github.magisk317.relay.engine.sender.SenderActiveScheduleRange
import io.github.magisk317.relay.engine.sender.SenderActiveScheduleRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Date

class SenderSelectorTest {

    @Test
    fun selectBaseSenders_respectsTargetSenderIdsForAppNotify() {
        val selector = SenderSelector()
        val senders = listOf(
            sender(id = 1L, receiveAppNotify = 1),
            sender(id = 2L, receiveAppNotify = 1),
            sender(id = 3L, receiveAppNotify = 0),
        )

        val selected = selector.selectBaseSenders(
            enabledSenders = senders,
            event = RelayEvent(
                messageType = MessageType.APP_NOTIFY,
                sourceType = "custom_broadcast",
                sender = "custom",
                body = "hello",
                timestamp = 1L,
                packageName = "com.example.tool",
                notifyChannelId = "custom_broadcast",
                companyOrAppName = "Custom",
                smsCode = null,
                callType = 0,
                callStage = "",
                simSlot = -1,
                subId = 0,
                targetSenderIds = listOf(2L, 3L),
            ),
        )

        assertEquals(listOf(2L), selected.map { it.id })
    }

    @Test
    fun selectBaseSenders_filtersByActiveScheduleAfterTargeting() {
        val selector = SenderSelector {
            LocalDateTime.of(2026, 4, 27, 20, 0)
        }
        val senders = listOf(
            sender(
                id = 1L,
                receiveAppNotify = 1,
                activeSchedule = SenderActiveSchedule(
                    appNotify = SenderActiveScheduleRule(
                        enabled = true,
                        mode = SenderActiveScheduleConst.MODE_WHITELIST,
                        weekdays = SenderActiveScheduleConst.ALL_WEEKDAYS,
                        ranges = listOf(SenderActiveScheduleRange("09:00", "18:00")),
                    ),
                ),
            ),
            sender(
                id = 2L,
                receiveAppNotify = 1,
                activeSchedule = SenderActiveSchedule(),
            ),
        )

        val selected = selector.selectBaseSenders(
            enabledSenders = senders,
            event = RelayEvent(
                messageType = MessageType.APP_NOTIFY,
                sourceType = "custom_broadcast",
                sender = "custom",
                body = "hello",
                timestamp = 1L,
                packageName = "com.example.tool",
                notifyChannelId = "custom_broadcast",
                companyOrAppName = "Custom",
                smsCode = null,
                callType = 0,
                callStage = "",
                simSlot = -1,
                subId = 0,
                targetSenderIds = listOf(1L),
            ),
        )

        assertEquals(emptyList<Long>(), selected.map { it.id })
    }

    @Test
    fun buildNoEligibleReason_usesScheduleSpecificMessageWhenOnlyScheduleBlocks() {
        val selector = SenderSelector {
            LocalDateTime.of(2026, 4, 27, 20, 0)
        }
        val senders = listOf(
            sender(
                id = 1L,
                receiveAppNotify = 1,
                activeSchedule = SenderActiveSchedule(
                    appNotify = SenderActiveScheduleRule(
                        enabled = true,
                        mode = SenderActiveScheduleConst.MODE_WHITELIST,
                        weekdays = SenderActiveScheduleConst.ALL_WEEKDAYS,
                        ranges = listOf(SenderActiveScheduleRange("09:00", "18:00")),
                    ),
                ),
            ),
        )

        val reason = selector.buildNoEligibleReason(
            allSenders = senders,
            enabledSenders = senders,
            event = RelayEvent(
                messageType = MessageType.APP_NOTIFY,
                sourceType = "custom_broadcast",
                sender = "custom",
                body = "hello",
                timestamp = 1L,
                packageName = "com.example.tool",
                notifyChannelId = "custom_broadcast",
                companyOrAppName = "Custom",
                smsCode = null,
                callType = 0,
                callStage = "",
                simSlot = -1,
                subId = 0,
                targetSenderIds = listOf(1L),
            ),
        )

        assertEquals("已启用通道均不在应用通知生效时间段内", reason)
    }

    private fun sender(
        id: Long,
        receiveAppNotify: Int,
        activeSchedule: SenderActiveSchedule = SenderActiveSchedule(),
    ): Sender {
        return Sender(
            id = id,
            name = "sender$id",
            jsonSetting = "{}",
            time = Date(),
            status = 1,
            receiveCode = 1,
            receiveNonCode = 1,
            receiveAppNotify = receiveAppNotify,
            receiveCallNotify = 0,
            activeSchedule = activeSchedule,
        )
    }
}
