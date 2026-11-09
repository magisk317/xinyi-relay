package io.github.magisk317.relay.feature.mode

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks the GitHub-only non-root runtime: an enabled notification listener is
 * enough for notification capture even when Xposed is absent.
 *
 * The build flag stays with the caller so this module does not need a
 * distribution-flavor BuildConfig of its own.
 */
object NonRootRuntimeState {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun refresh(
        context: Context,
        enabledInThisBuild: Boolean,
        notificationListenerComponent: String,
    ): Boolean {
        val enabled = enabledInThisBuild && isListenerEnabled(context, notificationListenerComponent)
        _active.value = enabled
        return enabled
    }

    private fun isListenerEnabled(context: Context, component: String): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        if (enabledServices.isBlank()) return false
        return enabledServices.split(':').any { it.equals(component, ignoreCase = true) }
    }
}
