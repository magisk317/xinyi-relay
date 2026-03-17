package io.github.magisk317.relay.domain.service

import io.github.magisk317.relay.domain.model.SystemEnvironment

interface SystemInfoProvider {
    fun getSnapshot(deviceName: String): SystemEnvironment
    fun resolveAppName(packageName: String): String
}
