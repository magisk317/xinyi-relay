package io.github.magisk317.relay.ui.home.appconfig

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.relay.engine.model.ForwardFilterRule
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.engine.filter.ForwardFilterConst
import io.github.magisk317.relay.sender.SenderSettingSanitizer
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.data.db.entity.NotifyRouteRule
import io.github.magisk317.relay.android.data.db.entity.AppInfo
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import io.github.magisk317.relay.android.data.mapper.ConfigMapper.toEntity
import io.github.magisk317.relay.engine.service.AppConfigRepository
import io.github.magisk317.relay.engine.service.MessageRecordRepository
import io.github.magisk317.relay.engine.routing.NotifyRouteScope
import io.github.magisk317.relay.android.data.store.EntityStoreManager
import io.github.magisk317.relay.android.data.store.EntityType
import io.github.magisk317.relay.ui.block.AppInfoHelper
import io.github.magisk317.relay.ui.common.AppIconCache
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val APP_NOTIFY_LOG_LIMIT = 20

@Immutable
data class AppConfigUiState(
    val apps: ImmutableList<AppInfo> = persistentListOf(),
    val isLoading: Boolean = false,
    val hasMoreApps: Boolean = false,
    val hideSystemApps: Boolean = true,
    val sortOption: AppConfigViewModel.SortOption = AppConfigViewModel.SortOption.LABEL,
    val isAscending: Boolean = true,
    val searchQuery: String = "",
    val appNotifyBindingCount: Map<String, Int> = emptyMap(),
    val appIcons: Map<String, Bitmap> = emptyMap(),
)

