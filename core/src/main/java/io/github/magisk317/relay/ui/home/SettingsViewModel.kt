package io.github.magisk317.relay.ui.home

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.TextUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.relay.core.BuildConfig
import io.github.magisk317.relay.common.constant.Const
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.common.constant.PrefRestoreTypeRegistry
import io.github.magisk317.relay.common.constant.PrefValueType
import io.github.magisk317.relay.common.utils.PackageUtils
import io.github.magisk317.relay.sms.SmsCodeUtils
import io.github.magisk317.relay.common.utils.StorageUtils
import io.github.magisk317.relay.common.utils.Utils
import io.github.magisk317.relay.common.utils.XLog
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import io.github.magisk317.relay.core.R
import androidx.core.os.LocaleListCompat
import io.github.magisk317.relay.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.data.repository.ConfigRepository
import io.github.magisk317.relay.data.repository.RelayRecordRepository
import io.github.magisk317.relay.data.repository.SettingsRepository
import io.github.magisk317.relay.data.backup.BackupImportResult
import io.github.magisk317.relay.data.backup.BackupManager
import io.github.magisk317.relay.data.backup.BackupRule
import io.github.magisk317.relay.data.backup.BackupSmsRecord
import io.github.magisk317.relay.data.backup.ExportResult
import io.github.magisk317.smscode.domain.model.SmsCodeMatchedRule
import io.github.magisk317.smscode.domain.model.SmsCodeMatchedRuleSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

sealed class SettingsEvent {
    data object ShowPrivacyPolicy : SettingsEvent()
    data object ShowAlipayPacket : SettingsEvent()
    data class SmsCodeTestResult(
        val code: String,
        val matchedRuleLabel: String? = null,
    ) : SettingsEvent()
    data object NavigateToRules : SettingsEvent()
    data object NavigateToRecords : SettingsEvent()
    data object StartPlayUpdate : SettingsEvent()
    data object StartGithubUpdateCheck : SettingsEvent()
    data class ShowSnackbar(val message: String) : SettingsEvent()
    data class BackupResultEvent(val success: Boolean) : SettingsEvent()
    data class RestoreResultEvent(val result: BackupImportResult) : SettingsEvent()
    data class ImportDialogConfirm(val uri: android.net.Uri) : SettingsEvent()
}

fun resolvePreferredUpdateEvent(installedFromPlay: Boolean): SettingsEvent =
    if (installedFromPlay) SettingsEvent.StartPlayUpdate else SettingsEvent.StartGithubUpdateCheck

