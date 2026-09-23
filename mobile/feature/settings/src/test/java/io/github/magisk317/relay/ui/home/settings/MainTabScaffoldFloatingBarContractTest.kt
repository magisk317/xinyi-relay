package io.github.magisk317.relay.ui.home.settings

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fix 5 static contract: both MainScreen scaffolds consume themeState.floatingBottomBar /
 * bottomBarBlur / bottomBarBackdrop (2 PagerTabScaffold + 2 MainTabScaffold = at least 2 of each).
 */
class MainTabScaffoldFloatingBarContractTest {

    private fun file(): File {
        val override = System.getProperty("xinyi.uiSourceRoot")
            ?: System.getenv("XINYI_UI_SOURCE_ROOT")
        return if (override.isNullOrBlank()) {
            File("../../ui/src/main/java/io/github/magisk317/relay/ui/home/MainScreen.kt").absoluteFile
        } else {
            File(override, "MainScreen.kt").absoluteFile
        }
    }

    @Test
    fun mainScreen_threadsFloatingBarParams() {
        val source = file().readText()
        val floating = Regex("""floatingBottomBar\s*=\s*themeState\.floatingBottomBar""")
            .findAll(source).count()
        val blur = Regex("""bottomBarBlur\s*=\s*themeState\.bottomBarBlur""")
            .findAll(source).count()
        val backdrop = Regex("""bottomBarBackdrop\s*=\s*themeState\.bottomBarBackdrop""")
            .findAll(source).count()
        assertTrue(floating == 2, "expected 2 floatingBottomBar call sites, got $floating")
        assertTrue(blur == 2, "expected 2 bottomBarBlur call sites, got $blur")
        assertTrue(backdrop == 2, "expected 2 bottomBarBackdrop call sites, got $backdrop")
    }
}
