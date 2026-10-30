package io.github.magisk317.relay.android.platform.sender

import android.content.Context
import io.github.magisk317.forward.sender.E2eePluginRepository
import io.github.magisk317.forward.sender.MatrixLoginIdentity
import io.github.magisk317.forward.sender.ForwardSenderHostConfig
import io.github.magisk317.forward.sender.OAuthCredentialNamespace
import io.github.magisk317.forward.sender.SenderRuntimeInstaller

/**
 * Wires relay/sender runtime services into relay/engine:api contracts.
 */
object SenderRuntimeBridge {
    /** xinyi-relay owns these values; the shared sender runtime must not. */
    private val HOST_CONFIG = ForwardSenderHostConfig(
        oauthCredentials = OAuthCredentialNamespace(
            prefsFile = "xinyi_oauth2_credentials",
            keyAlias = "io.github.magisk317.xinyi.relay.email_oauth2.v1",
        ),
        e2eePluginRepository = E2eePluginRepository(
            baseUrl = "https://gitlab.com/api/v4/projects/84113188/packages/generic/xinyi-e2ee-plugin",
            fileNamePrefix = "xinyi-e2ee-plugin",
        ),
        matrixLogin = MatrixLoginIdentity(
            deviceId = "XINYI_RELAY",
            displayName = "XinyiRelay",
        ),
    )

    fun install(context: Context? = null) {
        SenderRuntimeInstaller.installMatrixLogin(HOST_CONFIG)
        if (context != null) {
            SenderRuntimeInstaller.installEmailOAuth(context, HOST_CONFIG)
        }
        SenderRuntimeInstaller.install()
        if (context != null) {
            SenderRuntimeInstaller.initE2eeAvailability(context, HOST_CONFIG)
        }
    }
}
