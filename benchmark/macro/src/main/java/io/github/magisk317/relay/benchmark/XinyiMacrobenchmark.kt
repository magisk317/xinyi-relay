package io.github.magisk317.relay.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class XinyiMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val compilationMode: CompilationMode by lazy {
        when (
            InstrumentationRegistry.getArguments()
                .getString(COMPILATION_MODE_ARGUMENT)
                ?.lowercase()
        ) {
            null, "", "partial" -> CompilationMode.Partial()
            "none" -> CompilationMode.None()
            "full" -> CompilationMode.Full()
            else -> error(
                "Unsupported $COMPILATION_MODE_ARGUMENT; expected none, partial, or full",
            )
        }
    }

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = APP_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = {
            pressHome()
        },
    ) {
        startActivityAndWait()
        device.waitForResource(BENCHMARK_TAB_OVERVIEW)
    }

    @Test
    fun topLevelTabSwitching() = benchmarkRule.measureRepeated(
        packageName = APP_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = ITERATIONS,
        setupBlock = {
            startActivityAndWait()
            device.waitForResource(BENCHMARK_TAB_OVERVIEW)
        },
    ) {
        openTopLevelTab(BENCHMARK_NAV_APPS, BENCHMARK_TAB_APPS)
        openTopLevelTab(BENCHMARK_NAV_RECORDS, BENCHMARK_TAB_RECORDS)
        openTopLevelTab(BENCHMARK_NAV_ADVANCED, BENCHMARK_TAB_ADVANCED)
        openTopLevelTab(BENCHMARK_NAV_SETTINGS, BENCHMARK_TAB_SETTINGS)
        openTopLevelTab(BENCHMARK_NAV_OVERVIEW, BENCHMARK_TAB_OVERVIEW)
    }

    @Test
    fun topLevelSwipeNavigation() = benchmarkRule.measureRepeated(
        packageName = APP_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = ITERATIONS,
        setupBlock = {
            startActivityAndWait()
            device.waitForResource(BENCHMARK_TAB_OVERVIEW)
        },
    ) {
        device.swipeTopLevel(left = true, expectedPageResource = BENCHMARK_TAB_APPS)
        device.swipeTopLevel(left = true, expectedPageResource = BENCHMARK_TAB_RECORDS)
        // The swipe starts in the Records header/non-row area. Record rows retain their own
        // horizontal dismiss gesture while the surrounding page remains pager-owned.
        device.swipeTopLevel(
            left = true,
            expectedPageResource = BENCHMARK_TAB_ADVANCED,
            swipeZoneResource = BENCHMARK_RECORDS_SWIPE_ZONE,
        )
        device.swipeTopLevel(left = true, expectedPageResource = BENCHMARK_TAB_SETTINGS)
        device.swipeTopLevel(left = false, expectedPageResource = BENCHMARK_TAB_ADVANCED)
        device.swipeTopLevel(left = false, expectedPageResource = BENCHMARK_TAB_RECORDS)
        device.swipeTopLevel(
            left = false,
            expectedPageResource = BENCHMARK_TAB_APPS,
            swipeZoneResource = BENCHMARK_RECORDS_SWIPE_ZONE,
        )
        device.swipeTopLevel(left = false, expectedPageResource = BENCHMARK_TAB_OVERVIEW)
    }

    @Test
    fun appsListScroll() = benchmarkRule.measureRepeated(
        packageName = APP_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = ITERATIONS,
        setupBlock = {
            startActivityAndWait()
            openTopLevelTab(BENCHMARK_NAV_APPS, BENCHMARK_TAB_APPS)
            device.waitForResource(BENCHMARK_APPS_LIST)
        },
    ) {
        device.scrollTaggedRegion(BENCHMARK_APPS_LIST)
    }

    @Test
    fun recordsListScroll() = benchmarkRule.measureRepeated(
        packageName = APP_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = ITERATIONS,
        setupBlock = {
            startActivityAndWait()
            openTopLevelTab(BENCHMARK_NAV_RECORDS, BENCHMARK_TAB_RECORDS)
            device.waitForResource(BENCHMARK_RECORDS_LIST)
        },
    ) {
        device.scrollTaggedRegion(BENCHMARK_RECORDS_LIST)
    }

    @Test
    fun sendersOpenAndScroll() = benchmarkRule.measureRepeated(
        packageName = APP_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = ITERATIONS,
        setupBlock = {
            startActivityAndWait()
            openTopLevelTab(BENCHMARK_NAV_ADVANCED, BENCHMARK_TAB_ADVANCED)
        },
    ) {
        device.clickResource(BENCHMARK_ADVANCED_RELAY_CONFIG)
        device.clickResource(BENCHMARK_RELAY_SENDERS)
        device.scrollTaggedRegion(BENCHMARK_SENDERS_LIST)
    }

    private fun MacrobenchmarkScope.openTopLevelTab(navResource: String, pageResource: String) {
        device.clickResource(navResource)
        device.waitForResource(pageResource)
        device.waitForIdle()
    }

    private fun UiDevice.clickResource(resourceName: String): UiObject2 {
        return waitForResource(resourceName).also {
            it.click()
            waitForIdle()
        }
    }

    private fun UiDevice.waitForResource(resourceName: String): UiObject2 {
        return wait(Until.findObject(By.res(resourceName)), UI_TIMEOUT_MS)
            ?: error("Timed out waiting for resource tag: $resourceName")
    }

    private fun UiDevice.scrollTaggedRegion(resourceName: String) {
        val target = waitForResource(resourceName)
        repeat(SCROLL_REPETITIONS) {
            target.fling(Direction.DOWN)
            waitForIdle()
        }
        repeat(SCROLL_REPETITIONS) {
            target.fling(Direction.UP)
            waitForIdle()
        }
    }

    private fun UiDevice.swipeTopLevel(
        left: Boolean,
        expectedPageResource: String,
        swipeZoneResource: String? = null,
    ) {
        val swipeZoneBounds = swipeZoneResource
            ?.let { resource -> waitForResource(resource).visibleBounds }
        val leftX = swipeZoneBounds
            ?.let { bounds -> bounds.left + bounds.width() / 5 }
            ?: displayWidth / 5
        val rightX = swipeZoneBounds
            ?.let { bounds -> bounds.right - bounds.width() / 5 }
            ?: displayWidth * 4 / 5
        val startX = if (left) rightX else leftX
        val endX = if (left) leftX else rightX
        val y = swipeZoneBounds?.centerY() ?: displayHeight / 5
        check(swipe(startX, y, endX, y, SWIPE_STEPS)) {
            "Top-level swipe was not injected"
        }
        waitForResource(expectedPageResource)
        waitForIdle()
    }

    private companion object {
        const val APP_PACKAGE = "io.github.magisk317.xinyi.relay"
        const val ITERATIONS = 5
        const val SCROLL_REPETITIONS = 2
        const val SWIPE_STEPS = 20
        const val UI_TIMEOUT_MS = 10_000L
        const val COMPILATION_MODE_ARGUMENT = "compilationMode"

        const val BENCHMARK_NAV_OVERVIEW = "xinyi_benchmark_nav_overview"
        const val BENCHMARK_NAV_APPS = "xinyi_benchmark_nav_apps"
        const val BENCHMARK_NAV_RECORDS = "xinyi_benchmark_nav_records"
        const val BENCHMARK_NAV_ADVANCED = "xinyi_benchmark_nav_advanced"
        const val BENCHMARK_NAV_SETTINGS = "xinyi_benchmark_nav_settings"
        const val BENCHMARK_TAB_OVERVIEW = "xinyi_benchmark_tab_overview"
        const val BENCHMARK_TAB_APPS = "xinyi_benchmark_tab_apps"
        const val BENCHMARK_TAB_RECORDS = "xinyi_benchmark_tab_records"
        const val BENCHMARK_TAB_ADVANCED = "xinyi_benchmark_tab_advanced"
        const val BENCHMARK_TAB_SETTINGS = "xinyi_benchmark_tab_settings"
        const val BENCHMARK_RECORDS_SWIPE_ZONE = "xinyi_benchmark_records_swipe_zone"
        const val BENCHMARK_APPS_LIST = "xinyi_benchmark_apps_list"
        const val BENCHMARK_RECORDS_LIST = "xinyi_benchmark_records_list"
        const val BENCHMARK_ADVANCED_RELAY_CONFIG = "xinyi_benchmark_advanced_relay_config"
        const val BENCHMARK_RELAY_SENDERS = "xinyi_benchmark_relay_senders"
        const val BENCHMARK_SENDERS_LIST = "xinyi_benchmark_senders_list"
    }
}
