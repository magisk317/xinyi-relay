package io.github.magisk317.relay.entitlement

import io.github.magisk317.relay.core.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MobileEntitlementPolicyTest {
    @Test
    fun `active and grace statuses use localized active resource`() {
        assertTrue(isMobileEntitlementActivated(MobileEntitlementStatus.ACTIVE))
        assertTrue(isMobileEntitlementActivated(MobileEntitlementStatus.GRACE))
        assertEquals(R.string.module_status_active, mobileEntitlementStatusStringRes(MobileEntitlementStatus.ACTIVE))
        assertEquals(R.string.module_status_active, mobileEntitlementStatusStringRes(MobileEntitlementStatus.GRACE))
    }

    @Test
    fun `non-active statuses use localized inactive resource instead of enum names`() {
        val inactiveStatuses = listOf(
            MobileEntitlementStatus.MIGRATION,
            MobileEntitlementStatus.UNACTIVATED,
            MobileEntitlementStatus.EXPIRED,
            MobileEntitlementStatus.INVALID,
        )

        inactiveStatuses.forEach { status ->
            assertFalse(isMobileEntitlementActivated(status))
            assertEquals(R.string.module_status_inactive, mobileEntitlementStatusStringRes(status))
        }
        assertEquals(R.string.mobile_entitlement_not_loaded, mobileEntitlementStatusStringRes(null))
    }
}
