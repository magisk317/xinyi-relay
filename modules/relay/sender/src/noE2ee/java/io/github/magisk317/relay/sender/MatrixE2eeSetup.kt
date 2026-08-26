package io.github.magisk317.relay.sender

import android.content.Context

/** Registers the no-E2EE availability implementation for this distribution. */
object MatrixE2eeSetup {
    @Suppress("UNUSED_PARAMETER")
    fun init(context: Context) {
        MatrixE2eeAvailabilityProvider.install(NoE2eeFeatureLoader)
        MatrixE2eeVerificationProvider.install(MatrixE2eeVerificationManager)
    }
}
