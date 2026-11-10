package io.github.magisk317.relay.ui.home.overview

import android.content.Context
import io.github.magisk317.relay.common.utils.relayPackageActions
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.feature.mode.FrameworkIdentity
import io.github.magisk317.relay.feature.mode.ListenerRowState
import io.github.magisk317.relay.feature.mode.NonRootRuntimeState
import io.github.magisk317.relay.feature.mode.RuntimeSignalRefresher
import io.github.magisk317.relay.feature.mode.ShizukuRowState
import io.github.magisk317.relay.feature.mode.StatusCardState
import io.github.magisk317.relay.feature.mode.XposedRowState
import io.github.magisk317.relay.feature.mode.XposedRuntimeState
import io.github.magisk317.relay.nonroot.ShizukuGrantBridge
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.cancelAndJoin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OverviewResumePropertyTest {
    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `foreground runtime changes update rows and status without repeating probes or resuming`() = runBlocking {
        val context = mockk<Context>(relaxed = true)
        every { context.packageName } returns "test.app"
        mockkObject(relayPackageActions, XposedRuntimeState, NonRootRuntimeState, ShizukuGrantBridge)
        every { relayPackageActions.hasRootAccess() } returns false
        every { relayPackageActions.getPackageVersion(context, "test.app") } returns ("1.0" to 1L)
        val xposed = MutableStateFlow(false)
        val listener = MutableStateFlow(false)
        every { XposedRuntimeState.xposedActive } returns xposed
        every { NonRootRuntimeState.listenerConnected } returns listener
        every { ShizukuGrantBridge.isSupported() } returns true
        every { ShizukuGrantBridge.isAuthorized() } returns true
        every { ShizukuGrantBridge.shizukuVersion(context) } returns "13"
        var probes = 0
        val signals = object : RuntimeSignalRefresher {
            override suspend fun refresh() { probes++ }
            override fun frameworkIdentity() = FrameworkIdentity("LSPosed", "1.0")
        }
        val viewModel = OverviewViewModel(mockk<SettingsPreferencesRepository>(), signals)
        val observation = launch { viewModel.observeRuntimeSnapshot(context) }
        try {
            withTimeout(5_000) {
                viewModel.uiState.first { it.runtimeSnapshot.statusCard == StatusCardState.Shizuku("13") }
            }
            listener.value = true
            assertNotNull(withTimeoutOrNull(2_000) {
                viewModel.uiState.first { it.runtimeSnapshot.rows.listener == ListenerRowState.CONNECTED }
            }, "A foreground listener connection must update the rows without another resume")
            xposed.value = true
            withTimeout(5_000) {
                viewModel.uiState.first { it.runtimeSnapshot.statusCard is StatusCardState.Framework }
            }
            assertEquals(XposedRowState.ACTIVE, viewModel.uiState.value.runtimeSnapshot.rows.xposed)
            assertEquals(StatusCardState.Framework(FrameworkIdentity("LSPosed", "1.0")), viewModel.uiState.value.runtimeSnapshot.statusCard)
            listener.value = false
            xposed.value = false
            withTimeout(5_000) {
                viewModel.uiState.first {
                    it.runtimeSnapshot.rows.listener == ListenerRowState.DISCONNECTED &&
                        it.runtimeSnapshot.statusCard == StatusCardState.Shizuku("13")
                }
            }
            assertEquals(1, probes)
            verify(exactly = 1) { relayPackageActions.hasRootAccess() }
            verify(exactly = 1) { ShizukuGrantBridge.isSupported() }
        } finally {
            observation.cancelAndJoin()
        }
        listener.value = true
        yield()
        assertEquals(ListenerRowState.DISCONNECTED, viewModel.uiState.value.runtimeSnapshot.rows.listener)
    }

    @Test
    fun `every resume rereads current signals including listener disconnect and unauthorized Shizuku`() = runBlocking {
        val context = mockk<Context>(relaxed = true)
        every { context.packageName } returns "test.app"
        mockkObject(relayPackageActions, XposedRuntimeState, NonRootRuntimeState, ShizukuGrantBridge)
        every { relayPackageActions.hasRootAccess() } returns false
        every { relayPackageActions.getPackageVersion(context, "test.app") } returns ("1.0" to 1L)
        val xposed = MutableStateFlow(false)
        val listener = MutableStateFlow(false)
        every { XposedRuntimeState.xposedActive } returns xposed
        every { NonRootRuntimeState.listenerConnected } returns listener
        every { ShizukuGrantBridge.isSupported() } returns true
        every { ShizukuGrantBridge.isAuthorized() } returns false
        every { ShizukuGrantBridge.shizukuVersion(context) } returns "13"
        var refreshes = 0
        val signals = object : RuntimeSignalRefresher {
            override suspend fun refresh() {
                when (refreshes++ % 3) {
                    0 -> { xposed.value = true; listener.value = true }
                    1 -> { xposed.value = false; listener.value = true }
                    2 -> { xposed.value = false; listener.value = false }
                }
            }
            override fun frameworkIdentity() = FrameworkIdentity("LSPosed", "1.0")
        }
        val viewModel = OverviewViewModel(mockk<SettingsPreferencesRepository>(), signals)
        repeat(129) { index ->
            viewModel.setPageActive(true)
            viewModel.refreshRuntimeSnapshot(context)
            val snapshot = viewModel.uiState.value.runtimeSnapshot
            assertEquals(index + 1, refreshes)
            assertEquals(if (index % 3 == 0) XposedRowState.ACTIVE else XposedRowState.INACTIVE, snapshot.rows.xposed)
            assertEquals(if (index % 3 == 2) ListenerRowState.DISCONNECTED else ListenerRowState.CONNECTED, snapshot.rows.listener)
            assertEquals(ShizukuRowState.UNAUTHORIZED, snapshot.rows.shizuku)
            assertEquals(
                if (index % 3 == 0) StatusCardState.Framework(FrameworkIdentity("LSPosed", "1.0")) else StatusCardState.Inactive,
                snapshot.statusCard,
            )
            viewModel.setPageActive(false)
        }
        // The bridge maps binder failures to false; the view model must show unreachable,
        // even if the previous resume had an online binder and cached framework identity.
        every { ShizukuGrantBridge.isSupported() } returns false
        viewModel.setPageActive(true)
        viewModel.refreshRuntimeSnapshot(context)
        assertEquals(ShizukuRowState.UNREACHABLE, viewModel.uiState.value.runtimeSnapshot.rows.shizuku)
        viewModel.setPageActive(false)
        // A hidden page must not read signals or publish a fresh snapshot.
        viewModel.refreshRuntimeSnapshot(context)
        assertEquals(130, refreshes)
    }
}
