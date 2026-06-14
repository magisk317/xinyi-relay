package io.github.magisk317.relay.sender

import android.content.Context

/**
 * GitHub distribution variant: registers [GithubFeatureLoader] with the provider.
 * This works for both github×noE2ee and github×withE2ee since [GithubFeatureLoader]
 * is defined in both e2ee source sets.
 */
object MatrixE2eeSetup {
    @Suppress("UNUSED_PARAMETER")
    fun init(context: Context) {
        MatrixE2eeAvailabilityProvider.install(GithubFeatureLoader)
        MatrixE2eeVerificationProvider.install(MatrixE2eeVerificationManager)
    }
}
