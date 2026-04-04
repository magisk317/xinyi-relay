package io.github.magisk317.relay.webui

import android.content.Context
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.data.secret.InternalSecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

data class WebUiConfigSnapshot(
    val enabled: Boolean,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val allowLanAccess: Boolean,
)

class WebUiConfigStore(context: Context) {
    private val appContext = context.applicationContext ?: context
    private val preferenceDataSource by lazy { RuntimeGraph.from(appContext).preferenceDataSource }

    fun observe(): Flow<WebUiConfigSnapshot> {
        return combine(
            preferenceDataSource.getBooleanFlow(PrefConst.KEY_WEBUI_ENABLE, true),
            preferenceDataSource.getBooleanFlow(PrefConst.KEY_WEBUI_LAN_ACCESS, false),
            preferenceDataSource.getStringFlow(PrefConst.KEY_WEBUI_PORT, PrefConst.KEY_WEBUI_PORT_DEFAULT),
            preferenceDataSource.getStringFlow(PrefConst.KEY_WEBUI_USERNAME, PrefConst.KEY_WEBUI_USERNAME_DEFAULT),
            InternalSecretStore.observeString(appContext, PrefConst.KEY_WEBUI_PASSWORD, ""),
        ) { webUiEnabled, allowLanAccess, portString, username, password ->
            snapshotFrom(
                enabled = webUiEnabled,
                allowLanAccess = allowLanAccess,
                portString = portString,
                username = username,
                password = password,
            )
        }.distinctUntilChanged()
    }

    suspend fun loadSnapshot(): WebUiConfigSnapshot {
        return snapshotFrom(
            enabled = preferenceDataSource.getBoolean(PrefConst.KEY_WEBUI_ENABLE, true),
            allowLanAccess = preferenceDataSource.getBoolean(PrefConst.KEY_WEBUI_LAN_ACCESS, false),
            portString = preferenceDataSource.getString(PrefConst.KEY_WEBUI_PORT, PrefConst.KEY_WEBUI_PORT_DEFAULT),
            username = preferenceDataSource.getString(
                PrefConst.KEY_WEBUI_USERNAME,
                PrefConst.KEY_WEBUI_USERNAME_DEFAULT,
            ),
            password = InternalSecretStore.getOrMigrateString(
                context = appContext,
                key = PrefConst.KEY_WEBUI_PASSWORD,
                defaultValue = "",
                legacyValueProvider = { preferenceDataSource.getString(PrefConst.KEY_WEBUI_PASSWORD, "") },
                legacyValueCleaner = { preferenceDataSource.setString(PrefConst.KEY_WEBUI_PASSWORD, "") },
            ),
        )
    }

    suspend fun ensureInitialized() {
        val webUiEnabled = preferenceDataSource.getBoolean(PrefConst.KEY_WEBUI_ENABLE, true)
        preferenceDataSource.setBoolean(PrefConst.KEY_WEBUI_ENABLE, webUiEnabled)
        val port = preferenceDataSource.getString(PrefConst.KEY_WEBUI_PORT, "")
        if (port.isBlank()) {
            preferenceDataSource.setString(PrefConst.KEY_WEBUI_PORT, PrefConst.KEY_WEBUI_PORT_DEFAULT)
        }
        val username = preferenceDataSource.getString(PrefConst.KEY_WEBUI_USERNAME, "")
        if (username.isBlank() || username == "xsmscode") {
            preferenceDataSource.setString(PrefConst.KEY_WEBUI_USERNAME, PrefConst.KEY_WEBUI_USERNAME_DEFAULT)
        }
        val password = InternalSecretStore.getOrMigrateString(
            context = appContext,
            key = PrefConst.KEY_WEBUI_PASSWORD,
            defaultValue = "",
            legacyValueProvider = { preferenceDataSource.getString(PrefConst.KEY_WEBUI_PASSWORD, "") },
            legacyValueCleaner = { preferenceDataSource.setString(PrefConst.KEY_WEBUI_PASSWORD, "") },
        )
        if (password.isBlank()) {
            InternalSecretStore.putString(
                appContext,
                PrefConst.KEY_WEBUI_PASSWORD,
                WebUiTlsManager.generateRandomCredential(8),
            )
            preferenceDataSource.setString(PrefConst.KEY_WEBUI_PASSWORD, "")
        }
    }

    private fun snapshotFrom(
        enabled: Boolean,
        allowLanAccess: Boolean,
        portString: String,
        username: String,
        password: String,
    ): WebUiConfigSnapshot {
        val port = portString.toIntOrNull()
            ?.takeIf { it in 1..65535 }
            ?: PrefConst.KEY_WEBUI_PORT_DEFAULT.toInt()
        return WebUiConfigSnapshot(
            enabled = enabled,
            host = if (allowLanAccess) "0.0.0.0" else "127.0.0.1",
            port = port,
            username = username.ifBlank { PrefConst.KEY_WEBUI_USERNAME_DEFAULT },
            password = password,
            allowLanAccess = allowLanAccess,
        )
    }
}
