package io.github.magisk317.relay.android.diagnostics

import io.github.magisk317.relay.contract.constant.RelayPrefConst
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeDiagnosticsEnvironment
import io.github.magisk317.xposed.logging.MagiskLogPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Guards the values the contract module has to restate because its commonMain source set cannot
 * see the kit, and the artifact names the installed configuration derives from one token.
 */
class RuntimeLogRetentionDefaultsTest {
    @Test
    fun `the contract restates the kit retention default`() {
        assertEquals(MagiskLogPolicy.DEFAULT_LOG_RETENTION_DAYS, RelayPrefConst.RUNTIME_LOG_RETENTION_DAYS_DEFAULT)
        assertEquals(MagiskLogPolicy.MIN_LOG_RETENTION_DAYS, RelayPrefConst.RUNTIME_LOG_RETENTION_DAYS_MIN)
    }

    @Test
    fun `one product token names every artifact`() {
        RuntimeDiagnosticsBridge.ensureInstalled()
        val config = RuntimeDiagnosticsEnvironment.current()

        assertEquals("relay", config.product)
        assertEquals("relay_logs_", config.exportFilePrefix)
        assertEquals(".tmp_relay_logs_", config.stagingDirPrefix)
        assertEquals("relay_logs", config.privateExportDirName)
    }
}
