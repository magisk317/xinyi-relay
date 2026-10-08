package io.github.magisk317.relay.sender

import android.content.Context
import io.github.magisk317.relay.matrix.e2ee.MatrixE2eeRuntime
import io.github.magisk317.relay.matrix.e2ee.MatrixE2eeVerificationRuntime

/**
 * GitHub distribution variant: registers [GithubFeatureLoader] with the provider.
 * This works for both github×noE2ee and github×withE2ee since [GithubFeatureLoader]
 * is defined in both e2ee source sets.
 */
object MatrixE2eeSetup {
    @Suppress("UNUSED_PARAMETER")
    fun init(context: Context) {
        MatrixE2eeAvailabilityProvider.install(GithubFeatureLoader)
        MatrixE2eeSenderProvider.install(MatrixE2eeRuntime)
        MatrixE2eeVerificationProvider.install(MatrixE2eeVerificationRuntime)
    }
}
