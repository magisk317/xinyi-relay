package io.github.magisk317.relay.ui.home.settings

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.relay.mobilefeature.settings.BuildConfig
import io.github.magisk317.relay.contract.constant.RelayAppConst as Const
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.contract.constant.PrefRestoreTypeRegistry
import io.github.magisk317.relay.contract.constant.PrefValueType
import io.github.magisk317.relay.common.utils.PackageUtils
import io.github.magisk317.smscode.runtime.common.utils.StorageUtils
import io.github.magisk317.smscode.runtime.common.utils.BrowserUtils
import io.github.magisk317.relay.android.common.utils.XLog
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import android.content.Intent
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.backup.RelayBackupManager
import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.engine.service.AppConfigRepository
import io.github.magisk317.relay.engine.service.MessageRecordRepository
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.android.prefs.HookPreferenceMirror
import io.github.magisk317.smscode.runtime.common.backup.BackupImportResult
import io.github.magisk317.smscode.runtime.common.backup.BackupRule
import io.github.magisk317.smscode.runtime.common.backup.BackupSmsRecord
import io.github.magisk317.smscode.runtime.common.backup.ExportResult
import io.github.magisk317.uikit.theme.UiKitStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

sealed class SettingsEvent {
    data object ShowPrivacyPolicy : SettingsEvent()
    data object NavigateToRules : SettingsEvent()
    data object NavigateToRecords : SettingsEvent()
    data object StartPlayUpdate : SettingsEvent()
    data class ShowSnackbar(val message: String) : SettingsEvent()
}

internal sealed class SettingsBackupEvent {
    data class BackupResult(
        val success: Boolean,
        val inspection: RelayBackupManager.BackupInspection? = null,
    ) : SettingsBackupEvent()

    data class RestoreResult(val result: BackupImportResult) : SettingsBackupEvent()
    data class ImportDialogConfirm(val uri: String) : SettingsBackupEvent()
}

internal data class PendingSettingsBackupEvent(
    val id: Long,
    val event: SettingsBackupEvent,
)

internal class SettingsBackupEventQueue {
    private val nextId = AtomicLong(0L)
    private val _events = MutableStateFlow<List<PendingSettingsBackupEvent>>(emptyList())

    val events: StateFlow<List<PendingSettingsBackupEvent>> = _events.asStateFlow()

    fun emit(event: SettingsBackupEvent): Boolean {
        if (event is SettingsBackupEvent.ImportDialogConfirm) {
            return emitUniqueImportDialog(event)
        }
        _events.update { pending ->
            pending + PendingSettingsBackupEvent(
                id = nextId.incrementAndGet(),
                event = event,
            )
        }
        return true
    }

    fun acknowledge(id: Long) {
        _events.update { pending -> pending.filterNot { it.id == id } }
    }

    private fun emitUniqueImportDialog(event: SettingsBackupEvent.ImportDialogConfirm): Boolean {
        while (true) {
            val pending = _events.value
            val alreadyPending = pending.any { queued ->
                val queuedEvent = queued.event
                queuedEvent is SettingsBackupEvent.ImportDialogConfirm && queuedEvent.uri == event.uri
            }
            if (alreadyPending) return false
            val appended = pending + PendingSettingsBackupEvent(
                id = nextId.incrementAndGet(),
                event = event,
            )
            if (_events.compareAndSet(pending, appended)) return true
        }
    }
}

fun resolvePreferredUpdateEvent(isPlayFlavor: Boolean): SettingsEvent? =
    SettingsEvent.StartPlayUpdate.takeIf { isPlayFlavor }

