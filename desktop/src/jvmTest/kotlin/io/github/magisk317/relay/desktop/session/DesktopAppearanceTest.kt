package io.github.magisk317.relay.desktop.session

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesktopAppearanceTest {

    @Test
    fun `light and dark overrides ignore system appearance`() {
        assertFalse(DesktopAppearance.LIGHT.isDark(systemIsDark = true))
        assertTrue(DesktopAppearance.DARK.isDark(systemIsDark = false))
    }

    @Test
    fun `system appearance follows the operating system`() {
        assertTrue(DesktopAppearance.SYSTEM.isDark(systemIsDark = true))
        assertFalse(DesktopAppearance.SYSTEM.isDark(systemIsDark = false))
    }
}
