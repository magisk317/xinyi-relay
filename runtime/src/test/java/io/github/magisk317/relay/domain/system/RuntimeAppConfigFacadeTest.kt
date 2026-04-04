package io.github.magisk317.relay.domain.system

import android.content.Context
import dev.mokkery.MockMode.autofill
import dev.mokkery.mock
import io.github.magisk317.relay.data.db.entity.AppInfo
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeAppConfigFacadeTest {

    @Test
    fun isPackageBlocked_returnsDatabaseFlagWhenConfigExists() = runBlocking {
        val context = mock<Context>(autofill)

        val facade = RuntimeAppConfigFacade(
            context = context,
            appInfoLookup = {
                AppInfo(
                    packageName = "com.bank.app",
                    blocked = true,
                )
            },
            appConfigFallbackLoader = { emptyList() },
        )

        assertTrue(facade.isPackageBlocked("com.bank.app"))
    }

    @Test
    fun isPackageBlocked_returnsFalseWhenPackageMissing() = runBlocking {
        val context = mock<Context>(autofill)

        val facade = RuntimeAppConfigFacade(
            context = context,
            appInfoLookup = { null },
            appConfigFallbackLoader = {
                listOf(AppInfo(packageName = "com.unknown.app", blocked = true))
            },
        )

        assertFalse(facade.isPackageBlocked("com.unknown.app"))
    }

    @Test
    fun isPackageBlocked_fallsBackToFileWhenRepositoryFails() = runBlocking {
        val context = mock<Context>(autofill)

        val facade = RuntimeAppConfigFacade(
            context = context,
            appInfoLookup = { throw IllegalStateException("db unavailable") },
            appConfigFallbackLoader = {
                listOf(
                    AppInfo(packageName = "com.bank.app", blocked = true),
                    AppInfo(packageName = "com.other.app", blocked = false),
                )
            },
        )

        assertTrue(facade.isPackageBlocked("com.bank.app"))
    }
}
