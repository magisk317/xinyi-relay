package io.github.magisk317.relay.common.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PackageUtilsUpdateStrategyTest {

    @Test
    fun resolveUpdateDestination_prefersPlayWhenAvailable() {
        val destination = PackageUtils.resolveUpdateDestination(playStoreAvailable = true)
        assertEquals(PackageUtils.UpdateDestination.PLAY, destination)
    }

    @Test
    fun resolveUpdateDestination_fallsBackToGithubWhenPlayUnavailable() {
        val destination = PackageUtils.resolveUpdateDestination(playStoreAvailable = false)
        assertEquals(PackageUtils.UpdateDestination.GITHUB, destination)
    }
}
