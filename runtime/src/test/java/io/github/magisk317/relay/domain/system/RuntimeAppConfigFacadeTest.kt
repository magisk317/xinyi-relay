package io.github.magisk317.relay.domain.system

import android.content.Context
import io.github.magisk317.relay.data.db.entity.AppInfo
import io.github.magisk317.relay.data.repository.ConfigRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeAppConfigFacadeTest {

    @Test
    fun isPackageBlocked_returnsDatabaseFlagWhenConfigExists() = runBlocking {
        val context = mockk<Context>(relaxed = true)
        val configRepository = mockk<ConfigRepository>()
        coEvery { configRepository.getAppInfoByPackage("com.bank.app") } returns AppInfo(
            packageName = "com.bank.app",
            blocked = true,
        )

        val facade = RuntimeAppConfigFacade(
            context = context,
            configRepository = configRepository,
            appConfigFallbackLoader = { emptyList() },
        )

        assertTrue(facade.isPackageBlocked("com.bank.app"))
    }

    @Test
    fun isPackageBlocked_returnsFalseWhenPackageMissing() = runBlocking {
        val context = mockk<Context>(relaxed = true)
        val configRepository = mockk<ConfigRepository>()
        coEvery { configRepository.getAppInfoByPackage("com.unknown.app") } returns null

        val facade = RuntimeAppConfigFacade(
            context = context,
            configRepository = configRepository,
            appConfigFallbackLoader = {
                listOf(AppInfo(packageName = "com.unknown.app", blocked = true))
            },
        )

        assertFalse(facade.isPackageBlocked("com.unknown.app"))
    }

    @Test
    fun isPackageBlocked_fallsBackToFileWhenRepositoryFails() = runBlocking {
        val context = mockk<Context>(relaxed = true)
        val configRepository = mockk<ConfigRepository>()
        coEvery { configRepository.getAppInfoByPackage("com.bank.app") } throws IllegalStateException("db unavailable")

        val facade = RuntimeAppConfigFacade(
            context = context,
            configRepository = configRepository,
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
