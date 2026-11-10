package io.github.magisk317.relay.feature.mode

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-local Listener_Connected value.
 *
 * The notification listener service calls [onConnected] and [onDisconnected]
 * from its system callbacks. The value is not read from system settings.
 */
class ListenerConnection(initiallyConnected: Boolean = false) {

    private val _connected = MutableStateFlow(initiallyConnected)

    /** Latest Listener_Connected value. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Marks the listener as connected. */
    fun onConnected() {
        _connected.value = true
    }

    /** Marks the listener as disconnected. */
    fun onDisconnected() {
        _connected.value = false
    }
}
