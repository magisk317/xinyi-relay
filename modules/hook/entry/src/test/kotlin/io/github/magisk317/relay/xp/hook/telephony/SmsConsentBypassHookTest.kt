package io.github.magisk317.relay.xp.hook.telephony

import android.app.PendingIntent
import android.content.Context
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsConsentBypassHookTest {

    private val own = "io.github.magisk317.xinyi.relay"

    @Test
    fun bypassesWhenEnabledAndOwnPackage() {
        assertTrue(shouldBypassConsent(enabled = true, callingPackage = own, ownPackage = own))
    }

    @Test
    fun noBypassWhenDisabled() {
        assertFalse(shouldBypassConsent(enabled = false, callingPackage = own, ownPackage = own))
    }

    @Test
    fun noBypassWhenOtherPackage() {
        assertFalse(
            shouldBypassConsent(
                enabled = true,
                callingPackage = "com.example.other",
                ownPackage = own,
            ),
        )
    }

    @Test
    fun noBypassWhenCallingPackageNull() {
        assertFalse(shouldBypassConsent(enabled = true, callingPackage = null, ownPackage = own))
    }

    @Test
    fun findsOnlyTheExactArchivedSignature() {
        val method = findConsentMethod(TargetWithOverload::class.java)

        assertNotNull(method)
        assertEquals(11, method?.parameterCount)
        assertEquals(Int::class.javaPrimitiveType, method?.parameterTypes?.get(2))
        assertEquals(Boolean::class.javaPrimitiveType, method?.parameterTypes?.get(9))
        assertEquals(Long::class.javaPrimitiveType, method?.parameterTypes?.get(10))
    }

    @Test
    fun rejectsSameNameWithWrongSignature() {
        assertNull(findConsentMethod(WrongTarget::class.java))
    }

    private class TargetWithOverload {
        @Suppress("UNUSED_PARAMETER")
        fun startConsentUiIfNeeded(
            context: Context,
            callingPackage: String,
            subId: Int,
            callingAttributionTag: String,
            destAddr: String,
            scAddr: String,
            text: String,
            sentIntent: PendingIntent,
            deliveryIntent: PendingIntent,
            persistMessageForNonDefaultSmsApp: Boolean,
            messageId: Long,
        ): Boolean = true

        @Suppress("UNUSED_PARAMETER")
        fun startConsentUiIfNeeded(callingPackage: String): Boolean = true
    }

    private class WrongTarget {
        @Suppress("UNUSED_PARAMETER")
        fun startConsentUiIfNeeded(callingPackage: String): Boolean = true
    }
}
