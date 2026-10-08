package io.github.magisk317.relay.android.platform.ipc

import android.content.pm.ApplicationInfo
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProviderCallerPolicyTest {
    @Test
    fun `only system packages in the xinyi scope are recognized`() {
        val system = ApplicationInfo.FLAG_SYSTEM
        val updatedSystem = ApplicationInfo.FLAG_UPDATED_SYSTEM_APP

        assertTrue(ProviderCallerPolicy.isSystemScopePackage("android", system))
        assertTrue(ProviderCallerPolicy.isSystemScopePackage("com.android.phone", system))
        assertTrue(ProviderCallerPolicy.isSystemScopePackage("com.xiaomi.phone", updatedSystem))
        assertTrue(ProviderCallerPolicy.isSystemScopePackage("com.android.providers.telephony", system))
        assertTrue(ProviderCallerPolicy.isSystemScopePackage("com.android.mms", system))
        assertFalse(ProviderCallerPolicy.isSystemScopePackage("com.android.settings", system))
        assertFalse(ProviderCallerPolicy.isSystemScopePackage("com.android.phone", 0))
    }
}
