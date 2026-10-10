package io.github.magisk317.relay.matrix.e2ee.plugin

import android.content.Context
import android.util.Log
import io.github.magisk317.relay.matrix.e2ee.MatrixE2eePlatform
import io.github.magisk317.relay.matrix.e2ee.MatrixE2eeRuntime
import io.github.magisk317.relay.matrix.e2ee.MatrixE2eeVerificationRuntime
import io.github.magisk317.relay.matrix.e2ee.plugin.BuildConfig
import io.github.magisk317.relay.sender.MatrixE2eeSenderProvider
import io.github.magisk317.relay.sender.MatrixE2eeVerificationProvider

/**
 * Runtime entry point of the Matrix E2EE plugin APK.
 *
 * The host app (GitHub flavor) never statically links this module. It downloads
 * the plugin whose `sdkVersion` matches the pinned `matrix-sdk-android` catalog
 * version, verifies the APK signature against its own release keystore, then
 * reflectively calls [install]. The plugin dex resolves the sender API provider
 * objects from the host's classloader (parent-first delegation), so a single
 * provider instance is shared between host and plugin code.
 *
 * Versioning: this APK's versionName is the upstream matrix-rust-sdk version and
 * it is only rebuilt/published when that upstream version changes (see
 * scripts/ci/e2ee_plugin_publish.sh and the scheduled GitLab job).
 */
object PluginEntry {
    private const val TAG = "MatrixE2eePlugin"

    @JvmStatic
    fun install(context: Context) {
        MatrixE2eePlatform.initialize()
        MatrixE2eeSenderProvider.install(MatrixE2eeRuntime)
        MatrixE2eeVerificationProvider.install(MatrixE2eeVerificationRuntime)
        Log.i(TAG, "Matrix E2EE plugin installed (sdk=" + BuildConfig.VERSION_NAME + ")")
    }
}
