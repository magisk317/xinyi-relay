package io.github.magisk317.relay.webui

import android.content.Context
import android.content.pm.PackageManager
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.data.db.entity.AppInfo
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.data.repository.AnalyticsRepository
import io.github.magisk317.relay.data.repository.AdvancedSettingsUpdate
import io.github.magisk317.relay.data.repository.RelayRecordRepository
import io.github.magisk317.relay.data.repository.SettingsRepository
import io.github.magisk317.relay.data.repository.UserSettingsUpdate
import io.github.magisk317.relay.data.update.GithubUpdateChecker
import io.github.magisk317.relay.data.update.UpgradeCheckResult
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.domain.sender.SenderType
import io.github.magisk317.relay.core.BuildConfig
import io.github.magisk317.relay.domain.model.Sender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WebUiDataService(context: Context) {

    private val appContext = context.applicationContext ?: context
    private val runtimeGraph by lazy { RuntimeGraph.from(appContext) }
    private val analyticsRepository by lazy { runtimeGraph.analyticsRepository }
    private val configRepository by lazy { runtimeGraph.configRepository }
    private val settingsRepository by lazy { runtimeGraph.settingsRepository }
    private val relayRecordRepository by lazy { runtimeGraph.relayRecordRepository }
    private val preferenceDataSource by lazy { runtimeGraph.preferenceDataSource }

    suspend fun getOverview(): OverviewState = withContext(Dispatchers.IO) {
        val apps = loadMergedAppItems()
        val records = relayRecordRepository.listRecords(80)
        val advanced = getAdvancedState()
        val version = getVersionState()
        OverviewState(
            appCount = apps.size,
            blockedCount = apps.count { it.blocked },
            forwardingCount = apps.count { it.forwarding },
            recordCount = records.size,
            senderTotal = advanced.senderTotal,
            senderEnabled = advanced.senderEnabled,
            senderAppNotifyEnabled = advanced.senderAppNotifyEnabled,
            version = version,
        )
    }

    suspend fun getAnalytics(): AnalyticsResponse = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val window7d = now - 7L * 24L * 60L * 60L * 1000L
        val window30d = now - 30L * 24L * 60L * 60L * 1000L

        val senderConfig = analyticsRepository.senderConfigurationSnapshot()
        val configuredByType = senderConfig.configuredByType
        val enabledByType = senderConfig.enabledByType

        suspend fun buildWindow(fromMs: Long): AnalyticsWindow {
            val snapshot = analyticsRepository.snapshot(fromMs)
            val senderStatsRows = snapshot.senderStats
            val senderTypes = (configuredByType.keys + senderStatsRows.map { it.senderType }).distinct()
            val senderStats = senderTypes.map { type ->
                val row = senderStatsRows.firstOrNull { it.senderType == type }
                SenderTypeStat(
                    senderType = type,
                    senderTypeLabel = senderTypeLabel(type),
                    configured = configuredByType[type] ?: 0,
                    enabled = enabledByType[type] ?: 0,
                    sent = row?.sent ?: 0L,
                    success = row?.success ?: 0L,
                    failed = row?.failed ?: 0L,
                )
            }.sortedBy { it.senderType }

            val summary = AnalyticsSummary(
                smsCodeDetected = snapshot.codeDetected,
                autoInputAttempt = snapshot.autoInputAttempt,
                autoInputSuccess = snapshot.autoInputSuccess,
                autoInputFail = snapshot.autoInputFailed,
                messageTotal = snapshot.totalMessages,
            )
            return AnalyticsWindow(summary = summary, senderStats = senderStats)
        }

        AnalyticsResponse(
            allTime = buildWindow(0L),
            last7d = buildWindow(window7d),
            last30d = buildWindow(window30d),
        )
    }

    suspend fun loadMergedAppItems(): List<AppItem> = withContext(Dispatchers.IO) {
        val configMap = configRepository.getAllAppInfo().associateBy { it.packageName }
        val packageManager = appContext.packageManager
        packageManager.getInstalledApplications(PackageManager.MATCH_ALL)
            .asSequence()
            .map { appInfo ->
                val packageName = appInfo.packageName
                val label = runCatching {
                    packageManager.getApplicationLabel(appInfo).toString()
                }.getOrDefault(packageName).ifBlank { packageName }
                val config = configMap[packageName]
                val item = AppItem(
                    packageName = packageName,
                    label = label,
                    blocked = config?.blocked ?: false,
                    forwarding = config?.forwarding ?: false,
                    notifyTemplate = config?.notifyTemplate.orEmpty(),
                )
                item to (config?.let(::hasPersistedAppConfig) == true)
            }
            .sortedWith(
                compareByDescending<Pair<AppItem, Boolean>> { it.second }
                    .thenBy { it.first.label.lowercase() },
            )
            .map { it.first }
            .toList()
    }

    suspend fun updateApp(packageName: String, payload: AppUpdatePayload): AppItem = withContext(Dispatchers.IO) {
        val current = configRepository.getAppInfoByPackage(packageName)
        val label = current?.label?.takeIf { it.isNotBlank() }
            ?: resolveInstalledAppLabel(packageName)
            ?: packageName
        val next = (current ?: AppInfo(packageName = packageName, label = label)).copy(
            label = label,
            blocked = payload.blocked ?: current?.blocked ?: false,
            forwarding = payload.forwarding ?: current?.forwarding ?: false,
            forwardingConfigured = if (payload.forwarding != null) {
                true
            } else {
                current?.forwardingConfigured ?: false
            },
            notifyTemplate = payload.notifyTemplate ?: current?.notifyTemplate.orEmpty(),
        )
        if (hasPersistedAppConfig(next)) {
            configRepository.upsertAppInfo(next)
        } else {
            configRepository.removeAppInfosByPackage(listOf(packageName))
        }
        next.toItem()
    }

    suspend fun getRecords(limit: Int): List<RecordItem> = withContext(Dispatchers.IO) {
        relayRecordRepository.listRecords(limit).map { it.toRecordItem() }
    }

    suspend fun deleteRecord(recordId: Long): Boolean = withContext(Dispatchers.IO) {
        relayRecordRepository.deleteRecord(recordId)
    }

    suspend fun getAdvancedState(): AdvancedState = withContext(Dispatchers.IO) {
        val senders = configRepository.getAllSenders()
        val advanced = settingsRepository.getAdvancedSnapshot()
        AdvancedState(
            enableSmsBlacklist = advanced.enableSmsBlacklist,
            webUiLanAccess = advanced.webUiLanAccess,
            senderTotal = senders.size,
            senderEnabled = senders.count { it.status == 1 },
            senderAppNotifyEnabled = senders.count { it.status == 1 && it.receiveAppNotify == 1 },
        )
    }

    suspend fun updateAdvanced(payload: AdvancedUpdatePayload): AdvancedState = withContext(Dispatchers.IO) {
        settingsRepository.updateAdvanced(
            AdvancedSettingsUpdate(
                enableSmsBlacklist = payload.enableSmsBlacklist,
                webUiLanAccess = payload.webUiLanAccess,
            ),
        )
        getAdvancedState()
    }

    suspend fun getSettingsState(): SettingsState = withContext(Dispatchers.IO) {
        val snapshot = settingsRepository.getUserSettingsSnapshot()
        SettingsState(
            moduleEnabled = snapshot.moduleEnabled,
            verificationFeaturesEnabled = snapshot.verificationFeaturesEnabled,
            relayFeaturesEnabled = snapshot.relayFeaturesEnabled,
            copyToClipboard = snapshot.copyToClipboard,
            showToast = snapshot.showToast,
            showCodeNotification = snapshot.showCodeNotification,
            blockSmsEnabled = snapshot.blockSmsEnabled,
            enableAutoInputCode = snapshot.enableAutoInputCode,
            enableAutoEnterCode = snapshot.enableAutoEnterCode,
            verboseLogMode = snapshot.verboseLogMode,
            smsBlacklistEnabled = snapshot.smsBlacklistEnabled,
            forceStopRecoveryEnabled = snapshot.forceStopRecoveryEnabled,
        )
    }

    suspend fun updateSettings(payload: SettingsUpdatePayload): SettingsState = withContext(Dispatchers.IO) {
        settingsRepository.updateUserSettings(
            UserSettingsUpdate(
                moduleEnabled = payload.moduleEnabled,
                verificationFeaturesEnabled = payload.verificationFeaturesEnabled,
                relayFeaturesEnabled = payload.relayFeaturesEnabled,
                copyToClipboard = payload.copyToClipboard,
                showToast = payload.showToast,
                showCodeNotification = payload.showCodeNotification,
                blockSmsEnabled = payload.blockSmsEnabled,
                enableAutoInputCode = payload.enableAutoInputCode,
                enableAutoEnterCode = payload.enableAutoEnterCode,
                verboseLogMode = payload.verboseLogMode,
                smsBlacklistEnabled = payload.smsBlacklistEnabled,
                forceStopRecoveryEnabled = payload.forceStopRecoveryEnabled,
            ),
        )
        getSettingsState()
    }

    suspend fun getVersionState(): VersionState = withContext(Dispatchers.IO) {
        val localVersionName = BuildConfig.VERSION_NAME
        val localVersionCode = BuildConfig.VERSION_CODE
        val checkedAt = System.currentTimeMillis()
        when (val result = GithubUpdateChecker.fetchUpgradeInfo()) {
            is UpgradeCheckResult.CheckFailed -> VersionState(
                localVersionName = localVersionName,
                localVersionCode = localVersionCode,
                status = "failed",
                message = result.message ?: "check_failed",
                checkedAt = checkedAt,
            )

            UpgradeCheckResult.NoUpdate -> VersionState(
                localVersionName = localVersionName,
                localVersionCode = localVersionCode,
                status = "no_update",
                updateAvailable = false,
                checkedAt = checkedAt,
            )

            is UpgradeCheckResult.ReleaseLink -> {
                val newer = GithubUpdateChecker.isNewer(localVersionName, result.release.versionName)
                VersionState(
                    localVersionName = localVersionName,
                    localVersionCode = localVersionCode,
                    latestVersionName = result.release.versionName,
                    releaseUrl = result.release.htmlUrl,
                    updateAvailable = newer,
                    status = if (newer) "ok" else "no_update",
                    checkedAt = checkedAt,
                )
            }

            is UpgradeCheckResult.Structured -> {
                val info = result.info
                val newer = if (info.versionCode > 0L) {
                    GithubUpdateChecker.isNewer(localVersionCode.toLong(), info.versionCode)
                } else {
                    GithubUpdateChecker.isNewer(localVersionName, info.versionName)
                }
                VersionState(
                    localVersionName = localVersionName,
                    localVersionCode = localVersionCode,
                    latestVersionName = info.versionName.ifBlank { null },
                    latestVersionCode = info.versionCode.takeIf { it > 0L },
                    releaseUrl = info.htmlUrl.ifBlank { null },
                    updateAvailable = newer,
                    status = if (newer) "ok" else "no_update",
                    checkedAt = checkedAt,
                )
            }
        }
    }

    suspend fun getInterceptState(): InterceptState = withContext(Dispatchers.IO) {
        InterceptState(
            smsBlacklistNumbers = preferenceDataSource.getString(PrefConst.KEY_SMS_BLACKLIST_NUMBERS, ""),
            smsBlacklistPrefixes = preferenceDataSource.getString(
                PrefConst.KEY_SMS_BLACKLIST_PREFIXES,
                "",
            ),
            smsBlacklistRegex = preferenceDataSource.getString(PrefConst.KEY_SMS_BLACKLIST_REGEX, ""),
            smsBlacklistContent = preferenceDataSource.getString(PrefConst.KEY_SMS_BLACKLIST_CONTENT, ""),
            smsBlacklistActionDelete = preferenceDataSource.getBoolean(
                PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE,
                true,
            ),
            smsBlacklistActionBlock = preferenceDataSource.getBoolean(
                PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK,
                false,
            ),
        )
    }

    suspend fun updateIntercept(payload: InterceptUpdatePayload): InterceptState = withContext(Dispatchers.IO) {
        payload.smsBlacklistNumbers?.let {
            preferenceDataSource.setString(PrefConst.KEY_SMS_BLACKLIST_NUMBERS, it)
        }
        payload.smsBlacklistPrefixes?.let {
            preferenceDataSource.setString(PrefConst.KEY_SMS_BLACKLIST_PREFIXES, it)
        }
        payload.smsBlacklistRegex?.let {
            preferenceDataSource.setString(PrefConst.KEY_SMS_BLACKLIST_REGEX, it)
        }
        payload.smsBlacklistContent?.let {
            preferenceDataSource.setString(PrefConst.KEY_SMS_BLACKLIST_CONTENT, it)
        }
        payload.smsBlacklistActionDelete?.let {
            preferenceDataSource.setBoolean(PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE, it)
        }
        payload.smsBlacklistActionBlock?.let {
            preferenceDataSource.setBoolean(PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK, it)
        }
        getInterceptState()
    }

    suspend fun getSenders(): List<SenderItem> = withContext(Dispatchers.IO) {
        configRepository.getAllSenders().map { it.toSenderItem() }
    }

    suspend fun createSender(payload: SenderCreatePayload): SenderItem? = withContext(Dispatchers.IO) {
        val newSender = Sender(
            id = 0L,
            type = payload.type,
            name = payload.name.trim(),
            jsonSetting = payload.jsonSetting,
            status = if (payload.status) 1 else 0,
            receiveCode = if (payload.receiveCode) 1 else 0,
            receiveNonCode = if (payload.receiveNonCode) 1 else 0,
            receiveAppNotify = if (payload.receiveAppNotify) 1 else 0,
            receiveCallNotify = if (payload.receiveCallNotify) 1 else 0,
        )
        val insertedId = configRepository.insertSender(newSender)
        configRepository.getSenderById(insertedId)?.toSenderItem()
    }

    suspend fun updateSender(senderId: Long, payload: SenderUpdatePayload): SenderItem? = withContext(Dispatchers.IO) {
        val current = configRepository.getSenderById(senderId) ?: return@withContext null
        val next = current.copy(
            name = payload.name?.trim() ?: current.name,
            type = payload.type ?: current.type,
            jsonSetting = payload.jsonSetting ?: current.jsonSetting,
            status = if (payload.status ?: (current.status == 1)) 1 else 0,
            receiveCode = if (payload.receiveCode ?: (current.receiveCode == 1)) 1 else 0,
            receiveNonCode = if (payload.receiveNonCode ?: (current.receiveNonCode == 1)) 1 else 0,
            receiveAppNotify = if (payload.receiveAppNotify ?: (current.receiveAppNotify == 1)) 1 else 0,
            receiveCallNotify = if (payload.receiveCallNotify ?: (current.receiveCallNotify == 1)) 1 else 0,
        )
        configRepository.updateSender(next)
        next.toSenderItem()
    }

    suspend fun deleteSender(senderId: Long): Boolean = withContext(Dispatchers.IO) {
        val current = configRepository.getSenderById(senderId) ?: return@withContext false
        configRepository.deleteSender(current)
        true
    }

    private fun resolveInstalledAppLabel(packageName: String): String? {
        val packageManager = appContext.packageManager
        return runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.MATCH_ALL)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun AppInfo.toItem(): AppItem = AppItem(
        packageName = packageName,
        label = label?.takeIf { it.isNotBlank() } ?: packageName,
        blocked = blocked,
        forwarding = forwarding,
        notifyTemplate = notifyTemplate,
    )

    private fun hasPersistedAppConfig(appInfo: AppInfo): Boolean {
        return appInfo.blocked || appInfo.forwardingConfigured || appInfo.notifyTemplate.isNotBlank()
    }

    private fun SmsMsg.toRecordItem(): RecordItem = RecordItem(
        id = id,
        date = date,
        sender = sender.orEmpty(),
        body = body.orEmpty(),
        smsCode = smsCode.orEmpty(),
        packageName = packageName.orEmpty(),
        msgType = msgType,
        callType = callType,
        forwardStatus = forwardStatus,
        forwardTarget = forwardTarget.orEmpty(),
        forwardMessage = forwardMessage.orEmpty(),
    )

    private fun Sender.toSenderItem(): SenderItem = SenderItem(
        id = id,
        name = name,
        type = type,
        typeLabel = senderTypeLabel(type),
        jsonSetting = jsonSetting,
        status = status == 1,
        receiveCode = receiveCode == 1,
        receiveNonCode = receiveNonCode == 1,
        receiveAppNotify = receiveAppNotify == 1,
        receiveCallNotify = receiveCallNotify == 1,
    )

    private fun senderTypeLabel(type: Int): String = when (type) {
        SenderType.DINGTALK_GROUP_ROBOT -> "钉钉群机器人"
        SenderType.EMAIL -> "邮件"
        SenderType.BARK -> "Bark"
        SenderType.WEBHOOK -> "Webhook"
        SenderType.WEWORK_ROBOT -> "企业微信机器人"
        SenderType.WEWORK_AGENT -> "企业微信应用"
        SenderType.SERVERCHAN -> "Server酱"
        SenderType.TELEGRAM -> "Telegram"
        SenderType.SMS -> "短信"
        SenderType.FEISHU -> "飞书"
        SenderType.PUSHPLUS -> "PushPlus"
        SenderType.GOTIFY -> "Gotify"
        SenderType.NTFY -> "ntfy"
        SenderType.DINGTALK_INNER_ROBOT -> "钉钉内部机器人"
        SenderType.FEISHU_APP -> "飞书应用"
        SenderType.URL_SCHEME -> "URL Scheme"
        SenderType.SOCKET -> "Socket"
        else -> "Unknown($type)"
    }
}
