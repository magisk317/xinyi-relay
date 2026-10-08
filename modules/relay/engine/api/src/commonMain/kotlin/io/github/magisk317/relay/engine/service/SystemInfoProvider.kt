package io.github.magisk317.relay.engine.service

import io.github.magisk317.relay.engine.model.SystemEnvironment

interface SystemInfoProvider {
    fun getSnapshot(deviceName: String): SystemEnvironment
    fun resolveAppName(packageName: String): String
}
