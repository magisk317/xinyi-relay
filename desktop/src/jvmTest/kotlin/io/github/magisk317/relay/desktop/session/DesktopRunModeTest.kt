package io.github.magisk317.relay.desktop.session

import io.github.magisk317.relay.desktop.i18n.DesktopLocale
import io.github.magisk317.relay.desktop.i18n.DesktopMessages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesktopRunModeTest {

    @Test
    fun `default is remote`() {
        assertEquals(DesktopRunMode.Remote, DesktopRunMode.Default)
    }

    @Test
    fun `only remote leaves the local mirror closed`() {
        assertFalse(DesktopRunMode.Remote.usesLocalMirror)
        assertTrue(DesktopRunMode.Local.usesLocalMirror)
        assertTrue(DesktopRunMode.Hybrid.usesLocalMirror)
    }

    @Test
    fun `every mode maps to distinct label and description keys`() {
        val labels = DesktopRunMode.entries.map { it.labelKey }
        val descriptions = DesktopRunMode.entries.map { it.descriptionKey }
        assertEquals(3, labels.size)
        assertEquals(3, labels.toSet().size)
        assertEquals(3, descriptions.toSet().size)
        assertEquals("settings.runMode.remote", DesktopRunMode.Remote.labelKey)
        assertEquals("settings.runMode.local", DesktopRunMode.Local.labelKey)
        assertEquals("settings.runMode.hybrid", DesktopRunMode.Hybrid.labelKey)
        assertEquals("settings.runMode.remoteDesc", DesktopRunMode.Remote.descriptionKey)
        assertEquals("settings.runMode.localDesc", DesktopRunMode.Local.descriptionKey)
        assertEquals("settings.runMode.hybridDesc", DesktopRunMode.Hybrid.descriptionKey)
    }

    @Test
    fun `mapped keys resolve in every locale`() {
        for (mode in DesktopRunMode.entries) {
            for (key in listOf(mode.labelKey, mode.descriptionKey)) {
                for (locale in DesktopLocale.entries) {
                    assertTrue(
                        DesktopMessages.t(locale, key) != key,
                        "$key must resolve for $locale",
                    )
                }
            }
        }
    }
}
