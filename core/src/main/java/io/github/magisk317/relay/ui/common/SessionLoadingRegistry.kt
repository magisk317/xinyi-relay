package io.github.magisk317.relay.ui.common

import java.util.concurrent.ConcurrentHashMap

object SessionLoadingRegistry {
    private val shownKeys = ConcurrentHashMap.newKeySet<String>()

    fun shouldShowInitial(key: String): Boolean = !shownKeys.contains(key)

    fun markShown(key: String) {
        shownKeys.add(key)
    }
}
