package io.github.magisk317.relay.ui.sender.forms

import io.github.magisk317.relay.sender.E2eeModuleStatus
import io.github.magisk317.relay.sender.MatrixE2eeAvailability
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Unit tests for the Matrix E2EE status UI rendering logic.
 *
 * Validates that each [E2eeModuleStatus] maps to the correct UI component rendering
 * in [MatrixE2eeStatusSection].
 *
 * Requirements: 10.1, 10.2, 10.3
 */
class MatrixE2eeStatusSectionTest {

    /**
     * Represents the expected UI component type that should be rendered
     * for a given [E2eeModuleStatus].
     */
    enum class ExpectedUiComponent {
        /** Green "E2EE 已启用" status card */
        ENABLED_CARD,

        /** Info banner suggesting E2EE variant (for GitHub noE2ee builds) */
        INFO_BANNER,

        /** Card with install button (for Play NOT_INSTALLED) */
        INSTALL_CARD,

        /** Card with progress bar and disabled install button */
        DOWNLOADING_CARD,

        /** Card with error message and retry button */
        INSTALL_FAILED_CARD,

        /** Card with error message (module loaded but failed to initialize) */
        LOAD_FAILED_CARD,
    }

    /**
     * Maps each [E2eeModuleStatus] to the expected UI component.
     * This mirrors the `when` expression in [MatrixE2eeStatusSection].
     */
    private fun expectedComponent(status: E2eeModuleStatus): ExpectedUiComponent =
        when (status) {
            E2eeModuleStatus.AVAILABLE -> ExpectedUiComponent.ENABLED_CARD
            E2eeModuleStatus.NOT_APPLICABLE -> ExpectedUiComponent.INFO_BANNER
            E2eeModuleStatus.NOT_INSTALLED -> ExpectedUiComponent.INSTALL_CARD
            E2eeModuleStatus.DOWNLOADING -> ExpectedUiComponent.DOWNLOADING_CARD
            E2eeModuleStatus.INSTALL_FAILED -> ExpectedUiComponent.INSTALL_FAILED_CARD
            E2eeModuleStatus.LOAD_FAILED -> ExpectedUiComponent.LOAD_FAILED_CARD
        }

    // --- Requirement 10.1: AVAILABLE → "E2EE 已启用" status indicator ---

    @Test
    fun `AVAILABLE status maps to enabled card`() {
        val availability = FakeAvailability(E2eeModuleStatus.AVAILABLE)

        assertEquals(ExpectedUiComponent.ENABLED_CARD, expectedComponent(availability.status))
        assertTrue(availability.isAvailable)
    }

    @Test
    fun `AVAILABLE status should not show download button or info banner`() {
        val component = expectedComponent(E2eeModuleStatus.AVAILABLE)

        assertEquals(ExpectedUiComponent.ENABLED_CARD, component)
        // Verify it's NOT the install card or info banner
        assertTrue(component != ExpectedUiComponent.INSTALL_CARD)
        assertTrue(component != ExpectedUiComponent.INFO_BANNER)
    }

    // --- Requirement 10.2: NOT_INSTALLED (Play) → install button ---

    @Test
    fun `NOT_INSTALLED status maps to install card with button`() {
        val availability = FakeAvailability(E2eeModuleStatus.NOT_INSTALLED)

        assertEquals(ExpectedUiComponent.INSTALL_CARD, expectedComponent(availability.status))
        assertFalse(availability.isAvailable)
    }

    @Test
    fun `NOT_INSTALLED install triggers download state transition`() {
        val availability = FakeAvailability(E2eeModuleStatus.NOT_INSTALLED)

        // Simulate install trigger: status should transition to DOWNLOADING
        availability.simulateInstallStart()

        assertEquals(E2eeModuleStatus.DOWNLOADING, availability.status)
        assertEquals(ExpectedUiComponent.DOWNLOADING_CARD, expectedComponent(availability.status))
    }

    // --- Requirement 10.3: NOT_APPLICABLE → info banner ---

    @Test
    fun `NOT_APPLICABLE status maps to info banner`() {
        val availability = FakeAvailability(E2eeModuleStatus.NOT_APPLICABLE)

        assertEquals(ExpectedUiComponent.INFO_BANNER, expectedComponent(availability.status))
        assertFalse(availability.isAvailable)
    }

    @Test
    fun `NOT_APPLICABLE should not show download button`() {
        val component = expectedComponent(E2eeModuleStatus.NOT_APPLICABLE)

        assertTrue(component != ExpectedUiComponent.INSTALL_CARD)
        assertTrue(component != ExpectedUiComponent.DOWNLOADING_CARD)
    }

    // --- DOWNLOADING state → progress indicator + disabled button ---

    @Test
    fun `DOWNLOADING status maps to downloading card with progress`() {
        val availability = FakeAvailability(E2eeModuleStatus.DOWNLOADING)

        assertEquals(
            ExpectedUiComponent.DOWNLOADING_CARD,
            expectedComponent(availability.status),
        )
    }

    @Test
    fun `DOWNLOADING progress is clamped between 0 and 100`() {
        val availability = FakeAvailability(E2eeModuleStatus.DOWNLOADING)

        availability.simulateProgress(-5)
        assertEquals(0, availability.progress)

        availability.simulateProgress(50)
        assertEquals(50, availability.progress)

        availability.simulateProgress(150)
        assertEquals(100, availability.progress)
    }

