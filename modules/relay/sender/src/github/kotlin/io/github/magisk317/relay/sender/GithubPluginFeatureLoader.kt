package io.github.magisk317.relay.sender

import android.content.Context
import io.github.magisk317.relay.sender.plugin.E2eePluginLoader

/**
 * GitHub distribution implementation of [MatrixE2eeAvailability].
 *
 * The GitHub flavor ships no E2EE code at all: the matrix-rust-sdk runtime lives
 * in a separately versioned plugin APK (`features/matrix-e2ee-plugin`) published
 * to the GitLab generic package registry under `xinyi-e2ee-plugin/<sdkVersion>/`.
 * This loader downloads the plugin whose version matches
 * [BuildConfig.MATRIX_PLUGIN_SDK_VERSION], verifies its APK signature against the
 * host app (same release keystore), then loads it through a [android.database
 * .DexClassLoader] and lets the plugin install the sender/verification providers.
 *
 * Plugin cadence is independent of the main APK: a new plugin only appears when
 * the upstream matrix-rust-sdk is upgraded, so older app builds keep resolving
 * their own plugin version forever.
 */
internal object GithubPluginFeatureLoader : MatrixE2eeAvailability {

    private val loader = E2eePluginLoader()

    override val isAvailable: Boolean
        get() = loader.state == E2eeModuleStatus.AVAILABLE

    override val status: E2eeModuleStatus
        get() = loader.state

    override val progress: Int
        get() = loader.progress

    override val errorMessage: String?
        get() = loader.errorMessage

    /**
     * Download (if needed) and load the E2EE plugin APK matching the expected
     * matrix-rust-sdk version, then install the sender/verification providers
     * from inside the plugin. Safe to call repeatedly.
     */
    override fun requestInstall(
        onProgress: ((Int) -> Unit)?,
        onSuccess: (() -> Unit)?,
        onFailure: ((String) -> Unit)?,
    ) {
        loader.install(
            onProgress = onProgress,
            onSuccess = onSuccess,
            onFailure = onFailure,
        )
    }

    /**
     * Delete the downloaded plugin APK and its caches, drop the plugin
     * providers and fall back to plaintext delivery. Reinstalling downloads
     * the plugin again.
     */
    override fun requestUninstall(
        onSuccess: (() -> Unit)?,
        onFailure: ((String) -> Unit)?,
    ) {
        loader.uninstall(
            onSuccess = onSuccess,
            onFailure = onFailure,
        )
    }

    /** Load an already downloaded plugin on process start, if any. */
    fun init(context: Context) {
        loader.init(context)
    }
}
