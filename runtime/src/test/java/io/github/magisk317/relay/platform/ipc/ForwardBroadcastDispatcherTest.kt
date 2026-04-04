package io.github.magisk317.relay.platform.ipc

import android.content.Context
import dev.mokkery.MockMode.autofill
import dev.mokkery.mock
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForwardBroadcastDispatcherTest {

    @Test
    fun dispatchFromSmsHook_usesTokenWhenPresent() {
        val context = mock<Context>(autofill)
        var dispatchedToken: String? = null

        val result = ForwardBroadcastDispatcher.dispatchFromSmsHook(
            context = context,
            payload = ForwardBroadcastPayload(eventId = "sms_test"),
            sentFromUid = 20000,
            sdkInt = 34,
            tokenResolver = { "token123" },
            dispatchBlock = { token -> dispatchedToken = token },
        )

        assertTrue(result.dispatched)
        assertTrue(result.tokenPresent)
        assertFalse(result.bypassUsed)
        assertTrue(dispatchedToken == "token123")
    }

    @Test
    fun dispatchFromSmsHook_allowsLegacySystemBypassWithoutToken() {
        val context = mock<Context>(autofill)
        var dispatchCount = 0

        val result = ForwardBroadcastDispatcher.dispatchFromSmsHook(
            context = context,
            payload = ForwardBroadcastPayload(eventId = "sms_test"),
            sentFromUid = ForwardReceiverPolicy.PHONE_UID,
            sdkInt = 34,
            tokenResolver = { "" },
            dispatchBlock = { dispatchCount += 1 },
        )

        assertTrue(result.dispatched)
        assertFalse(result.tokenPresent)
        assertTrue(result.bypassUsed)
        assertTrue(dispatchCount == 1)
    }

    @Test
    fun dispatchFromSmsHook_blocksWhenTokenMissingAndBypassDenied() {
        val context = mock<Context>(autofill)
        var dispatchCount = 0

        val result = ForwardBroadcastDispatcher.dispatchFromSmsHook(
            context = context,
            payload = ForwardBroadcastPayload(eventId = "sms_test"),
            sentFromUid = 20000,
            sdkInt = 34,
            tokenResolver = { "" },
            dispatchBlock = { dispatchCount += 1 },
        )

        assertFalse(result.dispatched)
        assertFalse(result.tokenPresent)
        assertFalse(result.bypassUsed)
        assertTrue(dispatchCount == 0)
    }
}
