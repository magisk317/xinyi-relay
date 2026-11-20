package io.github.magisk317.relay.desktop.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DesktopNavigationTest {

    @Test
    fun `navigation groups keep every route reachable`() {
        val groupedRoutes = DESKTOP_NAV_GROUPS.flatMap { it.routes }
        assertEquals(DesktopRoute.entries.toSet(), groupedRoutes.toSet())
        assertEquals(groupedRoutes.size, groupedRoutes.toSet().size)
    }
}
