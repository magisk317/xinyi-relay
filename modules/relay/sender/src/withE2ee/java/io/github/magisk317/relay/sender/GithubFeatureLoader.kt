package io.github.magisk317.relay.sender

/**
 * GitHub variant (withE2ee) implementation of [MatrixE2eeAvailability].
 *
 * Detects bundled native crypto library availability at runtime using
 * [Class.forName] to load the DFM marker class [MatrixE2eeFeature].
 * If the class is loadable, the native library is bundled and E2EE is available.
 * If loading fails (ClassNotFoundException or linkage error), reports [E2eeModuleStatus.LOAD_FAILED].
 */
internal object GithubFeatureLoader : MatrixE2eeAvailability {

    private const val TAG = "GithubFeatureLoader"
    private const val FEATURE_CLASS_NAME =
        "io.github.magisk317.relay.feature.matrix.e2ee.MatrixE2eeFeature"

    private val detectedStatus: E2eeModuleStatus by lazy { detectModule() }

    override val isAvailable: Boolean
        get() = detectedStatus == E2eeModuleStatus.AVAILABLE

    override val status: E2eeModuleStatus
        get() = detectedStatus

    private fun detectModule(): E2eeModuleStatus {
        return try {
            Class.forName(FEATURE_CLASS_NAME)
            SLog.i(TAG, "E2EE module detected: $FEATURE_CLASS_NAME loaded successfully")
            E2eeModuleStatus.AVAILABLE
        } catch (e: ClassNotFoundException) {
            SLog.e(TAG, "E2EE module not found: ${e.message}")
            E2eeModuleStatus.LOAD_FAILED
        } catch (e: LinkageError) {
            SLog.e(TAG, "E2EE module linkage error: ${e.message}")
            E2eeModuleStatus.LOAD_FAILED
        } catch (e: Exception) {
            SLog.e(TAG, "E2EE module detection failed: ${e.message}")
            E2eeModuleStatus.LOAD_FAILED
        }
    }
}