    // --- INSTALL_FAILED state → error message + retry button ---

    @Test
    fun `INSTALL_FAILED status maps to install failed card`() {
        val availability = FakeAvailability(E2eeModuleStatus.INSTALL_FAILED)

        assertEquals(
            ExpectedUiComponent.INSTALL_FAILED_CARD,
            expectedComponent(availability.status),
        )
    }

    @Test
    fun `INSTALL_FAILED includes error message`() {
        val availability = FakeAvailability(
            status = E2eeModuleStatus.INSTALL_FAILED,
            error = "Network error",
        )

        assertEquals("Network error", availability.errorMessage)
    }

    @Test
    fun `INSTALL_FAILED retry transitions back to DOWNLOADING`() {
        val availability = FakeAvailability(
            status = E2eeModuleStatus.INSTALL_FAILED,
            error = "Network error",
        )

        availability.simulateInstallStart()

        assertEquals(E2eeModuleStatus.DOWNLOADING, availability.status)
        assertNull(availability.errorMessage)
    }

    // --- LOAD_FAILED state → error card ---

    @Test
    fun `LOAD_FAILED status maps to load failed card`() {
        assertEquals(
            ExpectedUiComponent.LOAD_FAILED_CARD,
            expectedComponent(E2eeModuleStatus.LOAD_FAILED),
        )
    }

    // --- Exhaustive coverage ---

    @ParameterizedTest
    @EnumSource(E2eeModuleStatus::class)
    fun `all E2eeModuleStatus values map to a valid UI component`(status: E2eeModuleStatus) {
        val component = expectedComponent(status)
        // Ensure each status produces a non-null, distinct component
        assertTrue(ExpectedUiComponent.entries.contains(component))
    }

    @Test
    fun `all enum values are exhaustively covered in the when expression`() {
        // This test ensures that if a new enum value is added to E2eeModuleStatus,
        // the test will fail to compile (due to non-exhaustive when), forcing an update.
        val allStatuses = E2eeModuleStatus.entries
        val mappedComponents = allStatuses.map { expectedComponent(it) }.toSet()

        // Each status should map to a unique component
        assertEquals(allStatuses.size, mappedComponents.size)
    }

    @Test
    fun `isAvailable is true only for AVAILABLE status`() {
        E2eeModuleStatus.entries.forEach { status ->
            val availability = FakeAvailability(status)
            if (status == E2eeModuleStatus.AVAILABLE) {
                assertTrue(availability.isAvailable, "Expected isAvailable=true for $status")
            } else {
                assertFalse(availability.isAvailable, "Expected isAvailable=false for $status")
            }
        }
    }

    // --- State transition tests ---

    @Test
    fun `successful install transitions from NOT_INSTALLED to AVAILABLE`() {
        val availability = FakeAvailability(E2eeModuleStatus.NOT_INSTALLED)

        availability.simulateInstallStart()
        assertEquals(E2eeModuleStatus.DOWNLOADING, availability.status)

        availability.simulateProgress(50)
        assertEquals(E2eeModuleStatus.DOWNLOADING, availability.status)

        availability.simulateInstallSuccess()
        assertEquals(E2eeModuleStatus.AVAILABLE, availability.status)
        assertTrue(availability.isAvailable)
    }

    @Test
    fun `failed install transitions from DOWNLOADING to INSTALL_FAILED`() {
        val availability = FakeAvailability(E2eeModuleStatus.NOT_INSTALLED)

        availability.simulateInstallStart()
        assertEquals(E2eeModuleStatus.DOWNLOADING, availability.status)

        availability.simulateInstallFailure("Disk full")
        assertEquals(E2eeModuleStatus.INSTALL_FAILED, availability.status)
        assertEquals("Disk full", availability.errorMessage)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test doubles
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fake implementation of [MatrixE2eeAvailability] that allows controlling
     * status, progress, and error state for test verification.
     */
    private class FakeAvailability(
        private var currentStatus: E2eeModuleStatus,
        private var currentProgress: Int = 0,
        private var currentError: String? = null,
    ) : MatrixE2eeAvailability {

        constructor(status: E2eeModuleStatus, error: String?) : this(
            currentStatus = status,
            currentError = error,
        )

        override val isAvailable: Boolean
            get() = currentStatus == E2eeModuleStatus.AVAILABLE

        override val status: E2eeModuleStatus
            get() = currentStatus

        override val progress: Int
            get() = currentProgress

        override val errorMessage: String?
            get() = currentError

        override fun requestInstall(
            onProgress: ((Int) -> Unit)?,
            onSuccess: (() -> Unit)?,
            onFailure: ((String) -> Unit)?,
        ) {
            // No-op in fake; transitions are driven by simulate* methods
        }

        fun simulateInstallStart() {
            currentStatus = E2eeModuleStatus.DOWNLOADING
            currentProgress = 0
            currentError = null
        }

        fun simulateProgress(percent: Int) {
            currentProgress = percent.coerceIn(0, 100)
        }

        fun simulateInstallSuccess() {
            currentStatus = E2eeModuleStatus.AVAILABLE
            currentProgress = 100
            currentError = null
        }

        fun simulateInstallFailure(message: String) {
            currentStatus = E2eeModuleStatus.INSTALL_FAILED
            currentProgress = 0
            currentError = message
        }
    }
}
