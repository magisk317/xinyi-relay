package io.github.magisk317.relay.ui.home.appconfig

import io.github.magisk317.relay.android.data.db.entity.AppInfo

internal const val APP_CONFIG_LIST_PAGE_SIZE = 80

internal fun appInfoHasEffectiveConfig(appInfo: AppInfo): Boolean {
    return appInfo.blocked || appInfo.forwardingConfigured || appInfo.notifyTemplate.isNotBlank()
}

internal fun filterAndSortAppConfigs(
    apps: Iterable<AppInfo>,
    filterText: String,
    hideSystemApps: Boolean,
    systemPackages: Set<String>,
    sortOption: AppConfigViewModel.SortOption,
    isAscending: Boolean,
    usageStatsByPackage: Map<String, Long> = emptyMap(),
): List<AppInfo> {
    val normalizedFilter = filterText.lowercase()
    return apps.asSequence()
        .filter { appInfo ->
            if (hideSystemApps && systemPackages.contains(appInfo.packageName)) {
                return@filter false
            }
            if (normalizedFilter.isEmpty()) {
                true
            } else {
                val lowerLabel = appInfo.label?.lowercase() ?: ""
                val lowerPkg = appInfo.packageName.lowercase()
                lowerLabel.contains(normalizedFilter) || lowerPkg.contains(normalizedFilter)
            }
        }
        .sortedWith(appConfigComparator(sortOption, isAscending, usageStatsByPackage))
        .toList()
}

internal fun visibleAppCountAfterFilter(
    totalSize: Int,
    previousVisibleCount: Int,
    resetVisibleWindow: Boolean,
): Int {
    return if (resetVisibleWindow || previousVisibleCount <= 0) {
        minOf(APP_CONFIG_LIST_PAGE_SIZE, totalSize)
    } else {
        minOf(previousVisibleCount, totalSize)
    }
}

internal fun visibleAppCountAfterLoadMore(
    totalSize: Int,
    currentVisibleCount: Int,
): Int = minOf(currentVisibleCount + APP_CONFIG_LIST_PAGE_SIZE, totalSize)

private fun appConfigComparator(
    sortOption: AppConfigViewModel.SortOption,
    isAscending: Boolean,
    usageStatsByPackage: Map<String, Long>,
): Comparator<AppInfo> {
    return Comparator { first, second ->
        val configCompare = compareConfigPriority(first, second)
        if (configCompare != 0) {
            return@Comparator configCompare
        }

        val result = when (sortOption) {
            AppConfigViewModel.SortOption.LABEL -> compareString(first.label, second.label)
            AppConfigViewModel.SortOption.PACKAGE -> compareString(first.packageName, second.packageName)
            AppConfigViewModel.SortOption.USAGE -> {
                val firstUsage = usageStatsByPackage[first.packageName] ?: 0L
                val secondUsage = usageStatsByPackage[second.packageName] ?: 0L
                firstUsage.compareTo(secondUsage)
            }
            AppConfigViewModel.SortOption.SELECTION -> compareString(first.label, second.label)
        }

        if (isAscending) result else -result
    }
}

private fun compareConfigPriority(first: AppInfo, second: AppInfo): Int {
    val firstHasConfig = appInfoHasEffectiveConfig(first)
    val secondHasConfig = appInfoHasEffectiveConfig(second)
    if (firstHasConfig != secondHasConfig) {
        return if (firstHasConfig) -1 else 1
    }

    if (first.blocked != second.blocked) {
        return if (first.blocked) -1 else 1
    }
    if (first.forwarding != second.forwarding) {
        return if (first.forwarding) -1 else 1
    }
    val firstHasTemplate = first.notifyTemplate.isNotBlank()
    val secondHasTemplate = second.notifyTemplate.isNotBlank()
    if (firstHasTemplate != secondHasTemplate) {
        return if (firstHasTemplate) -1 else 1
    }
    return 0
}

private fun compareString(first: String?, second: String?): Int {
    if (first == null && second == null) return 0
    if (first == null) return -1
    if (second == null) return 1
    return first.compareTo(second, ignoreCase = true)
}
