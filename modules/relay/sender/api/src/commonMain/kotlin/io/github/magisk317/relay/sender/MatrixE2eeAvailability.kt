package io.github.magisk317.relay.sender

/**
 * Reports the runtime availability of the Matrix E2EE module.
 *
 * Implementations differ by build variant:
 * - Play: checks DFM install status via SplitInstallManager
 * - GitHub: tracks the separately versioned plugin APK
 *   (features/matrix-e2ee-plugin) that is downloaded and DexClassLoader-loaded
 *   at runtime; until then the plaintext stubs stay installed
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

    /**
     * Request removal of the E2EE module to reclaim its storage.
     *
     * GitHub: deletes the plugin APK, its extracted native libraries and the
     * optimized dex cache and restores the plaintext stubs, so Matrix sends
     * fall back to unencrypted delivery. Play: defers the DFM uninstall until
     * the app is backgrounded (SplitInstallManager cannot uninstall a module
     * the app is currently using), so [onSuccess] only guarantees the request
     * was accepted. No-op on variants where the module is bundled or not
     * applicable.
     *
     * @param onSuccess called when the module has been removed (or the
     *   removal request was accepted on Play)
     * @param onFailure called with error message on failure
     */
    fun requestUninstall(
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

    /**
     * Uninstall requested; the module files are being removed.
     * Play: the uninstall only completes once the app is backgrounded.
     */
    UNINSTALLING,

    /** Current build flavor does not support E2EE (e.g. fdroid). */
    NOT_APPLICABLE,
}
