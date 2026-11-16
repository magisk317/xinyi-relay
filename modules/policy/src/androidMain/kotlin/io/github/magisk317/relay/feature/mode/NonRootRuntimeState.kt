package io.github.magisk317.relay.feature.mode

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local listener connection; authorization in settings is not a running signal. */
object NonRootRuntimeState {
    private val connection = ListenerConnection()
    private var enabledInBuild = false
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()
    val listenerConnected: StateFlow<Boolean> = connection.connected

    @Synchronized
    fun configure(enabledInThisBuild: Boolean) {
        enabledInBuild = enabledInThisBuild
        refresh()
    }

    @Synchronized
    fun onListenerConnected() {
        connection.onConnected()
        refresh()
    }

    @Synchronized
    fun onListenerDisconnected() {
        connection.onDisconnected()
        refresh()
    }

    @Synchronized
    fun refresh(): Boolean {
        val running = NonRootRuntimeRule.isRunning(enabledInBuild, connection.connected.value)
        _active.value = running
        return running
    }

    /** Setup-only authorization check; never mutates runtime state. */
    fun isListenerEnabledInSettings(context: Context, component: String): Boolean {
        val enabledServices = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        if (enabledServices.isBlank() || component.isBlank()) return false
        return enabledServices.split(':').any { it.equals(component, ignoreCase = true) }
    }
}
