package io.github.magisk317.relay.common.feature

/**
 * Embedded WebUI is intentionally disabled while the new remote architecture
 * is being prepared. Keep the flag centralized so UI, settings, and runtime
 * behavior stay consistent.
 */
object WebUiFeatureGate {
    const val EMBEDDED_WEBUI_ENABLED = false
}
