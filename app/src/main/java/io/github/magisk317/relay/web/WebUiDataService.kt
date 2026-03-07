package io.github.magisk317.relay.web

import android.content.Context
import android.content.pm.PackageManager
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.common.utils.AppPreferencesDataStore
import io.github.magisk317.relay.data.db.AppDatabase
import io.github.magisk317.relay.data.db.entity.AppInfo
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.data.update.GithubUpdateChecker
import io.github.magisk317.relay.data.update.UpgradeCheckResult
import io.github.magisk317.relay.forwarder.entity.Sender
import io.github.magisk317.relay.forwarder.utils.SenderType
import io.github.magisk317.relay.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class WebUiDataService(context: Context) {

    private val appContext = context.applicationContext ?: context
    private val database by lazy { AppDatabase.getInstance(appContext) }

    suspend fun getOverview(): OverviewState = withContext(Dispatchers.IO) {
        val apps = loadMergedAppItems()
        val records = database.smsMsgDao().getAll().take(80)
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

    suspend fun loadMergedAppItems(): List<AppItem> = withContext(Dispatchers.IO) {
        val dao = database.appInfoDao()
        val configMap = dao.getAll().associateBy { it.packageName }
        val packageManager = appContext.packageManager
        packageManager.getInstalledApplications(PackageManager.MATCH_ALL)
            .asSequence()
            .map { appInfo ->
                val packageName = appInfo.packageName
                val label = runCatching {
                    packageManager.getApplicationLabel(appInfo).toString()
                }.getOrDefault(packageName).ifBlank { packageName }
                val config = configMap[packageName]
                AppItem(
                    packageName = packageName,
                    label = label,
                    blocked = config?.blocked ?: false,
                    forwarding = config?.forwarding ?: false,
                    notifyTemplate = config?.notifyTemplate.orEmpty(),
                )
            }
            .sortedWith(
                compareByDescending<AppItem> { it.blocked || it.forwarding || it.notifyTemplate.isNotBlank() }
                    .thenBy { it.label.lowercase() },
            )
            .toList()
    }

    suspend fun updateApp(packageName: String, payload: AppUpdatePayload): AppItem = withContext(Dispatchers.IO) {
        val dao = database.appInfoDao()
        val current = dao.getByPackageName(packageName)
        val label = current?.label?.takeIf { it.isNotBlank() }
            ?: resolveInstalledAppLabel(packageName)
            ?: packageName
        val next = (current ?: AppInfo(packageName = packageName, label = label)).copy(
            label = label,
            blocked = payload.blocked ?: current?.blocked ?: false,
            forwarding = payload.forwarding ?: current?.forwarding ?: false,
            notifyTemplate = payload.notifyTemplate ?: current?.notifyTemplate.orEmpty(),
        )
        dao.insert(next)
        next.toItem()
    }

    suspend fun getRecords(limit: Int): List<RecordItem> = withContext(Dispatchers.IO) {
        database.smsMsgDao().getAll().take(limit).map { it.toRecordItem() }
    }

    suspend fun deleteRecord(recordId: Long): Boolean = withContext(Dispatchers.IO) {
        val dao = database.smsMsgDao()
        val existing = dao.getById(recordId) ?: return@withContext false
        dao.delete(existing)
        true
    }

    suspend fun getAdvancedState(): AdvancedState = withContext(Dispatchers.IO) {
        val senders = database.senderDao().getAll()
        AdvancedState(
            enableSmsBlacklist = AppPreferencesDataStore.getBoolean(
                appContext,
                PrefConst.KEY_ENABLE_SMS_BLACKLIST,
                false,
            ),
            webUiLanAccess = AppPreferencesDataStore.getBoolean(
                appContext,
                PrefConst.KEY_WEBUI_LAN_ACCESS,
                false,
            ),
            senderTotal = senders.size,
            senderEnabled = senders.count { it.status == 1 },
            senderAppNotifyEnabled = senders.count { it.status == 1 && it.receiveAppNotify == 1 },
        )
    }

    suspend fun updateAdvanced(payload: AdvancedUpdatePayload): AdvancedState = withContext(Dispatchers.IO) {
        payload.enableSmsBlacklist?.let {
            AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_ENABLE_SMS_BLACKLIST, it)
        }
        payload.webUiLanAccess?.let {
            AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_WEBUI_LAN_ACCESS, it)
        }
        getAdvancedState()
    }

    suspend fun getSettingsState(): SettingsState = withContext(Dispatchers.IO) {
        SettingsState(
            enable = AppPreferencesDataStore.getBoolean(appContext, PrefConst.KEY_ENABLE, true),
            copyToClipboard = AppPreferencesDataStore.getBoolean(appContext, PrefConst.KEY_COPY_TO_CLIPBOARD, false),
            showToast = AppPreferencesDataStore.getBoolean(appContext, PrefConst.KEY_SHOW_TOAST, true),
            showCodeNotification = AppPreferencesDataStore.getBoolean(
                appContext,
                PrefConst.KEY_SHOW_CODE_NOTIFICATION,
                true,
            ),
            enableAutoInputCode = AppPreferencesDataStore.getBoolean(
                appContext,
                PrefConst.KEY_ENABLE_AUTO_INPUT_CODE,
                true,
            ),
            enableAutoEnterCode = AppPreferencesDataStore.getBoolean(
                appContext,
                PrefConst.KEY_ENABLE_AUTO_ENTER_CODE,
                false,
            ),
            verboseLogMode = AppPreferencesDataStore.getBoolean(appContext, PrefConst.KEY_VERBOSE_LOG_MODE, false),
            blockSms = AppPreferencesDataStore.getBoolean(appContext, PrefConst.KEY_BLOCK_SMS, false),
            forceStopRecovery = AppPreferencesDataStore.getBoolean(
                appContext,
                PrefConst.KEY_FORCE_STOP_RECOVERY,
                false,
            ),
        )
    }

    suspend fun updateSettings(payload: SettingsUpdatePayload): SettingsState = withContext(Dispatchers.IO) {
        payload.enable?.let { AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_ENABLE, it) }
        payload.copyToClipboard?.let {
            AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_COPY_TO_CLIPBOARD, it)
        }
        payload.showToast?.let { AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_SHOW_TOAST, it) }
        payload.showCodeNotification?.let {
            AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_SHOW_CODE_NOTIFICATION, it)
        }
        payload.enableAutoInputCode?.let {
            AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, it)
        }
        payload.enableAutoEnterCode?.let {
            AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_ENABLE_AUTO_ENTER_CODE, it)
        }
        payload.verboseLogMode?.let {
            AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_VERBOSE_LOG_MODE, it)
        }
        payload.blockSms?.let {
            AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_BLOCK_SMS, it)
        }
        payload.forceStopRecovery?.let {
            AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_FORCE_STOP_RECOVERY, it)
        }
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
            smsBlacklistNumbers = AppPreferencesDataStore.getString(appContext, PrefConst.KEY_SMS_BLACKLIST_NUMBERS, ""),
            smsBlacklistPrefixes = AppPreferencesDataStore.getString(
                appContext,
                PrefConst.KEY_SMS_BLACKLIST_PREFIXES,
                "",
            ),
            smsBlacklistRegex = AppPreferencesDataStore.getString(appContext, PrefConst.KEY_SMS_BLACKLIST_REGEX, ""),
            smsBlacklistContent = AppPreferencesDataStore.getString(appContext, PrefConst.KEY_SMS_BLACKLIST_CONTENT, ""),
            smsBlacklistActionDelete = AppPreferencesDataStore.getBoolean(
                appContext,
                PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE,
                true,
            ),
            smsBlacklistActionBlock = AppPreferencesDataStore.getBoolean(
                appContext,
                PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK,
                false,
            ),
        )
    }

    suspend fun updateIntercept(payload: InterceptUpdatePayload): InterceptState = withContext(Dispatchers.IO) {
        payload.smsBlacklistNumbers?.let {
            AppPreferencesDataStore.setString(appContext, PrefConst.KEY_SMS_BLACKLIST_NUMBERS, it)
        }
        payload.smsBlacklistPrefixes?.let {
            AppPreferencesDataStore.setString(appContext, PrefConst.KEY_SMS_BLACKLIST_PREFIXES, it)
        }
        payload.smsBlacklistRegex?.let {
            AppPreferencesDataStore.setString(appContext, PrefConst.KEY_SMS_BLACKLIST_REGEX, it)
        }
        payload.smsBlacklistContent?.let {
            AppPreferencesDataStore.setString(appContext, PrefConst.KEY_SMS_BLACKLIST_CONTENT, it)
        }
        payload.smsBlacklistActionDelete?.let {
            AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE, it)
        }
        payload.smsBlacklistActionBlock?.let {
            AppPreferencesDataStore.setBoolean(appContext, PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK, it)
        }
        getInterceptState()
    }

    suspend fun getSenders(): List<SenderItem> = withContext(Dispatchers.IO) {
        database.senderDao().getAll().map { it.toSenderItem() }
    }

    suspend fun createSender(payload: SenderCreatePayload): SenderItem? = withContext(Dispatchers.IO) {
        val dao = database.senderDao()
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
        val insertedId = dao.insert(newSender)
        dao.getOne(insertedId)?.toSenderItem()
    }

    suspend fun updateSender(senderId: Long, payload: SenderUpdatePayload): SenderItem? = withContext(Dispatchers.IO) {
        val dao = database.senderDao()
        val current = dao.getOne(senderId) ?: return@withContext null
        payload.name?.trim()?.let { current.name = it }
        payload.type?.let { current.type = it }
        payload.jsonSetting?.let { current.jsonSetting = it }
        current.status = if (payload.status ?: (current.status == 1)) 1 else 0
        current.receiveCode = if (payload.receiveCode ?: (current.receiveCode == 1)) 1 else 0
        current.receiveNonCode = if (payload.receiveNonCode ?: (current.receiveNonCode == 1)) 1 else 0
        current.receiveAppNotify = if (payload.receiveAppNotify ?: (current.receiveAppNotify == 1)) 1 else 0
        current.receiveCallNotify = if (payload.receiveCallNotify ?: (current.receiveCallNotify == 1)) 1 else 0
        dao.update(current)
        current.toSenderItem()
    }

    suspend fun deleteSender(senderId: Long): Boolean = withContext(Dispatchers.IO) {
        val dao = database.senderDao()
        val current = dao.getOne(senderId) ?: return@withContext false
        dao.delete(current)
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

    private fun SmsMsg.toRecordItem(): RecordItem = RecordItem(
        id = id ?: -1,
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
        SenderType.DINGTALK_INNER_ROBOT -> "钉钉内部机器人"
        SenderType.FEISHU_APP -> "飞书应用"
        SenderType.URL_SCHEME -> "URL Scheme"
        SenderType.SOCKET -> "Socket"
        else -> "Unknown($type)"
    }
}
