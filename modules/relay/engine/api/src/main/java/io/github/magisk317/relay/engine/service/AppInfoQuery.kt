package io.github.magisk317.relay.engine.service

import io.github.magisk317.relay.engine.model.AppInfoData

interface AppInfoQuery {
    suspend fun getByPackageName(packageName: String): AppInfoData?
}
