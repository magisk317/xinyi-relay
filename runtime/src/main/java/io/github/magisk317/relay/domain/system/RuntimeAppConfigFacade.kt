package io.github.magisk317.relay.domain.system

import android.content.Context
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.data.db.entity.AppInfo
import io.github.magisk317.relay.data.repository.ConfigRepository
import io.github.magisk317.relay.data.store.EntityStoreManager
import io.github.magisk317.relay.data.store.EntityType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runtime-side app config reads for hook / receiver entrypoints.
 */
class RuntimeAppConfigFacade(
    context: Context,
    private val configRepository: ConfigRepository = RuntimeGraph.from(context).configRepository,
    private val appConfigFallbackLoader: () -> List<AppInfo> = {
        EntityStoreManager.loadEntitiesFromFile(context, EntityType.APP_CONFIG, AppInfo::class.java)
    },
) {
    suspend fun isPackageBlocked(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val dbResult = runCatching { configRepository.getAppInfoByPackage(packageName) }
        dbResult.getOrNull()?.let { appInfo -> return@withContext appInfo.blocked }
        if (dbResult.isSuccess) {
            return@withContext false
        }

        appConfigFallbackLoader().any { it.packageName == packageName && it.blocked }
    }
}
