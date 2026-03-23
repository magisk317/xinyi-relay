package io.github.magisk317.relay.ui.home

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.relay.domain.model.ForwardFilterRule
import io.github.magisk317.relay.domain.model.Sender
import io.github.magisk317.relay.domain.filter.ForwardFilterConst
import io.github.magisk317.relay.domain.sender.SenderSettingSanitizer
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.data.db.entity.NotifyRouteRule
import io.github.magisk317.relay.data.db.entity.AppInfo
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.data.repository.ConfigRepository
import io.github.magisk317.relay.data.repository.RelayRecordRepository
import io.github.magisk317.relay.domain.routing.NotifyRouteScope
import io.github.magisk317.relay.data.store.EntityStoreManager
import io.github.magisk317.relay.data.store.EntityType
import io.github.magisk317.relay.ui.block.AppInfoHelper
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Comparator

private const val APP_NOTIFY_LOG_LIMIT = 20
private const val APP_LIST_PAGE_SIZE = 80

class AppConfigViewModel(
    application: Application,
    private val configRepository: ConfigRepository,
    private val recordRepository: RelayRecordRepository,
) : AndroidViewModel(application) {

    private val _appsFlow = MutableStateFlow<ImmutableList<AppInfo>>(persistentListOf())
    val appsFlow: StateFlow<ImmutableList<AppInfo>> = _appsFlow.asStateFlow()

    private val _loadingFlow = MutableStateFlow(false)
    val loadingFlow: StateFlow<Boolean> = _loadingFlow.asStateFlow()
    private val _hasMoreAppsFlow = MutableStateFlow(false)
    val hasMoreAppsFlow: StateFlow<Boolean> = _hasMoreAppsFlow.asStateFlow()

    private val _hideSystemAppsFlow = MutableStateFlow(true)
    val hideSystemAppsFlow: StateFlow<Boolean> = _hideSystemAppsFlow.asStateFlow()

    private val _sortOptionFlow = MutableStateFlow(SortOption.LABEL)
    val sortOptionFlow: StateFlow<SortOption> = _sortOptionFlow.asStateFlow()

    private val _isAscendingFlow = MutableStateFlow(true)
    val isAscendingFlow: StateFlow<Boolean> = _isAscendingFlow.asStateFlow()

    private val _events = MutableSharedFlow<AppConfigEvent>()
    val events: SharedFlow<AppConfigEvent> = _events.asSharedFlow()
    private val _filterFlow = MutableStateFlow("")
    val filterFlow: StateFlow<String> = _filterFlow.asStateFlow()
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

    @Immutable
    sealed class AppConfigEvent {
        data class Error(val throwable: Throwable) : AppConfigEvent()
        object ShowUsageStatsPermission : AppConfigEvent()
    }

    private var apps: ImmutableList<AppInfo> = persistentListOf()
    private var filteredApps: ImmutableList<AppInfo> = persistentListOf()
    private var visibleAppCount = 0
    private var isLoadSucceed = false
    private val systemApps = HashSet<String>()
    private val persistMutex = Mutex()

    private var filter = ""
    private var currentSortOption = SortOption.LABEL
    private var isAscending = true

    enum class SortOption {
        LABEL,
        PACKAGE,
        USAGE,
        SELECTION,
    }

    private val usageStatsMap = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun refreshData(force: Boolean = false) {
        if (isLoadSucceed && !force) {
            applyFilterAndSort(resetVisibleWindow = true)
            return
        }

        viewModelScope.launch {
            _loadingFlow.value = true
            try {
                refreshUsageStats()

                val appList = withContext(Dispatchers.IO) {
                    val context = getApplication<Application>()
                    val pm = getApplication<Application>().packageManager
                    // Load all app infos from DB (both blocked and forwarding)
                    var configs = configRepository.getAllAppInfo()
                    EntityStoreManager.storeEntitiesToFile(
                        context,
                        EntityType.APP_CONFIG,
                        configs.filter(::hasEffectiveConfig),
                        AppInfo::class.java,
                    )

                    val installedApps = pm.getInstalledApplications(PackageManager.MATCH_ALL)
                    val configMap = configs.associateBy { it.packageName }

                    systemApps.clear()
                    installedApps.asSequence()
                        .map { app ->
                            val appInfoBase = AppInfoHelper.getAppInfo(pm, app)
                            val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                                (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                            if (isSystemApp) {
                                systemApps.add(appInfoBase.packageName)
                            }
                            
                            val config = configMap[appInfoBase.packageName]
                            if (config != null) {
                                appInfoBase.copy(
                                    blocked = config.blocked,
                                    forwarding = config.forwarding,
                                    notifyTemplate = config.notifyTemplate,
                                )
                            } else {
                                appInfoBase
                            }
                        }
                        .toImmutableList()
                }

                apps = appList
                isLoadSucceed = true
                applyFilterAndSort(resetVisibleWindow = true)
                _loadingFlow.value = false
            } catch (t: Throwable) {
                XLog.e("Unified AppConfig load failed", t)
                _loadingFlow.value = false
                viewModelScope.launch { _events.emit(AppConfigEvent.Error(t)) }
                isLoadSucceed = false
            }
        }
    }

    private fun refreshUsageStats() {
        try {
            val context = getApplication<Application>()
            val usageStatsManager = context.getSystemService(android.app.usage.UsageStatsManager::class.java)
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 1000 * 3600 * 24 * 30L // Last 30 days
            val stats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
            usageStatsMap.clear()
            if (stats != null) {
                for ((pkg, usage) in stats) {
                    usageStatsMap[pkg] = usage.totalTimeInForeground
                }
            }
        } catch (ignored: Exception) {
            XLog.e("Failed to load usage stats", ignored)
        }
    }

    fun doFilter(newFilter: String) {
        _filterFlow.value = newFilter
        filter = newFilter.lowercase()
        applyFilterAndSort(resetVisibleWindow = true)
    }

    fun setSortOption(option: SortOption) {
        if (option == SortOption.USAGE) {
            if (!hasUsageStatsPermission()) {
                viewModelScope.launch { _events.emit(AppConfigEvent.ShowUsageStatsPermission) }
                return
            }
        }

        if (currentSortOption != option) {
            currentSortOption = option
            isAscending = option != SortOption.USAGE && option != SortOption.SELECTION
            _sortOptionFlow.value = currentSortOption
            _isAscendingFlow.value = isAscending
        }
        applyFilterAndSort(resetVisibleWindow = true)
    }

    fun setAscending(ascending: Boolean) {
        isAscending = ascending
        _isAscendingFlow.value = isAscending
        applyFilterAndSort(resetVisibleWindow = true)
    }

    fun setHideSystemApps(hide: Boolean) {
        _hideSystemAppsFlow.value = hide
        applyFilterAndSort(resetVisibleWindow = true)
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
        if (_loadingFlow.value || !_hasMoreAppsFlow.value) return
        visibleAppCount = minOf(visibleAppCount + APP_LIST_PAGE_SIZE, filteredApps.size)
        publishVisibleApps()
    }

    private fun applyFilterAndSort(resetVisibleWindow: Boolean) {
        viewModelScope.launch {
            val filteredList = withContext(Dispatchers.Default) {
                apps.asSequence()
                    .filter { appInfo ->
                        if (_hideSystemAppsFlow.value && systemApps.contains(appInfo.packageName)) {
                            return@filter false
                        }
                        if (filter.isEmpty()) {
                            true
                        } else {
                            val lowerLabel = appInfo.label?.lowercase() ?: ""
                            val lowerPkg = appInfo.packageName.lowercase()
                            lowerLabel.contains(filter) || lowerPkg.contains(filter)
                        }
                    }
                    .sortedWith(mComparator)
                    .toImmutableList()
            }
            filteredApps = filteredList
            if (resetVisibleWindow || visibleAppCount <= 0) {
                visibleAppCount = minOf(APP_LIST_PAGE_SIZE, filteredApps.size)
            } else {
                visibleAppCount = minOf(visibleAppCount, filteredApps.size)
            }
            publishVisibleApps()
        }
    }

    private fun publishVisibleApps() {
        val endIndex = visibleAppCount.coerceAtMost(filteredApps.size)
        _appsFlow.value = filteredApps.subList(0, endIndex).toImmutableList()
        _hasMoreAppsFlow.value = endIndex < filteredApps.size
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
        updateApp(packageName) { it.copy(forwarding = forwarding) }
    }

    fun setNotifyTemplate(packageName: String, notifyTemplate: String) {
        updateApp(packageName) { it.copy(notifyTemplate = notifyTemplate) }
    }

    fun getAppByPackageName(packageName: String): AppInfo? {
        return apps.firstOrNull { it.packageName == packageName }
    }

    fun appNotifyLogsFlow(packageName: String): kotlinx.coroutines.flow.Flow<List<SmsMsg>> {
        return recordRepository.observeLogsForPackage(packageName, APP_NOTIFY_LOG_LIMIT)
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
        apps = apps.map { app ->
            if (app.packageName == packageName) updater(app) else app
        }.toImmutableList()
        applyFilterAndSort(resetVisibleWindow = false)
        persistAppConfig(packageName)
    }

    private fun persistAppConfig(packageName: String) {
        val latestApps = apps
        val target = latestApps.firstOrNull { it.packageName == packageName }
        val changedConfigs = latestApps.filter(::hasEffectiveConfig)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    persistMutex.withLock {
                        if (target != null) {
                            if (hasEffectiveConfig(target)) {
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

    private fun hasEffectiveConfig(appInfo: AppInfo): Boolean {
        return appInfo.blocked || appInfo.forwarding || appInfo.notifyTemplate.isNotBlank()
    }

    private val mComparator = Comparator<AppInfo> { o1, o2 ->
        // Keep configured apps pinned on top regardless of selected sort mode.
        val configCompare = compareConfigPriority(o1, o2)
        if (configCompare != 0) {
            return@Comparator configCompare
        }

        val result = when (currentSortOption) {
            SortOption.LABEL -> compareString(o1.label, o2.label)
            SortOption.PACKAGE -> compareString(o1.packageName, o2.packageName)
            SortOption.USAGE -> {
                val u1 = usageStatsMap[o1.packageName] ?: 0L
                val u2 = usageStatsMap[o2.packageName] ?: 0L
                u1.compareTo(u2)
            }
            SortOption.SELECTION -> compareString(o1.label, o2.label) // Fallback for equal priority
        }

        if (isAscending) result else -result
    }

    private fun compareConfigPriority(o1: AppInfo, o2: AppInfo): Int {
        val hasConfig1 = hasEffectiveConfig(o1)
        val hasConfig2 = hasEffectiveConfig(o2)
        if (hasConfig1 != hasConfig2) {
            return if (hasConfig1) -1 else 1
        }

        // Tie-break when both are configured:
        // blocked > forwarding > template-only > none.
        if (o1.blocked != o2.blocked) {
            return if (o1.blocked) -1 else 1
        }
        if (o1.forwarding != o2.forwarding) {
            return if (o1.forwarding) -1 else 1
        }
        val hasTemplate1 = o1.notifyTemplate.isNotBlank()
        val hasTemplate2 = o2.notifyTemplate.isNotBlank()
        if (hasTemplate1 != hasTemplate2) {
            return if (hasTemplate1) -1 else 1
        }
        return 0
    }

    private fun compareString(s1: String?, s2: String?): Int {
        if (s1 == null && s2 == null) return 0
        if (s1 == null) return -1
        if (s2 == null) return 1
        return s1.compareTo(s2, ignoreCase = true)
    }
}
