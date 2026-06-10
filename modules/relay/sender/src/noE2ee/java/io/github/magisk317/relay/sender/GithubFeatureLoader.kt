package io.github.magisk317.relay.sender

/**
 * GitHub variant (noE2ee) implementation of [MatrixE2eeAvailability].
 *
 * This variant does not bundle the E2EE native library, so E2EE is never
 * applicable. Always reports [E2eeModuleStatus.NOT_APPLICABLE].
 */
internal object GithubFeatureLoader : MatrixE2eeAvailability {

    override val isAvailable: Boolean
        get() = false

    override val status: E2eeModuleStatus
        get() = E2eeModuleStatus.NOT_APPLICABLE
}
