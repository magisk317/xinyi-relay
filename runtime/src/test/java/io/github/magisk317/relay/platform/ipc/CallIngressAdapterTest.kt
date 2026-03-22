package io.github.magisk317.relay.platform.ipc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CallIngressAdapterTest {

    @Test
    fun ringingPayload_usesFallbackTitleWhenNumberMissing() {
        val payload = CallIngressAdapter.ringingPayload(
            packageName = "io.github.magisk317.xinyi.relay",
            fallbackTitle = "Call Alert",
            phoneNumber = null,
            incomingBody = "Incoming: Call Alert",
            company = "Call Alert",
            timestamp = 100L,
            callType = 1,
        )

        assertEquals("Call Alert", payload.sender)
        assertEquals("Incoming: Call Alert", payload.body)
        assertEquals("ringing", payload.callStage)
    }

    @Test
    fun stagePayload_usesEndedCopyForEndedStage() {
        val payload = CallIngressAdapter.stagePayload(
            packageName = "io.github.magisk317.xinyi.relay",
            fallbackTitle = "Call Alert",
            phoneNumber = "10086",
            body = "Ended: 10086",
            company = "Call Alert",
            timestamp = 200L,
            callType = 2,
            stage = "ended",
        )

        assertEquals("10086", payload.sender)
        assertEquals("Ended: 10086", payload.body)
        assertEquals("ended", payload.callStage)
        assertEquals(2, payload.callType)
    }
}
