package io.github.magisk317.relay.sender

/**
 * Reports the runtime availability of the Matrix E2EE module.
 *
 * Implementations differ by build variant:
 * - Play: checks DFM install status via SplitInstallManager
 * - GitHub-withE2ee: detects bundled native library at startup
 * - GitHub-noE2ee / fdroid: always reports [E2eeModuleStatus.NOT_APPLICABLE]
 */
interface MatrixE2eeAvailability {
    /** Whether the E2EE module is loaded and ready for use. */
    val isAvailable: Boolean

    /** Detailed status of the E2EE module lifecycle. */
    val status: E2eeModuleStatus

    /** Current download progress percentage (0–100). Only meaningful when status is DOWNLOADING. */
    val progress: Int get() = 0

    /** Error message from the last failed install attempt, if any. */
    val errorMessage: String? get() = null

    /**
     * Request installation of the E2EE module (Play variant only).
     * No-op on variants where E2EE is bundled or not applicable.
     *
     * @param onProgress called with download percentage (0–100)
     * @param onSuccess called when installation completes successfully
     * @param onFailure called with error message on failure
     */
    fun requestInstall(
        onProgress: ((Int) -> Unit)? = null,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null,
    ) {
        // Default no-op for variants that don't support dynamic installation
    }
}

/**
 * Lifecycle states of the E2EE dynamic feature module.
 */
enum class E2eeModuleStatus {
    /** Module is loaded and ready — encryption can proceed. */
    AVAILABLE,

    /** Play: not yet installed. GitHub-default: module not bundled. */
    NOT_INSTALLED,

    /** Play: module download is in progress. */
    DOWNLOADING,

    /** Play: module installation failed (network error, insufficient storage, etc.). */
    INSTALL_FAILED,

    /** Module is installed but native library loading failed at runtime. */
    LOAD_FAILED,

    /** Current build flavor does not support E2EE (e.g. fdroid). */
    NOT_APPLICABLE,
}
