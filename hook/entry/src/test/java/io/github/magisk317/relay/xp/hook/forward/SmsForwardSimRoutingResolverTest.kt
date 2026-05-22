package io.github.magisk317.relay.xp.hook.forward

import io.github.magisk317.relay.testing.relaxedIntent
import io.github.magisk317.relay.testing.stubHookSimRouting
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class SmsForwardSimRoutingResolverTest {
    private companion object {
        const val EXTRA_SIM_SLOT = "sim_slot"
        const val EXTRA_SUB_ID = "sub_id"
    }

    @Test
    fun ensureSimRoutingExtras_readsPhoneFromHandler() {
        val intent = relaxedIntent()
        every { intent.hasExtra(any()) } returns false

        val resolved = SmsForwardSimRoutingResolver.ensureSimRoutingExtras(
            intent = intent,
            handler = FakeHandler(FakePhone(subId = 7, phoneId = 1)),
            args = emptyArray(),
        )

        assertNotNull(resolved)
        assertEquals(1, resolved?.simSlot)
        assertEquals(7, resolved?.subId)
        verify { intent.putExtra(EXTRA_SIM_SLOT, 1) }
        verify { intent.putExtra(EXTRA_SUB_ID, 7) }
    }

    @Test
    fun ensureSimRoutingExtras_prefersArgsWhenIntentMissing() {
        val intent = relaxedIntent()
        every { intent.hasExtra(any()) } returns false

        val resolved = SmsForwardSimRoutingResolver.ensureSimRoutingExtras(
            intent = intent,
            handler = FakeHandler(FakePhone(subId = 3, phoneId = 0)),
            args = arrayOf(FakeDispatchArgs(slotIndex = 2, subscriptionId = 9)),
        )

        assertNotNull(resolved)
        assertEquals(2, resolved?.simSlot)
        assertEquals(9, resolved?.subId)
        verify { intent.putExtra(EXTRA_SIM_SLOT, 2) }
        verify { intent.putExtra(EXTRA_SUB_ID, 9) }
    }

    @Test
    fun ensureSimRoutingExtras_keepsExistingIntentExtras() {
        val intent = relaxedIntent()
        intent.stubHookSimRouting(simSlot = 0, subId = 11)

        val resolved = SmsForwardSimRoutingResolver.ensureSimRoutingExtras(
            intent = intent,
            handler = FakeHandler(FakePhone(subId = 7, phoneId = 1)),
            args = arrayOf(FakeDispatchArgs(slotIndex = 2, subscriptionId = 9)),
        )

        assertNotNull(resolved)
        assertEquals(0, resolved?.simSlot)
        assertEquals(11, resolved?.subId)
        verify(exactly = 0) { intent.putExtra(any<String>(), any<Int>()) }
    }

    @Test
    fun resolve_readsRoutingFromVendorToString() {
        val resolved = SmsForwardSimRoutingResolver.resolve(
            handler = VendorPhoneDump(),
            args = emptyArray(),
        )

        assertNotNull(resolved)
        assertEquals(0, resolved?.simSlot)
        assertEquals(1, resolved?.subId)
    }

    private class FakePhone(
        private val subId: Int,
        private val phoneId: Int,
    ) {
        fun getSubId(): Int = subId
        fun getPhoneId(): Int = phoneId
    }

    private class FakeHandler(private val mPhone: FakePhone)

    private class FakeDispatchArgs(
        private val slotIndex: Int,
        private val subscriptionId: Int,
    ) {
        fun getSlotIndex(): Int = slotIndex
        fun getSubscriptionId(): Int = subscriptionId
    }

    private class VendorPhoneDump {
        override fun toString(): String {
            return "Handler (com.qualcomm.qti.internal.telephony.QtiGsmCdmaPhone) {30bfcc0} phondId=0 subId=1"
        }
    }
}
