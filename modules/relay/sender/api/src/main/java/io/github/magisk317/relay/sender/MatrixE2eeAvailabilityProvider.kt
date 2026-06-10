package io.github.magisk317.relay.sender

/**
 * Service locator that exposes the current [MatrixE2eeAvailability] instance
 * to modules that cannot directly depend on `relay/sender` internals (e.g. UI layer).
 *
 * The concrete [MatrixE2eeAvailability] implementation (GithubFeatureLoader or
 * PlayFeatureLoader) registers itself via [install] during app initialization.
 */
object MatrixE2eeAvailabilityProvider {

    @Volatile
    private var instance: MatrixE2eeAvailability? = null

    /**
     * Register the availability implementation. Called once during app startup
     * by the concrete FeatureLoader or [SenderRuntimeInstaller].
     */
    fun install(availability: MatrixE2eeAvailability) {
        instance = availability
    }

    /**
     * Returns the registered [MatrixE2eeAvailability], or a default that reports
     * [E2eeModuleStatus.NOT_APPLICABLE] if nothing was registered.
     */
    fun get(): MatrixE2eeAvailability = instance ?: DefaultUnavailable

    /** Whether an implementation has been installed. */
    val isInstalled: Boolean get() = instance != null

    private object DefaultUnavailable : MatrixE2eeAvailability {
        override val isAvailable: Boolean get() = false
        override val status: E2eeModuleStatus get() = E2eeModuleStatus.NOT_APPLICABLE
    }
}