class SettingsViewModel(
    application: Application,
    private val configRepository: ConfigRepository,
    private val recordRepository: RelayRecordRepository,
    private val settingsRepository: SettingsRepository,
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

    data class ThemeState(val mode: Int, val centerX: Float = -1f, val centerY: Float = -1f)
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
            sharedThemeState.value = ThemeState(mode)
        }
        viewModelScope.launch {
            sharedLanguageState.value = LanguageState(settingsRepository.getLanguageTag())
        }
        viewModelScope.launch {
            preferenceDataSource.syncToSharedPrefs()
        }
    }

    fun setThemeMode(mode: Int, x: Float = -1f, y: Float = -1f) {
        persistThemeMode(mode, x, y)
    }

    fun previewThemeMode(mode: Int, x: Float = -1f, y: Float = -1f) {
        sharedThemeState.value = ThemeState(mode, x, y)
    }

    fun persistThemeMode(mode: Int, x: Float = -1f, y: Float = -1f) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
            sharedThemeState.value = ThemeState(mode, x, y)
        }
    }

    fun setLanguageTag(languageTag: String) {
        persistLanguageTag(languageTag)
    }

    fun previewLanguageTag(languageTag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (languageTag.isBlank()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(languageTag)
            },
        )
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
                if (Const.ACTION_DONATE_BY_ALIPAY == extraAction) {
                    args.remove(Const.EXTRA_ACTION)
                    _eventsFlow.tryEmit(SettingsEvent.ShowAlipayPacket)
                } else if ("relay_records" == extraAction || "smscode_records" == extraAction) {
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
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
            }
            // 使用挂载了 CATEGORY_INFO 的主入口强行注册
            val mainActivity = android.content.ComponentName(context, MainActivity::class.java)
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
        val component = ComponentName(context, LauncherActivity::class.java)
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
        val component = ComponentName(context, LauncherActivity::class.java)
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

    fun performSmsCodeTest(msgBody: String) {
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    if (TextUtils.isEmpty(msgBody)) {
                        null
                    } else {
                        val keywords = settingsRepository.getVerificationSettings().relayKeywords
                        SmsCodeUtils.parseSmsCodeResultIfExists(getApplication(), msgBody, keywords)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
            val code = result?.code.orEmpty()
            val matchedRuleLabel = result?.matchedRule?.let(::formatMatchedRuleLabel)
            _eventsFlow.tryEmit(SettingsEvent.SmsCodeTestResult(code, matchedRuleLabel))
        }
    }

    private fun formatMatchedRuleLabel(matchedRule: SmsCodeMatchedRule): String {
        val app = getApplication<Application>()
        return when (matchedRule.source) {
            SmsCodeMatchedRuleSource.BUILTIN ->
                app.getString(R.string.builtin_rule_badge_format, matchedRule.ordinal)

            SmsCodeMatchedRuleSource.CUSTOM ->
                app.getString(R.string.user_rule_badge_format, matchedRule.ordinal)
        }
    }

    fun joinQQGroup() {
        PackageUtils.joinQQGroup(getApplication())?.let {
            _eventsFlow.tryEmit(SettingsEvent.ShowSnackbar(it))
        }
    }

    fun showSourceProject() {
        Utils.showWebPage(getApplication(), Const.PROJECT_SOURCE_CODE_URL)?.let {
            _eventsFlow.tryEmit(SettingsEvent.ShowSnackbar(it))
        }
    }

    fun setInternalFilesWritable() {
        StorageUtils.setFileWorldWritable(StorageUtils.getFilesDir(getApplication()), 1)
        viewModelScope.launch {
            preferenceDataSource.ensureReadable()
        }
    }

    fun requestPreferredUpdate() {
        viewModelScope.launch {
            val event = resolvePreferredUpdateEvent(PackageUtils.isInstalledFromPlay(getApplication()))
            _eventsFlow.tryEmit(event)
        }
    }

    fun handleBackupArguments(uri: android.net.Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _eventsFlow.tryEmit(SettingsEvent.ImportDialogConfirm(uri))
        }
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
                    BackupManager.exportBackup(
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
                _eventsFlow.tryEmit(SettingsEvent.BackupResultEvent(result == ExportResult.SUCCESS))
            } catch (e: Exception) {
                XLog.e("Backup failed", e)
                _eventsFlow.tryEmit(SettingsEvent.BackupResultEvent(false))
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
                    BackupManager.importRuleList(context, uri, BuildConfig.VERSION_NAME)
                }
                XLog.i(
                    "Restore import result=%s rules=%d records=%d prefs=%d warning=%s",
                    importResult.result.name,
                    importResult.rules.size,
                    importResult.records?.size ?: 0,
                    importResult.preferences?.size ?: 0,
                    importResult.warning?.name ?: "none",
                )

                if (importResult.result == io.github.magisk317.relay.data.backup.ImportResult.SUCCESS) {
                    withContext(Dispatchers.IO) {
                        if (restoreDatabase) {
                            val restored = BackupManager.restoreDatabaseFromBackup(context, uri)
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
                _eventsFlow.tryEmit(SettingsEvent.RestoreResultEvent(importResult))
            } catch (e: Exception) {
                XLog.e("Restore failed", e)
                // Return failed event
                _eventsFlow.tryEmit(
                    SettingsEvent.RestoreResultEvent(
                        BackupImportResult(io.github.magisk317.relay.data.backup.ImportResult.READ_FAILED),
                    ),
                )
            }
        }
    }

    private suspend fun restoreRules(context: Context, rules: List<BackupRule>) {
        if (rules.isEmpty()) return
        val entities = rules.map {
            io.github.magisk317.relay.data.db.entity.SmsCodeRule(it.company, it.codeKeyword, it.codeRegex)
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
            io.github.magisk317.relay.data.db.entity.SmsMsg(
                sender = it.sender,
                body = it.body,
                date = it.date,
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
        preferenceDataSource.syncToSharedPrefs()
    }

    private suspend fun ensureDataStoreLoaded(_context: android.content.Context) {
        // Trigger read to ensure in-memory cache if needed; keep no-op for now.
    }

    companion object {
        private val sharedThemeState = MutableStateFlow(ThemeState(0))
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
