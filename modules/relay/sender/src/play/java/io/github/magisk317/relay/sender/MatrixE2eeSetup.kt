package io.github.magisk317.relay.sender

import android.content.Context

/**
 * Play distribution variant: registers [PlayFeatureLoader] with the provider.
 * The Play variant uses Dynamic Feature Module for E2EE delivery.
 */
object MatrixE2eeSetup {
    fun init(context: Context) {
        val loader = PlayFeatureLoader(context)
        MatrixE2eeAvailabilityProvider.install(loader)
        if (!MatrixE2eeVerificationProvider.isInstalled) {
            MatrixE2eeVerificationProvider.install(MatrixE2eeVerificationManager)
        }
    }
}
