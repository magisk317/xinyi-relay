package io.github.magisk317.relay.ui.home.settings

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fix 2/3 static contract: sort dropdown + three sheets lost their titles, and the theme
 * page exposes the three floating-bar switches. Covers 4 files + MANIFEST, mirroring
 * StringFormatTest in io/github/magisk317/smscode/core.
 */
class ThemeSettingsPageTextContractTest {

    private fun settingsRoot(): File {
        val override = System.getProperty("xinyi.settingsSourceRoot")
            ?: System.getenv("XINYI_SETTINGS_SOURCE_ROOT")
        return if (override.isNullOrBlank()) {
            File("src/main/java/io/github/magisk317/relay/ui/home/settings").absoluteFile
        } else {
            File(override).absoluteFile
        }
    }

    private fun appConfigRoot(): File {
        val override = System.getProperty("xinyi.appConfigSourceRoot")
            ?: System.getenv("XINYI_APP_CONFIG_SOURCE_ROOT")
        return if (override.isNullOrBlank()) {
            File("../appconfig/src/main/java/io/github/magisk317/relay/ui/home/appconfig").absoluteFile
        } else {
            File(override).absoluteFile
        }
    }

    private fun recordRoot(): File {
        val override = System.getProperty("xinyi.recordSourceRoot")
            ?: System.getenv("XINYI_RECORD_SOURCE_ROOT")
        return if (override.isNullOrBlank()) {
            File("../record/src/main/java/io/github/magisk317/relay/ui/record").absoluteFile
        } else {
            File(override).absoluteFile
        }
    }

    private fun moduleRoot(): File {
        val override = System.getProperty("xinyi.moduleRoot")
            ?: System.getenv("XINYI_MODULE_ROOT")
        return if (override.isNullOrBlank()) {
            File("../../../modules/core/src").absoluteFile
        } else {
            File(override).absoluteFile
        }
    }

    private fun read(root: File, name: String): String = File(root, name).readText()

    @Test
    fun themeSettingsPage_declaresFloatingBarSwitches() {
        val source = read(settingsRoot(), "ThemeSettingsPage.kt")
        assertTrue(source.contains("pref_floating_bottom_bar_title"), "floating bar switch missing")
        assertTrue(source.contains("pref_bottom_bar_blur_title"), "blur switch missing")
        assertTrue(source.contains("pref_bottom_bar_backdrop_title"), "backdrop switch missing")
        assertTrue(source.contains("checked = themeState.floatingBottomBar"), "floating bar state missing")
        assertTrue(source.contains("checked = themeState.bottomBarBlur"), "blur state missing")
        assertTrue(source.contains("checked = themeState.bottomBarBackdrop"), "backdrop state missing")
        assertTrue(source.contains("setFloatingBottomBarEnabled"), "setter missing")
        assertTrue(source.contains("setBottomBarBlurEnabled"), "blur setter missing")
        assertTrue(source.contains("setBottomBarBackdropEnabled"), "backdrop setter missing")
    }

    @Test
    fun appConfigScreen_usesDropdownMenuForSorting() {
        val source = read(appConfigRoot(), "AppConfigScreen.kt")
        assertTrue(source.contains("AppDropdownMenu"), "sort dropdown missing")
        assertTrue(
            !source.contains("app_config_sort_label") && !source.contains("app_config_sort_usage"),
            "legacy sort radio items must be removed",
        )
    }

    @Test
    fun recordScreens_droppedSheetTitles() {
        val record = read(recordRoot(), "CodeRecordScreen.kt")
        val blacklist = read(recordRoot(), "BlacklistHitListScreen.kt")
        assertTrue(
            !record.contains("SectionHeader"),
            "CodeRecordScreen must not keep the SectionHeader sheet title",
        )
        assertTrue(
            !blacklist.contains("SectionHeader"),
            "BlacklistHitListScreen must not keep the SectionHeader sheet title",
        )
    }

    @Test
    fun strings_declareAllNineNewKeys() {
        // Core module strings live one level above the settings module source root.
        val valuesDir = File(moduleRoot(), "main/res/values")
        val zhDir = File(moduleRoot(), "main/res/values-zh-rCN")
        val twDir = File(moduleRoot(), "main/res/values-zh-rTW")
        val keys = listOf(
            "app_config_sort_mode",
            "pref_theme_group_navigation",
            "pref_floating_bottom_bar_title",
            "pref_floating_bottom_bar_summary",
            "pref_floating_bottom_bar_summary_miuix",
            "pref_bottom_bar_blur_title",
            "pref_bottom_bar_blur_summary",
            "pref_bottom_bar_backdrop_title",
            "pref_bottom_bar_backdrop_summary",
        )
        for (key in keys) {
            assertTrue(read(valuesDir, "strings.xml").contains("name=\"$key\""), "default missing $key")
            assertTrue(read(zhDir, "strings.xml").contains("name=\"$key\""), "zh-rCN missing $key")
            assertTrue(read(twDir, "strings.xml").contains("name=\"$key\""), "zh-rTW missing $key")
        }
    }
}
