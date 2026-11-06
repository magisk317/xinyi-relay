package io.github.magisk317.relay.app

import io.github.magisk317.smscode.runtime.contract.prefs.PrefRead
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LegacyShortCodeBypassMigrationTest {
    @Test
    fun `missing private key preserves enabled legacy intent and restores global`() {
        assertEquals(
            LegacyShortCodeBypassAction.ENABLE_PRIVATE_AND_RESTORE_GLOBAL,
            decideLegacyShortCodeBypassAction(PrefRead.Miss, "0"),
        )
    }

    @Test
    fun `existing enabled private key only restores global`() {
        assertEquals(
            LegacyShortCodeBypassAction.RESTORE_GLOBAL,
            decideLegacyShortCodeBypassAction(PrefRead.Hit(true, "test"), "0"),
        )
    }

    @Test
    fun `existing disabled private key stays disabled while restoring global`() {
        assertEquals(
            LegacyShortCodeBypassAction.RESTORE_GLOBAL,
            decideLegacyShortCodeBypassAction(PrefRead.Hit(false, "test"), "0"),
        )
    }

    @Test
    fun `unavailable private preferences leave the legacy global untouched`() {
        assertEquals(
            LegacyShortCodeBypassAction.NONE,
            decideLegacyShortCodeBypassAction(PrefRead.Unavailable, "0"),
        )
    }

    @Test
    fun `native global value needs no migration`() {
        assertEquals(
            LegacyShortCodeBypassAction.NONE,
            decideLegacyShortCodeBypassAction(PrefRead.Miss, "1"),
        )
    }
}
