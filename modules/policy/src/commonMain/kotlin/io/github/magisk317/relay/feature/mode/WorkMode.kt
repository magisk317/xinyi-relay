package io.github.magisk317.relay.feature.mode

enum class WorkMode {
    Enhanced,   // Xposed hooks active
    Standard,   // Legacy: system-API-only channel, no longer resolved
    Inactive    // No active runtime (module not activated)
}
