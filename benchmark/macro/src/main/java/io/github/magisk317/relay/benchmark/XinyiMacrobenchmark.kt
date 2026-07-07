package io.github.magisk317.relay.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = APP_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
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
        compilationMode = CompilationMode.Partial(),
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
    fun appsListScroll() = benchmarkRule.measureRepeated(
        packageName = APP_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
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
        compilationMode = CompilationMode.Partial(),
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
        compilationMode = CompilationMode.Partial(),
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

    private companion object {
        const val APP_PACKAGE = "io.github.magisk317.xinyi.relay"
        const val ITERATIONS = 5
        const val SCROLL_REPETITIONS = 2
        const val UI_TIMEOUT_MS = 10_000L

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
        const val BENCHMARK_APPS_LIST = "xinyi_benchmark_apps_list"
        const val BENCHMARK_RECORDS_LIST = "xinyi_benchmark_records_list"
        const val BENCHMARK_ADVANCED_RELAY_CONFIG = "xinyi_benchmark_advanced_relay_config"
        const val BENCHMARK_RELAY_SENDERS = "xinyi_benchmark_relay_senders"
        const val BENCHMARK_SENDERS_LIST = "xinyi_benchmark_senders_list"
    }
}