class AppConfigViewModel(
    application: Application,
    private val configRepository: AppConfigRepository,
    private val recordRepository: MessageRecordRepository,
) : AndroidViewModel(application) {

    private val _queryState = MutableStateFlow(AppConfigQueryState())
    private val _appIcons = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    private val loadingIconPackages = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val _events = MutableSharedFlow<AppConfigEvent>()
    val events: SharedFlow<AppConfigEvent> = _events.asSharedFlow()
    val notifySenderListFlow: StateFlow<List<Sender>> = configRepository.getAllSendersFlow()
        .map { list -> list.map(SenderSettingSanitizer::sanitizeSenderLenient) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )
    val appNotifyBindingCountFlow: StateFlow<Map<String, Int>> = configRepository.getAllNotifyRouteRulesFlow()
        .map { rules ->
            rules.asSequence()
                .filter { it.scope == NotifyRouteScope.APP_ALLOW_SENDER }
                .groupingBy { it.packageName }
                .eachCount()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap(),
        )
    val uiState: StateFlow<AppConfigUiState> = _queryState
        .combine(appNotifyBindingCountFlow) { queryState, bindingCount ->
            queryState to bindingCount
        }
        .combine(_appIcons) { queryAndBindings, appIcons ->
            queryAndBindings to appIcons
        }
        .map { (queryAndBindings, appIcons) ->
            val (queryState, bindingCount) = queryAndBindings
            withContext(Dispatchers.Default) {
                val assembly = assembleAppConfigList(queryState)
                AppConfigUiState(
                    apps = assembly.visibleApps,
                    isLoading = queryState.isLoading,
                    hasMoreApps = assembly.hasMoreApps,
                    hideSystemApps = queryState.hideSystemApps,
                    sortOption = queryState.sortOption,
                    isAscending = queryState.isAscending,
                    searchQuery = queryState.searchQuery,
                    appNotifyBindingCount = bindingCount,
                    appIcons = appIcons,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppConfigUiState(),
        )

    @Immutable
    sealed class AppConfigEvent {
        data class Error(val throwable: Throwable) : AppConfigEvent()
        object ShowUsageStatsPermission : AppConfigEvent()
    }

    private val persistMutex = Mutex()

    enum class SortOption {
        LABEL,
        PACKAGE,
        USAGE,
        SELECTION,
    }

    fun refreshData(force: Boolean = false) {
        if (_queryState.value.hasLoaded && !force) {
            _queryState.update {
                it.copy(
                    visibleCount = visibleAppCountAfterFilter(
                        previousVisibleCount = it.visibleCount,
                        resetVisibleWindow = true,
                    ),
                )
            }
            return
        }

        viewModelScope.launch {
            _queryState.update { it.copy(isLoading = true) }
            try {
                val usageStatsByPackage = loadUsageStats()
                val appCatalog = loadInstalledAppCatalog()

                _queryState.update { current ->
                    current.copy(
                        sourceApps = appCatalog.apps,
                        systemPackages = appCatalog.systemPackages,
                        usageStatsByPackage = usageStatsByPackage,
                        visibleCount = visibleAppCountAfterFilter(
                            previousVisibleCount = current.visibleCount,
                            resetVisibleWindow = true,
                        ),
                        hasLoaded = true,
                        isLoading = false,
                    )
                }
            } catch (t: Throwable) {
                XLog.e("Unified AppConfig load failed", t)
                _queryState.update { it.copy(isLoading = false) }
                _events.emit(AppConfigEvent.Error(t))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun loadUsageStats(): Map<String, Long> = withContext(Dispatchers.IO) {
        runCatching {
            if (!hasUsageStatsPermission()) {
                return@withContext emptyMap()
            }
            val context = getApplication<Application>()
            val usageStatsManager = context.getSystemService(android.app.usage.UsageStatsManager::class.java)
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 1000 * 3600 * 24 * 30L // Last 30 days
            val stats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
            if (stats == null) {
                emptyMap()
            } else {
                stats.mapValues { (_, usage) -> usage.totalTimeInForeground }
            }
        }.getOrElse { ignored ->
            XLog.e("Failed to load usage stats", ignored)
            emptyMap()
        }
    }

    fun doFilter(newFilter: String) {
        _queryState.update {
            it.copy(
                searchQuery = newFilter,
                visibleCount = visibleAppCountAfterFilter(
                    previousVisibleCount = it.visibleCount,
                    resetVisibleWindow = true,
                ),
            )
        }
    }

    fun setSortOption(option: SortOption) {
        if (option == SortOption.USAGE) {
            if (!hasUsageStatsPermission()) {
                viewModelScope.launch { _events.emit(AppConfigEvent.ShowUsageStatsPermission) }
                return
            }
        }

        if (_queryState.value.sortOption != option) {
            _queryState.update {
                it.copy(
                    sortOption = option,
                    isAscending = option != SortOption.USAGE && option != SortOption.SELECTION,
                    visibleCount = visibleAppCountAfterFilter(
                        previousVisibleCount = it.visibleCount,
                        resetVisibleWindow = true,
                    ),
                )
            }
        }
    }

    fun setAscending(ascending: Boolean) {
        _queryState.update {
            it.copy(
                isAscending = ascending,
                visibleCount = visibleAppCountAfterFilter(
                    previousVisibleCount = it.visibleCount,
                    resetVisibleWindow = true,
                ),
            )
        }
    }

    fun setHideSystemApps(hide: Boolean) {
        _queryState.update {
            it.copy(
                hideSystemApps = hide,
                visibleCount = visibleAppCountAfterFilter(
                    previousVisibleCount = it.visibleCount,
                    resetVisibleWindow = true,
                ),
            )
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getApplication<Application>().getSystemService(
            android.content.Context.APP_OPS_SERVICE,
        ) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            getApplication<Application>().packageName,
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun loadMoreApps() {
        if (_queryState.value.isLoading || !uiState.value.hasMoreApps) return
        _queryState.update {
            it.copy(visibleCount = visibleAppCountAfterLoadMore(it.visibleCount))
        }
    }

    fun preloadAppIcons(packageNames: Collection<String>, sizePx: Int) {
        val normalizedPackages = packageNames
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .filter { packageName ->
                !_appIcons.value.containsKey(packageName) && loadingIconPackages.add(packageName)
            }
            .toList()
        if (normalizedPackages.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val loaded = mutableMapOf<String, Bitmap>()
            for (packageName in normalizedPackages) {
                try {
                    val bitmap = AppIconCache.load(
                        context = context,
                        packageName = packageName,
                        sizePx = sizePx,
                    )
                    if (bitmap != null) {
                        loaded[packageName] = bitmap
                    }
                } finally {
                    loadingIconPackages.remove(packageName)
                }
            }
            if (loaded.isNotEmpty()) {
                _appIcons.update { current -> current + loaded }
            }
        }
    }

    fun setBlocked(item: AppInfo, blocked: Boolean) {
        setBlocked(item.packageName, blocked)
    }

    fun setForwarding(item: AppInfo, forwarding: Boolean) {
        setForwarding(item.packageName, forwarding)
    }

    fun setBlocked(packageName: String, blocked: Boolean) {
        updateApp(packageName) { it.copy(blocked = blocked) }
    }

    fun setForwarding(packageName: String, forwarding: Boolean) {
        updateApp(packageName) { it.copy(forwarding = forwarding, forwardingConfigured = true) }
    }

    fun setNotifyTemplate(packageName: String, notifyTemplate: String) {
        updateApp(packageName) { it.copy(notifyTemplate = notifyTemplate) }
    }

    fun getAppByPackageName(packageName: String): AppInfo? {
        return _queryState.value.sourceApps.firstOrNull { it.packageName == packageName }
    }

    fun appNotifyLogsFlow(packageName: String): kotlinx.coroutines.flow.Flow<List<SmsMsg>> {
        @Suppress("UNCHECKED_CAST")
        return recordRepository.observeLogsForPackage(packageName, APP_NOTIFY_LOG_LIMIT) as kotlinx.coroutines.flow.Flow<List<SmsMsg>>
    }

    fun appNotifyBoundSenderIdsFlow(packageName: String): kotlinx.coroutines.flow.Flow<Set<Long>> {
        return configRepository.observeNotifySenderIds(
            scope = NotifyRouteScope.APP_ALLOW_SENDER,
            packageName = packageName,
        )
    }

    fun senderDenyingPackageIdsFlow(packageName: String): kotlinx.coroutines.flow.Flow<Set<Long>> {
        return configRepository.observeNotifySenderIds(
            scope = NotifyRouteScope.SENDER_DENY_APP,
            packageName = packageName,
        )
    }

    fun saveAppNotifySenderBindings(packageName: String, senderIds: Set<Long>) {
        val normalizedPackageName = packageName.trim()
        if (normalizedPackageName.isBlank()) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            persistMutex.withLock {
                configRepository.deleteNotifyRouteByScopeAndPackage(
                    scope = NotifyRouteScope.APP_ALLOW_SENDER,
                    packageName = normalizedPackageName,
                )
                if (senderIds.isNotEmpty()) {
                    val updateTime = System.currentTimeMillis()
                    configRepository.insertNotifyRouteRules(
                        senderIds.map { senderId ->
                            NotifyRouteRule(
                                scope = NotifyRouteScope.APP_ALLOW_SENDER,
                                packageName = normalizedPackageName,
                                senderId = senderId,
                                updateTime = updateTime,
                            )
                        },
                    )
                }
            }
        }
    }

    fun getAppNotifyBindingCount(packageName: String): Int {
        return appNotifyBindingCountFlow.value[packageName] ?: 0
    }

    fun globalForwardRulesFlow(msgType: String): Flow<List<ForwardFilterRule>> {
        return configRepository.observeForwardFiltersByScope(
            msgType = msgType,
            scopeType = ForwardFilterConst.SCOPE_GLOBAL,
        )
    }

    fun appPackageForwardRulesFlow(packageName: String): Flow<List<ForwardFilterRule>> {
        return configRepository.observeForwardFiltersByScope(
            msgType = ForwardFilterConst.MSG_TYPE_APP_NOTIFY,
            scopeType = ForwardFilterConst.SCOPE_PACKAGE,
            scopeKey = packageName.trim(),
        )
    }

    fun appChannelForwardRulesFlow(packageName: String): Flow<List<ForwardFilterRule>> {
        val prefix = "${packageName.trim()}::%"
        return configRepository.observeForwardFiltersByScopePrefix(
            msgType = ForwardFilterConst.MSG_TYPE_APP_NOTIFY,
            scopeType = ForwardFilterConst.SCOPE_ANDROID_CHANNEL,
            scopeKeyPrefix = prefix,
        )
    }

    fun appNotifyChannelHistoryFlow(packageName: String, limit: Int = 20): Flow<List<String>> {
        return recordRepository.observeRecentNotifyChannelIds(
            packageName = packageName.trim(),
            limit = limit,
        )
    }

    fun saveForwardFilterRule(rule: ForwardFilterRule) {
        viewModelScope.launch(Dispatchers.IO) {
            val updateTime = System.currentTimeMillis()
            val normalized = rule.copy(
                updateTime = updateTime,
                scopeKey = rule.scopeKey.trim(),
                pattern = rule.pattern.trim(),
            )
            if (normalized.id <= 0L) {
                configRepository.insertForwardFilterRule(normalized.copy(id = 0L))
            } else {
                configRepository.updateForwardFilterRule(normalized)
            }
        }
    }

    fun deleteForwardFilterRule(id: Long) {
        if (id <= 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            configRepository.deleteForwardFilterRuleById(id)
        }
    }

    fun setForwardFilterRuleEnabled(id: Long, enabled: Boolean) {
        if (id <= 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            configRepository.updateForwardFilterEnabled(
                id = id,
                enabled = if (enabled) 1 else 0,
                updateTime = System.currentTimeMillis(),
            )
        }
    }

    private fun updateApp(packageName: String, updater: (AppInfo) -> AppInfo) {
        _queryState.update { state ->
            state.copy(
                sourceApps = state.sourceApps.map { app ->
                    if (app.packageName == packageName) updater(app) else app
                }.toImmutableList(),
                visibleCount = visibleAppCountAfterFilter(
                    previousVisibleCount = state.visibleCount,
                    resetVisibleWindow = false,
                ),
            )
        }
        persistAppConfig(packageName)
    }

    private fun persistAppConfig(packageName: String) {
        val latestApps = _queryState.value.sourceApps
        val target = latestApps.firstOrNull { it.packageName == packageName }
        val changedConfigs = latestApps.filter(::appInfoHasEffectiveConfig)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    persistMutex.withLock {
                        if (target != null) {
                            if (appInfoHasEffectiveConfig(target)) {
                                configRepository.upsertAppInfo(target)
                            } else {
                                configRepository.removeAppInfosByPackage(listOf(target.packageName))
                            }
                        }
                        EntityStoreManager.storeEntitiesToFile(
                            getApplication(),
                            EntityType.APP_CONFIG,
                            changedConfigs,
                            AppInfo::class.java,
                        )
                    }
                }
            } catch (t: Throwable) {
                XLog.e("Failed to persist app config: $packageName", t)
                _events.emit(AppConfigEvent.Error(t))
            }
        }
    }

    private suspend fun loadInstalledAppCatalog(): LoadedAppCatalog = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val pm = getApplication<Application>().packageManager
        val configs = configRepository.getAllAppInfo().map { it.toEntity() }
        EntityStoreManager.storeEntitiesToFile(
            context,
            EntityType.APP_CONFIG,
            configs.filter(::appInfoHasEffectiveConfig),
            AppInfo::class.java,
        )

        val configMap = configs.associateBy { it.packageName }
        val systemPackages = linkedSetOf<String>()
        val apps = pm.getInstalledApplications(PackageManager.MATCH_ALL)
            .asSequence()
            .map { applicationInfo ->
                val appInfoBase = AppInfoHelper.getAppInfo(pm, applicationInfo)
                val isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                if (isSystemApp) {
                    systemPackages.add(appInfoBase.packageName)
                }

                val config = configMap[appInfoBase.packageName]
                if (config != null) {
                    appInfoBase.copy(
                        blocked = config.blocked,
                        forwarding = config.forwarding,
                        forwardingConfigured = config.forwardingConfigured,
                        notifyTemplate = config.notifyTemplate,
                    )
                } else {
                    appInfoBase
                }
            }
            .toImmutableList()
        LoadedAppCatalog(
            apps = apps,
            systemPackages = systemPackages,
        )
    }
}

private data class LoadedAppCatalog(
    val apps: ImmutableList<AppInfo>,
    val systemPackages: Set<String>,
)
