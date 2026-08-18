package io.github.magisk317.relay.domain.system

import android.content.Context
import io.mockk.mockk
import io.github.magisk317.relay.android.data.db.entity.AppInfo
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RuntimeAppConfigFacadeTest {

    @Test
    fun isPackageBlocked_returnsDatabaseFlagWhenConfigExists() = runBlocking {
        val context = mockk<Context>(relaxed = true)

        val facade = RuntimeAppConfigFacade(
            context = context,
            appInfoLookup = {
                AppInfo(
                    packageName = "com.bank.app",
                    blocked = true,
                )
            },
        )

        assertTrue(facade.isPackageBlocked("com.bank.app"))
    }

    @Test
    fun isPackageBlocked_returnsFalseWhenPackageMissing() = runBlocking {
        val context = mockk<Context>(relaxed = true)

        val facade = RuntimeAppConfigFacade(
            context = context,
            appInfoLookup = { null },
        )

        assertFalse(facade.isPackageBlocked("com.unknown.app"))
    }

    @Test
    fun isPackageBlocked_propagatesProviderFailureForFailClosedCaller() {
        val context = mockk<Context>(relaxed = true)

        val facade = RuntimeAppConfigFacade(
            context = context,
            appInfoLookup = { throw IllegalStateException("db unavailable") },
        )

        assertThrows<IllegalStateException> {
            runBlocking { facade.isPackageBlocked("com.bank.app") }
        }
    }
}
