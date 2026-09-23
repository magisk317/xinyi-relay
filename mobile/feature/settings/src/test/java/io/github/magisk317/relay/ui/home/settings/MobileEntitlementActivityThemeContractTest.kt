package io.github.magisk317.relay.ui.home.settings

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fix 6 static contract: MobileEntitlementActivity drives AppTheme from ThemeState instead
 * of the hard-coded dark mode.
 */
class MobileEntitlementActivityThemeContractTest {

    private fun file(): File {
        val override = System.getProperty("xinyi.entitlementSourceRoot")
            ?: System.getenv("XINYI_ENTITLEMENT_SOURCE_ROOT")
        val name = "MobileEntitlementActivity.kt"
        return if (override.isNullOrBlank()) {
            File("../../../app/src/main/java/io/github/magisk317/relay/entitlement/$name").absoluteFile
        } else {
            File(override, name).absoluteFile
        }
    }

    @Test
    fun entitlementActivity_usesThemeState() {
        val source = file().readText()
        assertTrue(source.contains("= koinViewModel()"), "viewmodel wiring missing")
        assertTrue(source.contains("themeState.uiKitStyle"), "theme param missing")
        assertTrue(source.contains("themeState.accentColor"), "accent param missing")
        assertTrue(
            source.contains("themeMode = themeState.mode"),
            "AppTheme must read themeMode from ThemeState",
        )
    }
}
