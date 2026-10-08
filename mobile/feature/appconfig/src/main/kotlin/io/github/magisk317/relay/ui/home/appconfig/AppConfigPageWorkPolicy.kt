package io.github.magisk317.relay.ui.home.appconfig

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

internal data class AppConfigPageWorkPolicy(
    val observeUiState: Boolean,
    val refreshData: Boolean,
    val collectEvents: Boolean,
    val paginate: Boolean,
    val preloadIcons: Boolean,
)

internal fun appConfigPageWorkPolicy(isActive: Boolean): AppConfigPageWorkPolicy =
    AppConfigPageWorkPolicy(
        observeUiState = isActive,
        refreshData = isActive,
        collectEvents = isActive,
        paginate = isActive,
        preloadIcons = isActive,
    )

internal fun retainedAppConfigUiStateFlow(
    policy: AppConfigPageWorkPolicy,
    uiState: StateFlow<AppConfigUiState>,
): Flow<AppConfigUiState> = if (policy.observeUiState) {
    uiState
} else {
    flowOf(uiState.value)
}

internal class AppConfigRequestGeneration {
    private val generation = AtomicLong(0L)

    fun next(): Long = generation.incrementAndGet()

    fun invalidate(candidate: Long): Boolean = generation.compareAndSet(candidate, candidate + 1L)

    fun isCurrent(candidate: Long): Boolean = generation.get() == candidate
}

/** Tracks local mutations independently from refresh requests and keeps the newest package write. */
internal class AppConfigMutationTracker {
    private val generation = AtomicLong(0L)
    private val latestByPackage = ConcurrentHashMap<String, Long>()

    fun record(packageName: String): Long {
        val mutation = generation.incrementAndGet()
        latestByPackage[packageName] = mutation
        return mutation
    }

    fun currentGeneration(): Long = generation.get()

    fun isLatest(packageName: String, mutation: Long): Boolean =
        latestByPackage[packageName] == mutation
}
