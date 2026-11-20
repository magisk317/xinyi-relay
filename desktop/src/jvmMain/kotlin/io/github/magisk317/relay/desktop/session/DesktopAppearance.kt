package io.github.magisk317.relay.desktop.session

import kotlinx.serialization.Serializable

@Serializable
enum class DesktopAppearance {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }
}
