package io.github.magisk317.relay.sender

import android.content.Context
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus

/**
 * Play variant implementation of [MatrixE2eeAvailability].
 *
 * Uses Google Play Feature Delivery ([SplitInstallManager]) to detect whether
 * the `matrix_e2ee` dynamic feature module is installed, trigger on-demand
 * installation, and load it via [SplitCompat] after install completes.
 */
internal class PlayFeatureLoader(private val context: Context) : MatrixE2eeAvailability {

    private val splitInstallManager: SplitInstallManager =
        SplitInstallManagerFactory.create(context)

    @Volatile
    private var currentStatus: E2eeModuleStatus = checkInitialStatus()

    @Volatile
    private var downloadProgress: Int = 0

    @Volatile
    private var lastErrorMessage: String? = null

    override val isAvailable: Boolean
        get() = currentStatus == E2eeModuleStatus.AVAILABLE

    override val status: E2eeModuleStatus
        get() = currentStatus

    /** Current download progress percentage (0–100). Only meaningful when status is DOWNLOADING. */
    override val progress: Int
        get() = downloadProgress

    /** Error message from the last failed install attempt, if any. */
    override val errorMessage: String?
        get() = lastErrorMessage

    /**
     * Trigger installation of the `matrix_e2ee` dynamic feature module.
     *
     * @param onProgress called with progress percentage (0–100) during download
     * @param onSuccess called when module installation completes successfully
     * @param onFailure called with error message when installation fails
     */
    override fun requestInstall(
        onProgress: ((Int) -> Unit)?,
        onSuccess: (() -> Unit)?,
        onFailure: ((String) -> Unit)?,
    ) {
        if (currentStatus == E2eeModuleStatus.AVAILABLE) {
            onSuccess?.invoke()
            return
        }
        if (currentStatus == E2eeModuleStatus.DOWNLOADING) {
            // Already in progress — no-op
            return
        }

        currentStatus = E2eeModuleStatus.DOWNLOADING
        downloadProgress = 0
        lastErrorMessage = null

        val request = SplitInstallRequest.newBuilder()
            .addModule(MODULE_NAME)
            .build()

        val listener = object : SplitInstallStateUpdatedListener {
            override fun onStateUpdate(state: SplitInstallSessionState) {
                when (state.status()) {
                    SplitInstallSessionStatus.DOWNLOADING -> {
                        val total = state.totalBytesToDownload()
                        val downloaded = state.bytesDownloaded()
                        val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                        downloadProgress = percent.coerceIn(0, 100)
                        currentStatus = E2eeModuleStatus.DOWNLOADING
                        onProgress?.invoke(downloadProgress)
                    }

                    SplitInstallSessionStatus.INSTALLED -> {
                        splitInstallManager.unregisterListener(this)
                        downloadProgress = 100
                        loadModuleAfterInstall()
                        if (currentStatus == E2eeModuleStatus.AVAILABLE) {
                            onSuccess?.invoke()
                        } else {
                            val msg = lastErrorMessage ?: "Module load failed after install"
                            onFailure?.invoke(msg)
                        }
                    }

                    SplitInstallSessionStatus.FAILED -> {
                        splitInstallManager.unregisterListener(this)
                        val errorCode = state.errorCode()
                        val msg = "DFM install failed: errorCode=$errorCode"
                        lastErrorMessage = msg
                        currentStatus = E2eeModuleStatus.INSTALL_FAILED
                        downloadProgress = 0
                        SLog.e(TAG, msg)
                        onFailure?.invoke(msg)
                    }

                    SplitInstallSessionStatus.CANCELED -> {
                        splitInstallManager.unregisterListener(this)
                        val msg = "DFM install canceled by user"
                        lastErrorMessage = msg
                        currentStatus = E2eeModuleStatus.INSTALL_FAILED
                        downloadProgress = 0
                        SLog.w(TAG, msg)
                        onFailure?.invoke(msg)
                    }

                    SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                        // Large download requires user confirmation — treat as still downloading
                        currentStatus = E2eeModuleStatus.DOWNLOADING
                    }

                    SplitInstallSessionStatus.PENDING,
                    SplitInstallSessionStatus.CANCELING,
                    -> {
                        // Intermediate states — keep current DOWNLOADING status
                    }

                    else -> {
                        // Unknown status — no-op
                    }
                }
            }
        }

        splitInstallManager.registerListener(listener)
        splitInstallManager.startInstall(request)
            .addOnFailureListener { exception ->
                splitInstallManager.unregisterListener(listener)
                val msg = "DFM install request failed: ${exception.message}"
                lastErrorMessage = msg
                currentStatus = E2eeModuleStatus.INSTALL_FAILED
                downloadProgress = 0
                SLog.e(TAG, msg)
                onFailure?.invoke(msg)
            }
    }

    /**
     * Check the initial installation status of the module.
     * If already installed (e.g. after process restart), mark as AVAILABLE immediately.
     */
    private fun checkInitialStatus(): E2eeModuleStatus {
        return if (splitInstallManager.installedModules.contains(MODULE_NAME)) {
            loadModuleAfterInstall()
            currentStatus // May be AVAILABLE or LOAD_FAILED depending on load result
        } else {
            E2eeModuleStatus.NOT_INSTALLED
        }
    }

    /**
     * After module installation, use [SplitCompat] to make the module's classes
     * and resources available in the current process, then verify loading.
     */
    private fun loadModuleAfterInstall() {
        try {
            SplitCompat.install(context)
            // Verify the module is loadable by probing a known class
            Class.forName(PROBE_CLASS_NAME)
            installFeatureBridge()
            currentStatus = E2eeModuleStatus.AVAILABLE
            SLog.i(TAG, "matrix_e2ee module loaded successfully via SplitCompat")
        } catch (e: Exception) {
            val msg = "Failed to load matrix_e2ee module after install: ${e.message}"
            lastErrorMessage = msg
            currentStatus = E2eeModuleStatus.LOAD_FAILED
            SLog.e(TAG, msg)
        }
    }

    private fun installFeatureBridge() {
        val bridgeClass = Class.forName(BRIDGE_CLASS_NAME)
        bridgeClass.getMethod("install", Context::class.java)
            .invoke(null, context.applicationContext)
    }

    companion object {
        private const val TAG = "PlayFeatureLoader"

        /** Name of the dynamic feature module as declared in the DFM's AndroidManifest. */
        private const val MODULE_NAME = "matrix_e2ee"

        /**
         * A class from the DFM used to verify the module loaded correctly.
         * This class is expected to exist in the feature/matrix-e2ee module.
         */
        private const val PROBE_CLASS_NAME =
            "io.github.magisk317.relay.feature.matrix.e2ee.MatrixE2eeFeature"

        /**
         * Runtime bridge exported by the DFM. The base APK reaches it through
         * reflection so the Play noE2ee shell does not statically link the
         * matrix-rust-sdk implementation.
         */
        private const val BRIDGE_CLASS_NAME =
            "io.github.magisk317.relay.feature.matrix.e2ee.MatrixE2eeBridge"
    }
}
