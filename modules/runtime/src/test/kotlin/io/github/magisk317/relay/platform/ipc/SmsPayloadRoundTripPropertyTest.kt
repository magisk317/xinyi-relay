package io.github.magisk317.relay.platform.ipc

import io.github.magisk317.smscode.db.entity.SmsMsg
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Property 2: SMS Payload Round Trip
 *
 * **Validates: Requirements 2.2, 2.3**
 *
 * For any valid SMS message with non-null sender, body, and positive timestamp,
 * constructing an `SmsMsg` and passing it through `ForwardPayloadFactory.smsPayload()`
 * SHALL produce a `ForwardBroadcastPayload` where `payload.sender == sms.sender`,
 * `payload.body == sms.body`, `payload.date == sms.date`, and `payload.msgType == "sms"`.
 */
class SmsPayloadRoundTripPropertyTest {

    @Test
    fun `Property 2 - smsPayload preserves sender, body, date, and sets msgType to sms`() {
        runBlocking {
            checkAll(100, Arb.string(1..100), Arb.string(1..200), Arb.long(1L..Long.MAX_VALUE)) { sender, body, date ->
                val smsMsg = SmsMsg(
                    sender = sender,
                    body = body,
                    date = date,
                )

                val payload = ForwardPayloadFactory.smsPayload(smsMsg)

                assertEquals(sender, payload.sender)
                assertEquals(body, payload.body)
                assertEquals(date, payload.date)
                assertEquals(ForwardBroadcastContract.MSG_TYPE_SMS, payload.msgType)
            }
        }
    }
}
