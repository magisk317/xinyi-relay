package io.github.magisk317.relay.feature.matrix.e2ee

import android.content.Context
import io.github.magisk317.relay.matrix.e2ee.MatrixE2eePlatform
import io.github.magisk317.relay.matrix.e2ee.MatrixE2eeRuntime
import io.github.magisk317.relay.matrix.e2ee.MatrixE2eeVerificationRuntime
import io.github.magisk317.relay.sender.MatrixE2eeSenderProvider
import io.github.magisk317.relay.sender.MatrixE2eeVerificationProvider
import io.github.magisk317.relay.sender.SLog

object MatrixE2eeBridge {
    private const val TAG = "MatrixE2eeBridge"

    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun install(context: Context) {
        MatrixE2eePlatform.initialize()
        MatrixE2eeSenderProvider.install(MatrixE2eeRuntime)
        MatrixE2eeVerificationProvider.install(MatrixE2eeVerificationRuntime)
        SLog.i(TAG, "Matrix E2EE dynamic feature bridge installed")
    }

    @JvmStatic
    fun isReady(): Boolean =
        MatrixE2eeSenderProvider.isInstalled && MatrixE2eeVerificationProvider.isInstalled
}
