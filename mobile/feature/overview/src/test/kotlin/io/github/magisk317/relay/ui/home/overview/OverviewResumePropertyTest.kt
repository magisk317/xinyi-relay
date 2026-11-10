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
import io.github.magisk317.relay.mobilefeature.overview.BuildConfig
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
import org.junit.jupiter.api.Assertions.assertNull
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
            val inactiveCard = if (BuildConfig.ENABLE_NONROOT_MODE) {
                StatusCardState.Shizuku("13")
            } else {
                StatusCardState.Inactive
            }
            withTimeout(5_000) {
                viewModel.uiState.first {
                    it.runtimeSnapshot.appVersionName == "1.0" && it.runtimeSnapshot.statusCard == inactiveCard
                }
            }
            assertNonRootRows(
                viewModel.uiState.value.runtimeSnapshot, ListenerRowState.DISCONNECTED, ShizukuRowState.AUTHORIZED,
            )
            listener.value = true
            if (BuildConfig.ENABLE_NONROOT_MODE) {
                assertNotNull(withTimeoutOrNull(2_000) {
                    viewModel.uiState.first { it.runtimeSnapshot.rows.listener == ListenerRowState.CONNECTED }
                }, "A foreground listener connection must update the rows without another resume")
            }
            xposed.value = true
            withTimeout(5_000) {
                viewModel.uiState.first { it.runtimeSnapshot.statusCard is StatusCardState.Framework }
            }
            assertEquals(XposedRowState.ACTIVE, viewModel.uiState.value.runtimeSnapshot.rows.xposed)
            assertEquals(StatusCardState.Framework(FrameworkIdentity("LSPosed", "1.0")), viewModel.uiState.value.runtimeSnapshot.statusCard)
            // The framework emission also proves the observer processed the changed signals
            // on Play, where listener changes correctly produce no visible non-root row.
            assertNonRootRows(
                viewModel.uiState.value.runtimeSnapshot, ListenerRowState.CONNECTED, ShizukuRowState.AUTHORIZED,
            )
            listener.value = false
            xposed.value = false
            withTimeout(5_000) {
                viewModel.uiState.first {
                    it.runtimeSnapshot.rows.listener ==
                        (if (BuildConfig.ENABLE_NONROOT_MODE) ListenerRowState.DISCONNECTED else null) &&
                        it.runtimeSnapshot.statusCard == inactiveCard
                }
            }
            assertNonRootRows(
                viewModel.uiState.value.runtimeSnapshot, ListenerRowState.DISCONNECTED, ShizukuRowState.AUTHORIZED,
            )
            assertEquals(1, probes)
            verify(exactly = 1) { relayPackageActions.hasRootAccess() }
            val shizukuProbes = if (BuildConfig.ENABLE_NONROOT_MODE) 1 else 0
            verify(exactly = shizukuProbes) { ShizukuGrantBridge.isSupported() }
            verify(exactly = shizukuProbes) { ShizukuGrantBridge.isAuthorized() }
            verify(exactly = shizukuProbes) { ShizukuGrantBridge.shizukuVersion(context) }
        } finally {
            observation.cancelAndJoin()
        }
        val stoppedSnapshot = viewModel.uiState.value.runtimeSnapshot
        listener.value = true
        xposed.value = true
        yield()
        assertEquals(stoppedSnapshot, viewModel.uiState.value.runtimeSnapshot)
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
            assertNonRootRows(
                snapshot,
                if (index % 3 == 2) ListenerRowState.DISCONNECTED else ListenerRowState.CONNECTED,
                ShizukuRowState.UNAUTHORIZED,
            )
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
        assertNonRootRows(
            viewModel.uiState.value.runtimeSnapshot, ListenerRowState.CONNECTED, ShizukuRowState.UNREACHABLE,
        )
        val shizukuSupportProbes = if (BuildConfig.ENABLE_NONROOT_MODE) 130 else 0
        val shizukuAuthorizationProbes = if (BuildConfig.ENABLE_NONROOT_MODE) 129 else 0
        verify(exactly = shizukuSupportProbes) { ShizukuGrantBridge.isSupported() }
        verify(exactly = shizukuAuthorizationProbes) { ShizukuGrantBridge.isAuthorized() }
        verify(exactly = 0) { ShizukuGrantBridge.shizukuVersion(context) }
        viewModel.setPageActive(false)
        // A hidden page must not read signals or publish a fresh snapshot.
        val stoppedSnapshot = viewModel.uiState.value.runtimeSnapshot
        viewModel.refreshRuntimeSnapshot(context)
        assertEquals(130, refreshes)
        assertEquals(stoppedSnapshot, viewModel.uiState.value.runtimeSnapshot)
    }

    private fun assertNonRootRows(
        snapshot: OverviewRuntimeUiState,
        listener: ListenerRowState,
        shizuku: ShizukuRowState,
    ) {
        if (BuildConfig.ENABLE_NONROOT_MODE) {
            assertEquals(listener, snapshot.rows.listener)
            assertEquals(shizuku, snapshot.rows.shizuku)
        } else {
            assertNull(snapshot.rows.listener, "Play must hide the notification-listener row")
            assertNull(snapshot.rows.shizuku, "Play must hide the Shizuku row")
        }
    }
}
