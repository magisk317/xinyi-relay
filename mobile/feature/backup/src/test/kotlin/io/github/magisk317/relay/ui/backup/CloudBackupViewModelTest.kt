package io.github.magisk317.relay.ui.backup

import android.app.Application
import io.github.magisk317.relay.auth.AuthManager
import io.github.magisk317.relay.auth.GoogleSignInHelper
import io.github.magisk317.relay.backup.BackupSource
import io.github.magisk317.relay.backup.CloudBackupProvider
import io.github.magisk317.relay.backup.CloudBackupSettings
import io.github.magisk317.relay.backup.CloudBackupSettingsStore
import io.github.magisk317.relay.backup.WebDavCloudBackupProvider
import io.github.magisk317.relay.backup.drive.GoogleDriveBackupConfig
import io.github.magisk317.relay.backup.drive.GoogleDriveBackupConfigStore
import io.github.magisk317.relay.backup.webdav.WebDavConfig
import io.github.magisk317.relay.backup.webdav.WebDavConfigStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

@OptIn(ExperimentalCoroutinesApi::class)
class CloudBackupViewModelTest {
    private val application = mockk<Application>(relaxed = true)
    private val webDav = mockk<WebDavCloudBackupProvider>(relaxed = true)
    private val drive = mockk<CloudBackupProvider>(relaxed = true)
    private val config = WebDavConfig("https://backup.example.test", "user", "password")

    @BeforeEach
    fun setUp() {
        mockkObject(CloudBackupSettingsStore, WebDavConfigStore, GoogleDriveBackupConfigStore)
        every { CloudBackupSettingsStore.getSettings(any()) } returns CloudBackupSettings(autoBackupSource = BackupSource.WEBDAV)
        every { WebDavConfigStore.getConfig(any()) } returns config
        every { GoogleDriveBackupConfigStore.getConfig(any()) } returns GoogleDriveBackupConfig()
        every { application.getString(any()) } answers { "resource-${firstArg<Int>()}" }
        startKoin {
            modules(module {
                single<CloudBackupProvider> { drive }
                single { webDav }
                single { mockk<AuthManager>(relaxed = true) }
                single { mockk<GoogleSignInHelper>(relaxed = true) }
            })
        }
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
        unmockkObject(CloudBackupSettingsStore, WebDavConfigStore, GoogleDriveBackupConfigStore)
        Dispatchers.resetMain()
    }

    @Test
    fun `restore success reports success only after the provider finishes`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = CloudBackupViewModel(application)
        val events = mutableListOf<CloudBackupViewModel.CloudBackupEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        coEvery { webDav.restoreFromBackup("backup-1") } coAnswers {
            assertTrue(vm.isLoading.value)
            assertTrue(events.isEmpty())
            Result.success(Unit)
        }
        vm.restoreBackup("backup-1")
        advanceUntilIdle()
        assertEquals(listOf(CloudBackupViewModel.CloudBackupEvent.RestoreSuccess), events)
        assertFalse(vm.isLoading.value)
        coVerify(exactly = 1) { webDav.restoreFromBackup("backup-1") }
    }

    @Test
    fun `restore failure clears loading and permits a successful retry`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = CloudBackupViewModel(application)
        val events = mutableListOf<CloudBackupViewModel.CloudBackupEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        coEvery { webDav.restoreFromBackup("backup-1") } returns Result.failure(IllegalStateException("Restore failed"))
        vm.restoreBackup("backup-1")
        advanceUntilIdle()
        assertFalse(vm.isLoading.value)
        assertEquals(1, events.size)
        assertTrue(events.single() is CloudBackupViewModel.CloudBackupEvent.Error)
        coEvery { webDav.restoreFromBackup("backup-1") } returns Result.success(Unit)
        vm.restoreBackup("backup-1")
        advanceUntilIdle()
        assertEquals(CloudBackupViewModel.CloudBackupEvent.RestoreSuccess, events.last())
        assertFalse(vm.isLoading.value)
        coVerify(exactly = 2) { webDav.restoreFromBackup("backup-1") }
    }

    @Test
    fun `missing configuration prevents restore and reports upload unavailability`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        every { WebDavConfigStore.getConfig(any()) } returns null
        val vm = CloudBackupViewModel(application)
        val events = mutableListOf<CloudBackupViewModel.CloudBackupEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        vm.restoreBackup("backup-1")
        vm.backupNow()
        vm.loadBackups()
        advanceUntilIdle()
        coVerify(exactly = 0) { webDav.restoreFromBackup(any()) }
        coVerify(exactly = 0) { webDav.uploadBackup() }
        coVerify(exactly = 0) { webDav.listBackups() }
        assertTrue(events.single() is CloudBackupViewModel.CloudBackupEvent.Error)
        assertTrue(vm.backupListMessage.value != null)
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun `thrown provider exception reports an error and clears loading`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = CloudBackupViewModel(application)
        val events = mutableListOf<CloudBackupViewModel.CloudBackupEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        coEvery { webDav.restoreFromBackup(any()) } throws IllegalStateException("network offline")
        vm.restoreBackup("backup-1")
        advanceUntilIdle()
        assertFalse(vm.isLoading.value)
        assertTrue(events.single() is CloudBackupViewModel.CloudBackupEvent.Error)
    }

    @Test
    fun `thrown cancellation propagates without user error and clears loading`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = CloudBackupViewModel(application)
        val events = mutableListOf<CloudBackupViewModel.CloudBackupEvent>()
        var operation: Job? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        coEvery { webDav.restoreFromBackup(any()) } coAnswers {
            operation = currentCoroutineContext()[Job]
            throw CancellationException("screen left")
        }
        vm.restoreBackup("backup-1")
        advanceUntilIdle()
        assertTrue(operation?.isCancelled == true)
        assertFalse(vm.isLoading.value)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `cancellation returned as failure propagates without a user error`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = CloudBackupViewModel(application)
        val events = mutableListOf<CloudBackupViewModel.CloudBackupEvent>()
        var operation: Job? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        coEvery { webDav.restoreFromBackup(any()) } coAnswers {
            operation = currentCoroutineContext()[Job]
            Result.failure(CancellationException("download canceled"))
        }
        vm.restoreBackup("backup-1")
        advanceUntilIdle()
        assertTrue(operation?.isCancelled == true)
        assertFalse(vm.isLoading.value)
        assertTrue(events.isEmpty())
    }
}
