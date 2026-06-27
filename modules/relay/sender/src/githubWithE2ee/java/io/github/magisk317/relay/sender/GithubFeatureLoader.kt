package io.github.magisk317.relay.sender

import org.matrix.rustcomponents.sdk.LogLevel
import org.matrix.rustcomponents.sdk.TracingConfiguration
import org.matrix.rustcomponents.sdk.initPlatform

/**
 * GitHub variant (withE2ee) implementation of [MatrixE2eeAvailability].
 *
 * In the withE2ee source set, the matrix-rust-sdk native library (libmatrix_sdk_ffi.so)
 * is statically bundled into the APK at compile time. We verify availability by
 * attempting to load the native library via JNA (which the SDK uses internally).
 * If the .so is present and loadable, E2EE is available.
 */
internal object GithubFeatureLoader : MatrixE2eeAvailability {

    private const val TAG = "GithubFeatureLoader"
    private const val NATIVE_LIB_NAME = "matrix_sdk_ffi"

    private val detectedStatus: E2eeModuleStatus by lazy { detectModule() }

    override val isAvailable: Boolean
        get() = detectedStatus == E2eeModuleStatus.AVAILABLE

    override val status: E2eeModuleStatus
        get() = detectedStatus

    @Suppress("TooGenericExceptionCaught")
    private fun detectModule(): E2eeModuleStatus {
        return try {
            // The withE2ee variant bundles libmatrix_sdk_ffi.so at compile time.
            // Verify the native library is loadable on this device.
            System.loadLibrary(NATIVE_LIB_NAME)
            SLog.i(TAG, "E2EE native library loaded successfully: lib$NATIVE_LIB_NAME.so")

            // Initialize the Rust SDK platform support (required for TLS/rustls on Android).
            // This must be called once before building any Matrix client.
            initPlatform(
                TracingConfiguration(
                    logLevel = LogLevel.INFO,
                    traceLogPacks = emptyList(),
                    extraTargets = emptyList(),
                    writeToStdoutOrSystem = false,
                    writeToFiles = null,
                ),
                useLightweightTokioRuntime = false,
            )
            SLog.i(TAG, "Matrix SDK platform initialized (initPlatform)")

            E2eeModuleStatus.AVAILABLE
        } catch (e: UnsatisfiedLinkError) {
            SLog.e(TAG, "E2EE native library not found or incompatible: ${e.message}")
            E2eeModuleStatus.LOAD_FAILED
        } catch (e: SecurityException) {
            SLog.e(TAG, "E2EE native library load denied: ${e.message}")
            E2eeModuleStatus.LOAD_FAILED
        } catch (e: Exception) {
            SLog.e(TAG, "E2EE module detection failed: ${e.message}")
            E2eeModuleStatus.LOAD_FAILED
        }
    }
}
