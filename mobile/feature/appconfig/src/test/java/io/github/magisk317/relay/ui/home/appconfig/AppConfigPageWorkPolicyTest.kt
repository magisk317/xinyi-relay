package io.github.magisk317.relay.ui.home.appconfig

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppConfigPageWorkPolicyTest {

    @Test
    fun activePage_enablesAllPageWork() {
        val policy = appConfigPageWorkPolicy(isActive = true)

        assertTrue(policy.observeUiState)
        assertTrue(policy.refreshData)
        assertTrue(policy.collectEvents)
        assertTrue(policy.paginate)
        assertTrue(policy.preloadIcons)
    }

    @Test
    fun inactivePage_disablesAllPageWork() {
        val policy = appConfigPageWorkPolicy(isActive = false)

        assertFalse(policy.observeUiState)
        assertFalse(policy.refreshData)
        assertFalse(policy.collectEvents)
        assertFalse(policy.paginate)
        assertFalse(policy.preloadIcons)
    }

    @Test
    fun inactivePage_retainsSnapshotWithoutFollowingLiveState() = runBlocking {
        val liveState = MutableStateFlow(AppConfigUiState(searchQuery = "retained"))
        val retained = retainedAppConfigUiStateFlow(
            policy = appConfigPageWorkPolicy(isActive = false),
            uiState = liveState,
        )

        liveState.value = AppConfigUiState(searchQuery = "new")

        assertEquals("retained", retained.first().searchQuery)
    }

    @Test
    fun invalidation_rejectsAnOlderRequestGeneration() {
        val generations = AppConfigRequestGeneration()
        val request = generations.next()

        assertTrue(generations.isCurrent(request))

        assertTrue(generations.invalidate(request))

        assertFalse(generations.isCurrent(request))
        assertFalse(generations.invalidate(request))
    }

    @Test
    fun mutationTracker_onlyKeepsTheNewestMutationPerPackage() {
        val mutations = AppConfigMutationTracker()
        val first = mutations.record("com.example.app")
        val second = mutations.record("com.example.app")
        val other = mutations.record("com.example.other")

        assertFalse(mutations.isLatest("com.example.app", first))
        assertTrue(mutations.isLatest("com.example.app", second))
        assertTrue(mutations.isLatest("com.example.other", other))
    }
}
