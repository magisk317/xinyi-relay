package io.github.magisk317.relay.sender

/**
 * Distribution implementation for variants that do not bundle Matrix E2EE.
 * E2EE is therefore not applicable.
 */
internal object NoE2eeFeatureLoader : MatrixE2eeAvailability {

    override val isAvailable: Boolean
        get() = false

    override val status: E2eeModuleStatus
        get() = E2eeModuleStatus.NOT_APPLICABLE
}
