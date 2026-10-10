package io.github.magisk317.relay.platform.ipc

import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Property 3: Call Payload Metadata Correctness
 *
 * **Validates: Requirements 3.2, 3.3**
 *
 * For any phone number string and call state transition (RINGING or IDLE),
 * the generated `ForwardBroadcastPayload` SHALL contain the phone number in sender/body,
 * correct `msgType == "call_notify"`, and a non-blank `eventId`.
 */
class CallPayloadPropertyTest {

    private val phoneNumberArb = Arb.string(1..15).filter { it.all { c -> c.isDigit() } }
    private val callStageArb = Arb.of("ringing", "ended")
    private val callTypeArb = Arb.int(1..2)
    private val timestampArb = Arb.long(1L..Long.MAX_VALUE)

    @Test
    fun `Property 3 - call payload contains phone number, msgType call_notify, and non-blank eventId`() {
        runBlocking {
            checkAll(100, phoneNumberArb, callStageArb, callTypeArb, timestampArb) { phoneNumber, stage, callType, timestamp ->
                val packageName = "io.github.magisk317.xinyi.relay"
                val fallbackTitle = "Call Alert"
                val body = "Incoming: $phoneNumber"
                val company = "Call Alert"

                val payload = if (stage == "ringing") {
                    CallIngressAdapter.ringingPayload(
                        packageName = packageName,
                        fallbackTitle = fallbackTitle,
                        phoneNumber = phoneNumber,
                        incomingBody = body,
                        company = company,
                        timestamp = timestamp,
                        callType = callType,
                    )
                } else {
                    CallIngressAdapter.stagePayload(
                        packageName = packageName,
                        fallbackTitle = fallbackTitle,
                        phoneNumber = phoneNumber,
                        body = body,
                        company = company,
                        timestamp = timestamp,
                        callType = callType,
                        stage = stage,
                    )
                }

                // Phone number should be in sender (displayName uses phone number when non-blank)
                assertEquals(phoneNumber, payload.sender)
                // Body should contain the phone number
                assertEquals(body, payload.body)
                // msgType must be "call_notify"
                assertEquals(ForwardBroadcastContract.MSG_TYPE_CALL_NOTIFY, payload.msgType)
                // eventId must be non-blank
                assertTrue(payload.eventId.isNotBlank(), "eventId should not be blank but was <${payload.eventId}>")
            }
        }
    }
}
