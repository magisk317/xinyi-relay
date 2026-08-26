package io.github.magisk317.relay.ui.home.overview

import io.github.magisk317.smscode.runtime.contract.diagnostics.ActivationDiagnosticsSnapshot

internal const val CARD_STATUS = "status"
internal const val CARD_CHART = "chart"
internal const val CARD_APP_INFO = "app_info"
internal const val CARD_DEVICE_INFO = "device_info"
internal const val CARD_LINKS = "links"

internal val DEFAULT_OVERVIEW_CARD_ORDER: List<String> = listOf(
    CARD_STATUS,
    CARD_APP_INFO,
    CARD_DEVICE_INFO,
    CARD_LINKS,
    CARD_CHART,
)

internal val DEFAULT_OVERVIEW_ENABLED_CARD_IDS: Set<String> = setOf(
    CARD_STATUS,
    CARD_APP_INFO,
    CARD_DEVICE_INFO,
    CARD_LINKS,
    CARD_CHART,
)

internal data class OverviewUiState(
    val cardOrder: List<String> = DEFAULT_OVERVIEW_CARD_ORDER,
    val enabledCardIds: Set<String> = DEFAULT_OVERVIEW_ENABLED_CARD_IDS,
    val chartType: HomeChartType = HomeChartType.EVENTS,
    val chartWindow: HomeChartWindow = HomeChartWindow.ALL,
    val editMode: Boolean = false,
    val showAddCardSheet: Boolean = false,
    val showStatusDiagnostics: Boolean = false,
    val draggingCardId: String? = null,
    val dragOffsetY: Float = 0f,
    val batteryOptimizationExempted: Boolean = true,
    val runtimeSnapshot: OverviewRuntimeUiState = OverviewRuntimeUiState(),
)

internal data class OverviewRuntimeUiState(
    val runtimeConnected: Boolean = false,
    val mobileAutomationAllowed: Boolean = false,
    val activationDiagnostics: ActivationDiagnosticsSnapshot = ActivationDiagnosticsSnapshot(),
    val frameworkType: String = "",
    val frameworkVersion: String = "",
    val hasRootAccess: Boolean = false,
    val appVersionName: String = "",
    val appVersionCode: String = "",
    val chartSnapshot: HomeAnalyticsSnapshot? = null,
)

internal fun parseCardList(value: String): List<String> =
    value.split(',').map { it.trim() }.filter { it.isNotBlank() }

internal fun encodeCardList(items: List<String>): String = items.joinToString(",")

internal fun normalizeCardOrder(order: List<String>, fallback: List<String>): List<String> {
    val known = fallback.toSet()
    val filtered = order.filter { known.contains(it) }.distinct()
    val missing = fallback.filterNot { filtered.contains(it) }
    return filtered + missing
}

internal fun normalizeCardEnabled(enabled: Set<String>, fallback: Set<String>): Set<String> {
    val normalized = enabled.filter { fallback.contains(it) }.toSet()
    return if (normalized.isEmpty()) fallback else normalized
}

internal fun moveCardByVisible(order: List<String>, visible: List<String>, cardId: String, direction: Int): List<String> {
    val indexInVisible = visible.indexOf(cardId)
    if (indexInVisible == -1) return order
    val targetIndex = indexInVisible + direction
    if (targetIndex !in visible.indices) return order
    val targetId = visible[targetIndex]
    val mutable = order.toMutableList()
    mutable.remove(cardId)
    val insertIndex = mutable.indexOf(targetId).let { if (direction > 0) it + 1 else it }
    mutable.add(insertIndex, cardId)
    return mutable
}

internal sealed interface OverviewEvent {
    data class StatusDiagnosticsVisibilityChanged(val visible: Boolean) : OverviewEvent
}
