package io.github.magisk317.relay.ui.home.appconfig

import io.github.magisk317.relay.android.data.db.entity.AppInfo
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppConfigListPolicyTest {

    @Test
    fun appInfoHasEffectiveConfig_detectsPersistedConfig() {
        assertFalse(appInfoHasEffectiveConfig(app("com.example.empty")))
        assertTrue(appInfoHasEffectiveConfig(app("com.example.blocked", blocked = true)))
        assertTrue(appInfoHasEffectiveConfig(app("com.example.forwarding", forwardingConfigured = true)))
        assertTrue(appInfoHasEffectiveConfig(app("com.example.template", notifyTemplate = "{{MSG}}")))
    }

    @Test
    fun filterAndSortAppConfigs_pinsConfiguredAppsBeforeLabelSort() {
        val apps = listOf(
            app("com.example.zeta", label = "Zeta"),
            app("com.example.alpha", label = "Alpha"),
            app("com.example.blocked", label = "Omega", blocked = true),
            app("com.example.forwarding-off", label = "Gamma", forwardingConfigured = true),
            app("com.example.forwarding-on", label = "Delta", forwarding = true, forwardingConfigured = true),
            app("com.example.template", label = "Beta", notifyTemplate = "{{MSG}}"),
        )

        val result = filterAndSortAppConfigs(
            apps = apps,
            filterText = "",
            hideSystemApps = false,
            systemPackages = emptySet(),
            sortOption = AppConfigViewModel.SortOption.LABEL,
            isAscending = true,
        )

        assertEquals(
            listOf(
                "com.example.blocked",
                "com.example.forwarding-on",
                "com.example.template",
                "com.example.forwarding-off",
                "com.example.alpha",
                "com.example.zeta",
            ),
            result.map { it.packageName },
        )
    }

    @Test
    fun filterAndSortAppConfigs_filtersByLabelOrPackageAndHidesSystemApps() {
        val apps = listOf(
            app("com.alpha.mail", label = "Mail"),
            app("com.beta.chat", label = "Talk"),
            app("com.system.mail", label = "System Mail"),
        )

        val result = filterAndSortAppConfigs(
            apps = apps,
            filterText = "mail",
            hideSystemApps = true,
            systemPackages = setOf("com.system.mail"),
            sortOption = AppConfigViewModel.SortOption.PACKAGE,
            isAscending = true,
        )

        assertEquals(listOf("com.alpha.mail"), result.map { it.packageName })
    }

    @Test
    fun filterAndSortAppConfigs_sortsUsageDescendingWhenRequested() {
        val apps = listOf(
            app("com.example.low", label = "Low"),
            app("com.example.high", label = "High"),
            app("com.example.none", label = "None"),
        )

        val result = filterAndSortAppConfigs(
            apps = apps,
            filterText = "",
            hideSystemApps = false,
            systemPackages = emptySet(),
            sortOption = AppConfigViewModel.SortOption.USAGE,
            isAscending = false,
            usageStatsByPackage = mapOf(
                "com.example.low" to 10L,
                "com.example.high" to 100L,
            ),
        )

        assertEquals(
            listOf("com.example.high", "com.example.low", "com.example.none"),
            result.map { it.packageName },
        )
    }

    @Test
    fun visibleAppCountAfterFilter_resetsOrClampsWindow() {
        assertEquals(80, visibleAppCountAfterFilter(previousVisibleCount = 20, resetVisibleWindow = true))
        assertEquals(120, visibleAppCountAfterFilter(previousVisibleCount = 120, resetVisibleWindow = false))
        assertEquals(40, visibleAppCountAfterFilter(previousVisibleCount = 40, resetVisibleWindow = false))
    }

    @Test
    fun visibleAppCountAfterLoadMore_growsByPageAndClampsAtTotalSize() {
        assertEquals(160, visibleAppCountAfterLoadMore(currentVisibleCount = 80))
        assertEquals(240, visibleAppCountAfterLoadMore(currentVisibleCount = 160))
    }

    @Test
    fun assembleAppConfigList_appliesQueryStateAndClampsVisibleApps() {
        val state = AppConfigQueryState(
            sourceApps = listOf(
                app("com.alpha", label = "Alpha"),
                app("com.system.beta", label = "Beta"),
                app("com.gamma", label = "Gamma", blocked = true),
            ).toImmutableList(),
            systemPackages = setOf("com.system.beta"),
            visibleCount = 2,
            hideSystemApps = true,
            searchQuery = "",
            sortOption = AppConfigViewModel.SortOption.LABEL,
            isAscending = true,
        )

        val assembly = assembleAppConfigList(state)

        assertEquals(listOf("com.gamma", "com.alpha"), assembly.visibleApps.map { it.packageName })
        assertFalse(assembly.hasMoreApps)
    }

    @Test
    fun assembleAppConfigList_reportsHasMoreWhenFilteredResultExceedsVisibleWindow() {
        val state = AppConfigQueryState(
            sourceApps = listOf(
                app("com.alpha", label = "Alpha"),
                app("com.beta", label = "Beta"),
                app("com.gamma", label = "Gamma"),
            ).toImmutableList(),
            visibleCount = 2,
            hideSystemApps = false,
            searchQuery = "",
            sortOption = AppConfigViewModel.SortOption.LABEL,
            isAscending = true,
        )

        val assembly = assembleAppConfigList(state)

        assertEquals(listOf("com.alpha", "com.beta"), assembly.visibleApps.map { it.packageName })
        assertTrue(assembly.hasMoreApps)
    }

    private fun app(
        packageName: String,
        label: String = packageName,
        blocked: Boolean = false,
        forwarding: Boolean = false,
        forwardingConfigured: Boolean = false,
        notifyTemplate: String = "",
    ): AppInfo {
        return AppInfo(
            packageName = packageName,
            label = label,
            blocked = blocked,
            forwarding = forwarding,
            forwardingConfigured = forwardingConfigured,
            notifyTemplate = notifyTemplate,
        )
    }
}
