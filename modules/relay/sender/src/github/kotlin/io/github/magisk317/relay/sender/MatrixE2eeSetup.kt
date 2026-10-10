package io.github.magisk317.relay.sender

import android.content.Context

/**
 * GitHub distribution variant: registers [GithubPluginFeatureLoader] with the
 * availability provider. The sender/verification implementations arrive from the
 * E2EE plugin APK at runtime; until then the plaintext stubs stay installed.
 */
object MatrixE2eeSetup {
    fun init(context: Context) {
        MatrixE2eeAvailabilityProvider.install(GithubPluginFeatureLoader)
        if (!MatrixE2eeVerificationProvider.isInstalled) {
            MatrixE2eeVerificationProvider.install(MatrixE2eeVerificationManager)
        }
        GithubPluginFeatureLoader.init(context)
    }
}
