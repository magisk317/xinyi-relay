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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
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
    private val refreshGeneration = AppConfigRequestGeneration()
    private val localMutations = AppConfigMutationTracker()
    private val iconPreloadMutex = Mutex()
    private val pendingPersistJobs = java.util.concurrent.ConcurrentHashMap.newKeySet<Job>()

    private val eventQueue = Channel<AppConfigEvent>(capacity = Channel.UNLIMITED)
    val events: Flow<AppConfigEvent> = eventQueue.receiveAsFlow()
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
            started = SharingStarted.WhileSubscribed(),
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
            started = SharingStarted.WhileSubscribed(),
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

    suspend fun refreshData(force: Boolean = false) {
        val generation = refreshGeneration.next()
        val mutationGeneration = localMutations.currentGeneration()
        if (_queryState.value.hasLoaded && !force) {
            try {
                awaitPendingPersistence()
                val configs = withContext(Dispatchers.IO) {
                    persistMutex.withLock {
                        configRepository.getAllAppInfo().map { it.toEntity() }
                    }
                }
                currentCoroutineContext().ensureActive()
                if (
                    refreshGeneration.isCurrent(generation) &&
                    localMutations.currentGeneration() == mutationGeneration
                ) {
                    _queryState.update { current ->
                        current.copy(
                            sourceApps = applyPersistedAppConfigs(current.sourceApps, configs),
                            isLoading = false,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                if (refreshGeneration.invalidate(generation)) {
                    _queryState.update { current ->
                        if (current.isLoading) current.copy(isLoading = false) else current
                    }
                }
                throw cancelled
            } catch (t: Throwable) {
                if (refreshGeneration.isCurrent(generation)) {
                    XLog.e("Unified AppConfig config refresh failed", t)
                    _queryState.update { current ->
                        if (current.isLoading) current.copy(isLoading = false) else current
                    }
                    eventQueue.send(AppConfigEvent.Error(t))
                }
            } finally {
                if (refreshGeneration.isCurrent(generation)) {
                    _queryState.update { current ->
                        if (current.isLoading) current.copy(isLoading = false) else current
                    }
                }
            }
            return
        }

        _queryState.update { it.copy(isLoading = true) }
        try {
            awaitPendingPersistence()
            val (usageStatsByPackage, appCatalog) = coroutineScope {
                val usageStats = async { loadUsageStats() }
                val installedApps = async { loadInstalledAppCatalog() }
                usageStats.await() to installedApps.await()
            }

            if (refreshGeneration.isCurrent(generation)) {
                _queryState.update { current ->
                    val sourceApps = if (localMutations.currentGeneration() == mutationGeneration) {
                        appCatalog.apps
                    } else {
                        preserveCurrentAppConfigs(appCatalog.apps, current.sourceApps)
                    }
                    current.copy(
                        sourceApps = sourceApps,
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
            }
        } catch (cancelled: CancellationException) {
            if (refreshGeneration.invalidate(generation)) {
                _queryState.update { current ->
                    if (current.isLoading) current.copy(isLoading = false) else current
                }
            }
            throw cancelled
        } catch (t: Throwable) {
            if (refreshGeneration.isCurrent(generation)) {
                XLog.e("Unified AppConfig load failed", t)
                _queryState.update { it.copy(isLoading = false) }
                eventQueue.send(AppConfigEvent.Error(t))
            }
        } finally {
            if (refreshGeneration.isCurrent(generation)) {
                _queryState.update { current ->
                    if (current.isLoading) current.copy(isLoading = false) else current
                }
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
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            XLog.e("Failed to load usage stats", throwable)
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
                viewModelScope.launch { eventQueue.send(AppConfigEvent.ShowUsageStatsPermission) }
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

    suspend fun preloadAppIcons(packageNames: Collection<String>, sizePx: Int) {
        iconPreloadMutex.withLock {
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

            try {
                val loaded = withContext(Dispatchers.IO) {
                    val context = getApplication<Application>().applicationContext
                    buildMap {
                        for (packageName in normalizedPackages) {
                            currentCoroutineContext().ensureActive()
                            val bitmap = AppIconCache.load(
                                context = context,
                                packageName = packageName,
                                sizePx = sizePx,
                            )
                            if (bitmap != null) {
                                put(packageName, bitmap)
                            }
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                if (loaded.isNotEmpty()) {
                    _appIcons.update { current -> current + loaded }
                }
            } finally {
                normalizedPackages.forEach(loadingIconPackages::remove)
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
        val mutation = localMutations.record(packageName)
        var updatedTarget: AppInfo? = null
        _queryState.update { state ->
            updatedTarget = null
            val updatedApps = state.sourceApps.map { app ->
                if (app.packageName == packageName) {
                    updater(app).also { updatedTarget = it }
                } else {
                    app
                }
            }.toImmutableList()
            state.copy(
                sourceApps = updatedApps,
                visibleCount = visibleAppCountAfterFilter(
                    previousVisibleCount = state.visibleCount,
                    resetVisibleWindow = false,
                ),
            )
        }
        updatedTarget?.let { persistAppConfig(packageName, it, mutation) }
    }

    private fun persistAppConfig(packageName: String, target: AppInfo, mutation: Long) {
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                withContext(Dispatchers.IO) {
                    persistMutex.withLock {
                        if (!localMutations.isLatest(packageName, mutation)) return@withLock
                        // The package sequence makes stale writers no-ops. Build the shared mirror
                        // from the committed repository snapshot so another package's optimistic
                        // in-memory edit cannot leak into the hook file after a failed write.
                        if (appInfoHasEffectiveConfig(target)) {
                            configRepository.upsertAppInfo(target)
                        } else {
                            configRepository.removeAppInfosByPackage(listOf(target.packageName))
                        }
                        val changedConfigs = configRepository.getAllAppInfo()
                            .map { it.toEntity() }
                            .filter(::appInfoHasEffectiveConfig)
                        EntityStoreManager.storeEntitiesToFile(
                            getApplication(),
                            EntityType.APP_CONFIG,
                            changedConfigs,
                            AppInfo::class.java,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                XLog.e("Failed to persist app config: $packageName", t)
                eventQueue.send(AppConfigEvent.Error(t))
            }
        }
        pendingPersistJobs += job
        job.invokeOnCompletion { pendingPersistJobs -= job }
        job.start()
    }

    private suspend fun awaitPendingPersistence() {
        while (true) {
            val pending = pendingPersistJobs.toList()
            if (pending.isEmpty()) return
            pending.forEach { it.join() }
        }
    }

    private suspend fun loadInstalledAppCatalog(): LoadedAppCatalog = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val pm = getApplication<Application>().packageManager
        val configs = persistMutex.withLock {
            val latestConfigs = configRepository.getAllAppInfo().map { it.toEntity() }
            currentCoroutineContext().ensureActive()
            // The entity mirror and interactive writes share one writer boundary. Whichever
            // operation acquires the mutex last always publishes the newest repository snapshot.
            EntityStoreManager.storeEntitiesToFile(
                context,
                EntityType.APP_CONFIG,
                latestConfigs.filter(::appInfoHasEffectiveConfig),
                AppInfo::class.java,
            )
            latestConfigs
        }

        val configMap = configs.associateBy { it.packageName }
        val systemPackages = linkedSetOf<String>()
        val scanJob = currentCoroutineContext()[Job]
        val apps = pm.getInstalledApplications(PackageManager.MATCH_ALL)
            .asSequence()
            .map { applicationInfo ->
                scanJob?.ensureActive()
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

private fun applyPersistedAppConfigs(
    apps: ImmutableList<AppInfo>,
    configs: List<AppInfo>,
): ImmutableList<AppInfo> {
    val configByPackage = configs.associateBy(AppInfo::packageName)
    return apps.map { app -> app.withAppConfig(configByPackage[app.packageName]) }.toImmutableList()
}

private fun preserveCurrentAppConfigs(
    refreshedApps: ImmutableList<AppInfo>,
    currentApps: ImmutableList<AppInfo>,
): ImmutableList<AppInfo> {
    val currentByPackage = currentApps.associateBy(AppInfo::packageName)
    return refreshedApps.map { app ->
        currentByPackage[app.packageName]?.let { current -> app.withAppConfig(current) } ?: app
    }.toImmutableList()
}

private fun AppInfo.withAppConfig(config: AppInfo?): AppInfo = copy(
    blocked = config?.blocked ?: false,
    forwarding = config?.forwarding ?: false,
    forwardingConfigured = config?.forwardingConfigured ?: false,
    notifyTemplate = config?.notifyTemplate.orEmpty(),
)

private data class LoadedAppCatalog(
    val apps: ImmutableList<AppInfo>,
    val systemPackages: Set<String>,
)
