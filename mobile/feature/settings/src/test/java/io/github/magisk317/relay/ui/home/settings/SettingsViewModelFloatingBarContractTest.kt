package io.github.magisk317.relay.ui.home.settings

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fix 5 static contract: SettingsViewModel carries the three floating-bar ThemeState fields,
 * bootstraps them from AppearancePreferences, and exposes setters.
 */
class SettingsViewModelFloatingBarContractTest {

    private fun root(): File {
        val override = System.getProperty("xinyi.settingsSourceRoot")
            ?: System.getenv("XINYI_SETTINGS_SOURCE_ROOT")
        return if (override.isNullOrBlank()) {
            File("src/main/java/io/github/magisk317/relay/ui/home/settings").absoluteFile
        } else {
            File(override).absoluteFile
        }
    }

    @Test
    fun viewModel_carriesFloatingBarState() {
        val source = File(root(), "SettingsViewModel.kt").readText()
        assertTrue(source.contains("val floatingBottomBar"), "ThemeState field missing")
        assertTrue(source.contains("val bottomBarBlur"), "blur field missing")
        assertTrue(source.contains("val bottomBarBackdrop"), "backdrop field missing")
        assertTrue(source.contains("AppearancePreferences.floatingBottomBarEnabled(app)"), "init read missing")
        assertTrue(source.contains("AppearancePreferences.bottomBarBlurEnabled(app)"), "blur init read missing")
        assertTrue(source.contains("AppearancePreferences.bottomBarBackdropEnabled(app)"), "backdrop init read missing")
        assertTrue(
            source.contains("fun setFloatingBottomBarEnabled") &&
                source.contains("fun setBottomBarBlurEnabled") &&
                source.contains("fun setBottomBarBackdropEnabled"),
            "setters missing",
        )
    }
}
