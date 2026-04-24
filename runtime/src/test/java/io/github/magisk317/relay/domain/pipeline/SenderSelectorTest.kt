package io.github.magisk317.relay.domain.pipeline

import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.domain.event.RelayEvent
import io.github.magisk317.relay.domain.model.Sender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
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

    private fun sender(id: Long, receiveAppNotify: Int): Sender {
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
        )
    }
}
