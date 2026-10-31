package io.github.magisk317.relay.data.appconfig

import android.content.Context
import io.github.magisk317.forward.engine.model.AppInfoData
import io.github.magisk317.forward.engine.service.AppConfigRepository
import io.github.magisk317.relay.android.data.db.legacy.entity.LegacyAppInfo as AppInfo
import io.github.magisk317.relay.android.data.mapper.ConfigMapper.toEntity
import io.github.magisk317.relay.android.data.store.EntityStoreManager
import io.github.magisk317.relay.android.data.store.EntityType

/**
 * An app config only earns a hook-side entry when one of the effective flags is set;
 * anything else stays a purely local UI override.
 */
fun appInfoHasEffectiveConfig(appInfo: AppInfoData): Boolean =
    appInfo.blocked || appInfo.forwardingConfigured || appInfo.notifyTemplate.isNotBlank()

/**
 * Repository-side owner of the hook-side app-config entity mirror.
 *
 * The legacy entity file is a platform-adapter artefact (`relay:android`): feature/UI code must
 * only see [AppInfoData], so the domain -> entity mapping and the file write both live here.
 * The app-config persistence flow serializes its repository reads, mirror writes and
 * notify-route writes behind one mutex and calls into this facade while holding it.
 */
class AppConfigEntityMirror(
    context: Context,
    private val configRepository: AppConfigRepository,
) {
    private val appContext = context.applicationContext

    /**
     * Refreshes the hook-side entity file from the committed repository snapshot and returns
     * the persisted configs as domain data.
     */
    suspend fun syncEntityMirror(): List<AppInfoData> {
        val latest = configRepository.getAllAppInfo()
        writeMirror(latest)
        return latest
    }

    /** Persists one app config (or clears it when it has no effective config) and re-mirrors. */
    suspend fun upsertAndSync(appInfo: AppInfoData) {
        if (appInfoHasEffectiveConfig(appInfo)) {
            configRepository.upsertAppInfo(appInfo)
        } else {
            configRepository.removeAppInfosByPackage(listOf(appInfo.packageName))
        }
        writeMirror(configRepository.getAllAppInfo())
    }

    private fun writeMirror(latest: List<AppInfoData>) {
        EntityStoreManager.storeEntitiesToFile(
            appContext,
            EntityType.APP_CONFIG,
            latest.filter(::appInfoHasEffectiveConfig).map { it.toEntity() },
            AppInfo::class.java,
        )
    }
}
