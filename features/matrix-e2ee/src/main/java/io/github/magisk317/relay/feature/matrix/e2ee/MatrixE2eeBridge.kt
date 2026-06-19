package io.github.magisk317.relay.feature.matrix.e2ee

import android.content.Context
import io.github.magisk317.relay.feature.matrix.e2ee.impl.MatrixE2eeFeatureSender
import io.github.magisk317.relay.feature.matrix.e2ee.impl.MatrixE2eeFeatureVerification
import io.github.magisk317.relay.sender.MatrixE2eeSenderProvider
import io.github.magisk317.relay.sender.MatrixE2eeVerificationProvider
import io.github.magisk317.relay.sender.SLog

object MatrixE2eeBridge {
    private const val TAG = "MatrixE2eeBridge"

    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun install(context: Context) {
        MatrixE2eeSenderProvider.install(MatrixE2eeFeatureSender)
        MatrixE2eeVerificationProvider.install(MatrixE2eeFeatureVerification)
        SLog.i(TAG, "Matrix E2EE dynamic feature bridge installed")
    }

    @JvmStatic
    fun isReady(): Boolean =
        MatrixE2eeSenderProvider.isInstalled && MatrixE2eeVerificationProvider.isInstalled
}