class SettingsViewModel(
    application: Application,
    private val configRepository: AppConfigRepository,
    private val recordRepository: MessageRecordRepository,
    private val settingsRepository: SettingsPreferencesRepository,
    private val preferenceDataSource: PreferenceDataSource,
) : AndroidViewModel(application) {
    data class CoercedRestoreValue(
        val type: PrefValueType,
        val booleanValue: Boolean? = null,
        val intValue: Int? = null,
        val floatValue: Float? = null,
        val stringValue: String? = null,
    ) {
        val shouldWrite: Boolean
            get() = when (type) {
                PrefValueType.BOOLEAN -> booleanValue != null
                PrefValueType.INT -> intValue != null
                PrefValueType.FLOAT -> floatValue != null
                PrefValueType.STRING -> true
            }
    }

    private val _eventsFlow = MutableSharedFlow<SettingsEvent>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val eventsFlow = _eventsFlow.asSharedFlow()
    private val backupEventQueue = SettingsBackupEventQueue()
    internal val backupEventsFlow = backupEventQueue.events

    internal fun acknowledgeBackupEvent(id: Long) {
        backupEventQueue.acknowledge(id)
    }

    data class ThemeState(
        val mode: Int,
        val uiKitStyle: Int = UiKitStyle.Expressive.value,
        val centerX: Float = -1f,
        val centerY: Float = -1f,
    )
    data class LanguageState(val languageTag: String = "")

    val themeState: StateFlow<ThemeState> = sharedThemeState.asStateFlow()
    val languageState: StateFlow<LanguageState> = sharedLanguageState.asStateFlow()

    val smsRecordCount: StateFlow<Long> = recordRepository.countFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Const.FLOW_STOP_TIMEOUT_MS),
            initialValue = 0L,
        )

    init {
        viewModelScope.launch {
            val mode = settingsRepository.getThemeMode()
            val uiKitStyle = settingsRepository.getUiKitStyle()
            sharedThemeState.value = ThemeState(mode, uiKitStyle)
        }
        viewModelScope.launch {
            sharedLanguageState.value = LanguageState(settingsRepository.getLanguageTag())
        }
    }

    fun setThemeMode(mode: Int, x: Float = -1f, y: Float = -1f) {
        persistThemeMode(mode, x, y)
    }

    fun previewThemeMode(mode: Int, x: Float = -1f, y: Float = -1f) {
        sharedThemeState.value = sharedThemeState.value.copy(mode = mode, centerX = x, centerY = y)
    }

    fun persistThemeMode(mode: Int, x: Float = -1f, y: Float = -1f) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
            sharedThemeState.value = sharedThemeState.value.copy(mode = mode, centerX = x, centerY = y)
        }
    }

    fun previewUiKitStyle(style: Int) {
        sharedThemeState.value = sharedThemeState.value.copy(uiKitStyle = style)
    }

    fun persistUiKitStyle(style: Int) {
        viewModelScope.launch {
            settingsRepository.setUiKitStyle(style)
            sharedThemeState.value = sharedThemeState.value.copy(uiKitStyle = style)
        }
    }

    fun setLanguageTag(languageTag: String) {
        persistLanguageTag(languageTag)
    }

    fun previewLanguageTag(languageTag: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val localeManager = getApplication<Application>().getSystemService(android.app.LocaleManager::class.java)
            localeManager?.applicationLocales = if (languageTag.isBlank()) {
                android.os.LocaleList.getEmptyLocaleList()
            } else {
                android.os.LocaleList.forLanguageTags(languageTag)
            }
        }
        sharedLanguageState.value = LanguageState(languageTag)
    }

    fun persistLanguageTag(languageTag: String) {
        viewModelScope.launch {
            settingsRepository.setLanguageTag(languageTag)
            previewLanguageTag(languageTag)
        }
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun handleArguments(args: Bundle?) {
        if (args == null) return

        viewModelScope.launch {
            if (!settingsRepository.isPrivacyPolicyAccepted()) {
                _eventsFlow.tryEmit(SettingsEvent.ShowPrivacyPolicy)
            } else {
                val extraAction = args.getString(Const.EXTRA_ACTION)
                if ("relay_records" == extraAction || "smscode_records" == extraAction) {
                    args.remove(Const.EXTRA_ACTION)
                    _eventsFlow.tryEmit(SettingsEvent.NavigateToRecords)
                } else if ("relay_rules" == extraAction || "smscode_rules" == extraAction) {
                    args.remove(Const.EXTRA_ACTION)
                    _eventsFlow.tryEmit(SettingsEvent.NavigateToRules)
                }
            }
        }
    }

    fun pinShortcutToDesktop() {
        val context = getApplication<Application>()
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            val intent = Intent().apply {
                setClassName(context.packageName, "io.github.magisk317.relay.ui.home.MainActivity")
                action = Intent.ACTION_MAIN
            }
            // 使用挂载了 CATEGORY_INFO 的主入口强行注册
            val mainActivity = android.content.ComponentName(context.packageName, "io.github.magisk317.relay.ui.home.MainActivity")
            val shortcut = ShortcutInfoCompat.Builder(context, "shortcut_main")
                .setShortLabel(context.getString(R.string.app_name))
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(intent)
                .setActivity(mainActivity)
                .build()
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        } else {
            _eventsFlow.tryEmit(SettingsEvent.ShowSnackbar(context.getString(R.string.settings_shortcut_unsupported)))
        }
    }

    fun isLauncherIconVisible(): Boolean {
        val context = getApplication<Application>()
        val component = ComponentName(context.packageName, "io.github.magisk317.relay.ui.home.LauncherActivity")
        val pm = context.packageManager
        return when (pm.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
            -> false

            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> runCatching {
                pm.getActivityInfo(component, 0).enabled
            }.getOrDefault(false)

            else -> false
        }
    }

    fun setLauncherIconVisible(visible: Boolean): Boolean {
        val context = getApplication<Application>()
        val component = ComponentName(context.packageName, "io.github.magisk317.relay.ui.home.LauncherActivity")
        val pm = context.packageManager
        val newState = if (visible) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        return runCatching {
            pm.setComponentEnabledSetting(
                component,
                newState,
                PackageManager.DONT_KILL_APP,
            )
            true
        }.onFailure {
            XLog.e("Failed to set launcher icon visible=$visible", it)
        }.getOrElse { false }
    }

    fun showSourceProject() {
        BrowserUtils.openWebPage(
            getApplication(),
            Const.PROJECT_SOURCE_CODE_URL,
            R.string.browser_install_or_enable_prompt,
        )?.let {
            _eventsFlow.tryEmit(SettingsEvent.ShowSnackbar(it))
        }
    }

    fun setInternalFilesWritable() {
        // Older releases widened Android/data/<package> to 0777. Hook runtime
        // state now goes through DBProvider, so only normalize the legacy tree.
        StorageUtils.repairExternalAppDataPermissions(getApplication())
        viewModelScope.launch {
            HookPreferenceMirror.publish(getApplication())
        }
    }

    fun requestPreferredUpdate() {
        viewModelScope.launch {
            val event = resolvePreferredUpdateEvent(BuildConfig.HAS_BILLING)
            event?.let(_eventsFlow::tryEmit)
        }
    }

    fun handleBackupArguments(uri: android.net.Uri?): Boolean {
        if (uri == null) return false
        // An already-pending event for this URI also counts as accepted: the durable queue
        // already represents the import request, so the Activity may safely clear its Intent.
        backupEventQueue.emit(SettingsBackupEvent.ImportDialogConfirm(uri.toString()))
        return true
    }

    fun performBackup(
        uri: android.net.Uri,
        includeConfig: Boolean,
        includeRules: Boolean,
        includeRecords: Boolean,
        includeDatabase: Boolean,
    ) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                XLog.i(
                    "Backup start: uri=%s includeConfig=%s includeRules=%s includeRecords=%s includeDatabase=%s",
                    uri.toString(),
                    includeConfig,
                    includeRules,
                    includeRecords,
                    includeDatabase,
                )
                val rules = if (includeRules) {
                    withContext(Dispatchers.IO) {
                        configRepository.getAllSmsCodeRules()
                            .map { BackupRule(it.company, it.codeKeyword, it.codeRegex) }
                    }
                } else {
                    emptyList()
                }

                val records = if (includeRecords) {
                    withContext(Dispatchers.IO) {
                        recordRepository.queryAll()
                            .map {
                                BackupSmsRecord(
                                    sender = it.sender,
                                    body = it.body,
                                    date = it.date,
                                    processedTime = it.processedTime,
                                    company = it.company,
                                    smsCode = it.smsCode,
                                    packageName = it.packageName,
                                    msgType = it.msgType,
                                    callType = it.callType,
                                    forwardStatus = it.forwardStatus,
                                    forwardTarget = it.forwardTarget,
                                    forwardMessage = it.forwardMessage,
                                    forwardTime = it.forwardTime,
                                )
                            }
                    }
                } else {
                    null
                }

                val prefs = if (includeConfig) {
                    withContext(Dispatchers.IO) {
                        ensureDataStoreLoaded(context)
                        val sharedPrefs = context.getSharedPreferences(
                            "xposed_prefs",
                            android.content.Context.MODE_PRIVATE,
                        )
                        val allPrefs = sharedPrefs.all
                        val map = HashMap<String, String?>()
                        for ((k, v) in allPrefs) {
                            if (k.startsWith("internal_")) continue
                            map[k] = v?.toString()
                        }
                        map
                    }
                } else {
                    null
                }

                XLog.i(
                    "Backup payload prepared: rules=%d records=%d prefs=%d",
                    rules.size,
                    records?.size ?: 0,
                    prefs?.size ?: 0,
                )
                val result = withContext(Dispatchers.IO) {
                    RelayBackupManager.exportBackup(
                        context = context,
                        uri = uri,
                        ruleList = rules,
                        preferences = prefs,
                        records = records,
                        appVersion = BuildConfig.VERSION_NAME,
                        includeDatabase = includeDatabase,
                    )
                }
                XLog.i("Backup finished: result=%s", result.name)
                var backupInspection: RelayBackupManager.BackupInspection? = null
                if (result == ExportResult.SUCCESS) {
                    try {
                        val inspected = withContext(Dispatchers.IO) {
                            RelayBackupManager.inspectBackup(context, uri)
                        }
                        backupInspection = inspected
                        XLog.i("Backup inspect: %s", inspected.toLogString())
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (t: Throwable) {
                        XLog.w("Backup inspect failed: %s", t.message ?: t.javaClass.simpleName)
                    }
                }
                backupEventQueue.emit(
                    SettingsBackupEvent.BackupResult(
                        success = result == ExportResult.SUCCESS,
                        inspection = backupInspection,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                XLog.e("Backup failed", e)
                backupEventQueue.emit(SettingsBackupEvent.BackupResult(false))
            }
        }
    }

    fun performRestore(
        uri: android.net.Uri,
        restoreConfig: Boolean,
        restoreRules: Boolean,
        restoreRecords: Boolean,
        restoreDatabase: Boolean,
    ) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                XLog.i(
                    "Restore start: uri=%s restoreConfig=%s restoreRules=%s restoreRecords=%s restoreDatabase=%s",
                    uri.toString(),
                    restoreConfig,
                    restoreRules,
                    restoreRecords,
                    restoreDatabase,
                )
                val importResult = withContext(Dispatchers.IO) {
                    RelayBackupManager.importRuleList(context, uri, BuildConfig.VERSION_NAME)
                }
                try {
                    val inspection = withContext(Dispatchers.IO) {
                        RelayBackupManager.inspectBackup(context, uri)
                    }
                    XLog.i("Restore inspect: %s", inspection.toLogString())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    XLog.w("Restore inspect failed: %s", t.message ?: t.javaClass.simpleName)
                }
                XLog.i(
                    "Restore import result=%s rules=%d records=%d prefs=%d warning=%s",
                    importResult.result.name,
                    importResult.rules.size,
                    importResult.records?.size ?: 0,
                    importResult.preferences?.size ?: 0,
                    importResult.warning?.name ?: "none",
                )

                if (importResult.result == io.github.magisk317.smscode.runtime.common.backup.ImportResult.SUCCESS) {
                    withContext(Dispatchers.IO) {
                        if (restoreDatabase) {
                            val restored = RelayBackupManager.restoreDatabaseFromBackup(context, uri)
                            if (!restored) {
                                throw IllegalStateException("Restore database failed: backup zip has no database files")
                            }
                            if (restoreRules || restoreRecords) {
                                XLog.i(
                                    "Restore database enabled: skip logical restore rules=%s records=%s",
                                    restoreRules,
                                    restoreRecords,
                                )
                            }
                        } else {
                            if (restoreRules) restoreRules(context, importResult.rules)
                            if (restoreRecords) restoreRecords(context, importResult.records.orEmpty())
                        }
                        if (restoreConfig) restorePreferences(context, importResult.preferences.orEmpty())
                    }
                    XLog.i("Restore apply finished")
                }
                backupEventQueue.emit(SettingsBackupEvent.RestoreResult(importResult))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                XLog.e("Restore failed", e)
                backupEventQueue.emit(
                    SettingsBackupEvent.RestoreResult(
                        BackupImportResult(io.github.magisk317.smscode.runtime.common.backup.ImportResult.READ_FAILED),
                    ),
                )
            }
        }
    }

    private suspend fun restoreRules(context: Context, rules: List<BackupRule>) {
        if (rules.isEmpty()) return
        val entities = rules.map {
            io.github.magisk317.relay.android.data.db.entity.SmsCodeRule(it.company, it.codeKeyword, it.codeRegex)
        }
        configRepository.insertSmsCodeRules(entities)
    }

    private suspend fun restoreRecords(context: Context, records: List<BackupSmsRecord>) {
        if (records.isEmpty()) {
            XLog.w("Restore records skipped: empty list")
            return
        }
        val beforeCount = recordRepository.queryAll().size
        val entities = records.map {
            io.github.magisk317.relay.android.data.db.entity.SmsMsg(
                sender = it.sender,
                body = it.body,
                date = it.date,
                processedTime = it.processedTime,
                company = it.company,
                smsCode = it.smsCode,
                packageName = it.packageName,
                msgType = it.msgType,
                callType = it.callType,
                forwardStatus = it.forwardStatus,
                forwardTarget = it.forwardTarget,
                forwardMessage = it.forwardMessage,
                forwardTime = it.forwardTime,
            )
        }
        recordRepository.insertList(entities)
        val afterCount = recordRepository.queryAll().size
        XLog.i(
            "Restore records finished: requested=%d before=%d after=%d delta=%d",
            records.size,
            beforeCount,
            afterCount,
            afterCount - beforeCount,
        )
    }

    private suspend fun restorePreferences(context: Context, prefsMap: Map<String, String?>) {
        if (prefsMap.isEmpty()) return
        for ((k, v) in prefsMap) {
            if (v == null) continue
            val strV = v
            val coerced = coerceRestoreValue(k, strV)
            if (!coerced.shouldWrite) {
                XLog.w(
                    "Restore preference skipped: key=%s raw=%s expectedType=%s",
                    k,
                    strV,
                    coerced.type.name,
                )
                continue
            }
            when (coerced.type) {
                PrefValueType.BOOLEAN -> {
                    val boolValue = coerced.booleanValue ?: continue
                    preferenceDataSource.setBoolean(k, boolValue)
                    if (k == PrefConst.KEY_SHOW_LAUNCHER_ICON) {
                        setLauncherIconVisible(boolValue)
                    }
                }

                PrefValueType.INT -> {
                    val intValue = coerced.intValue ?: continue
                    preferenceDataSource.setInt(k, intValue)
                }

                PrefValueType.FLOAT -> {
                    val floatValue = coerced.floatValue ?: continue
                    preferenceDataSource.setFloat(k, floatValue)
                }

                PrefValueType.STRING -> {
                    preferenceDataSource.setString(k, coerced.stringValue ?: strV)
                }
            }
        }
        HookPreferenceMirror.publish(getApplication())
    }

    private suspend fun ensureDataStoreLoaded(_context: android.content.Context) {
        // Trigger read to ensure in-memory cache if needed; keep no-op for now.
    }

    suspend fun inspectBackup(uri: android.net.Uri): RelayBackupManager.BackupInspection? {
        val context = getApplication<Application>()
        return withContext(Dispatchers.IO) {
            try {
                RelayBackupManager.inspectBackup(context, uri)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                XLog.w("Inspect backup failed: %s", failure.message ?: failure.javaClass.simpleName)
                null
            }
        }
    }

    companion object {
        private val sharedThemeState = MutableStateFlow(ThemeState(0, UiKitStyle.Expressive.value))
        private val sharedLanguageState = MutableStateFlow(LanguageState())

        @JvmStatic
        fun coerceRestoreValue(key: String, rawValue: String): CoercedRestoreValue {
            return when (PrefRestoreTypeRegistry.typeOf(key)) {
                PrefValueType.BOOLEAN -> CoercedRestoreValue(
                    type = PrefValueType.BOOLEAN,
                    booleanValue = parseBooleanValue(rawValue),
                )

                PrefValueType.INT -> CoercedRestoreValue(
                    type = PrefValueType.INT,
                    intValue = rawValue.trim().toIntOrNull(),
                )

                PrefValueType.FLOAT -> CoercedRestoreValue(
                    type = PrefValueType.FLOAT,
                    floatValue = rawValue.trim().toFloatOrNull(),
                )

                PrefValueType.STRING -> CoercedRestoreValue(
                    type = PrefValueType.STRING,
                    stringValue = rawValue,
                )
            }
        }

        @JvmStatic
        fun parseBooleanValue(rawValue: String): Boolean? {
            return when (rawValue.trim().lowercase(Locale.ROOT)) {
                "1", "true", "yes", "y", "on" -> true
                "0", "false", "no", "n", "off" -> false
                else -> null
            }
        }
    }
}
