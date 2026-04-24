package io.github.magisk317.relay.ui.sender

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.relay.domain.model.ForwardFilterRule
import io.github.magisk317.relay.domain.model.ForwardCommonConfig
import io.github.magisk317.relay.domain.model.Sender
import io.github.magisk317.relay.data.db.entity.NotifyRouteRule
import io.github.magisk317.relay.domain.system.DeviceIdentityUtils
import io.github.magisk317.relay.domain.filter.ForwardFilterConst
import io.github.magisk317.relay.domain.sender.SenderSettingSanitizer
import io.github.magisk317.relay.domain.sender.SenderType
import io.github.magisk317.relay.domain.sender.SenderValidationResult
import io.github.magisk317.relay.domain.sender.SenderValidator
import io.github.magisk317.relay.domain.routing.NotifyRouteScope
import io.github.magisk317.relay.core.BuildConfig
import io.github.magisk317.relay.data.repository.ConfigRepository
import io.github.magisk317.relay.data.repository.SettingsRepository
import io.github.magisk317.relay.data.repository.SimRemarkSettingsSnapshot
import io.github.magisk317.relay.data.repository.SimRemarkSettingsUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SenderViewModel(
    application: Application,
    private val configRepository: ConfigRepository,
    private val settingsRepository: SettingsRepository,
) : AndroidViewModel(application) {

    private val _forwardCommonConfig = MutableStateFlow(
        ForwardCommonConfig(deviceName = DeviceIdentityUtils.resolveDefaultDeviceName()),
    )
    val forwardCommonConfig: StateFlow<ForwardCommonConfig> = _forwardCommonConfig.asStateFlow()
    private val _appNotifyTemplate = MutableStateFlow("")
    val appNotifyTemplate: StateFlow<String> = _appNotifyTemplate.asStateFlow()
    private val _callNotifyTemplate = MutableStateFlow("")
    val callNotifyTemplate: StateFlow<String> = _callNotifyTemplate.asStateFlow()
    private val _simRemarkSettings = MutableStateFlow(SimRemarkSettingsSnapshot("", ""))
    val simRemarkSettings: StateFlow<SimRemarkSettingsSnapshot> = _simRemarkSettings.asStateFlow()

    val senderList: StateFlow<List<Sender>> = configRepository.getAllSendersFlow()
        .map { list ->
            val sanitizedList = list.map { sender ->
                SenderSettingSanitizer.sanitizeSenderLenient(sender)
            }
            if (BuildConfig.ENABLE_SMS_CHANNEL) {
                sanitizedList
            } else {
                sanitizedList.filterNot { it.type == SenderType.SMS }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )
    private val _lastSavedStatus = MutableStateFlow<Int?>(null)
    val lastSavedStatus: StateFlow<Int?> = _lastSavedStatus.asStateFlow()

    init {
        refreshForwardCommonConfig()
        refreshAppNotifyTemplate()
        refreshCallNotifyTemplate()
        refreshSimRemarkSettings()
    }

    fun loadSenders() {
        // no-op: senderList is now reactive from Room Flow.
    }

    fun saveForwardCommonConfig(config: ForwardCommonConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.saveForwardCommonConfig(config)
            _forwardCommonConfig.value = settingsRepository.loadForwardCommonConfig()
        }
    }

    private fun refreshForwardCommonConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            _forwardCommonConfig.value = settingsRepository.loadForwardCommonConfig()
        }
    }

    fun saveAppNotifyTemplate(template: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.saveAppNotifyTemplate(template)
            _appNotifyTemplate.value = settingsRepository.loadAppNotifyTemplate()
        }
    }

    fun saveCallNotifyTemplate(template: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.saveCallNotifyTemplate(template)
            _callNotifyTemplate.value = settingsRepository.loadCallNotifyTemplate()
        }
    }

    private fun refreshAppNotifyTemplate() {
        viewModelScope.launch(Dispatchers.IO) {
            _appNotifyTemplate.value = settingsRepository.loadAppNotifyTemplate()
        }
    }

    private fun refreshCallNotifyTemplate() {
        viewModelScope.launch(Dispatchers.IO) {
            _callNotifyTemplate.value = settingsRepository.loadCallNotifyTemplate()
        }
    }

    private fun refreshSimRemarkSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            _simRemarkSettings.value = settingsRepository.getSimRemarkSettings()
        }
    }

    fun saveSimRemarkSettings(simSlot1Remark: String, simSlot2Remark: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _simRemarkSettings.value = settingsRepository.updateSimRemarkSettings(
                SimRemarkSettingsUpdate(
                    simSlot1Remark = simSlot1Remark,
                    simSlot2Remark = simSlot2Remark,
                ),
            )
        }
    }


    fun deleteSender(sender: Sender) {
        viewModelScope.launch(Dispatchers.IO) {
            configRepository.deleteSender(sender)
            configRepository.checkpoint()
        }
    }

    fun restoreSender(sender: Sender) {
        viewModelScope.launch(Dispatchers.IO) {
            val safeSender = SenderSettingSanitizer.sanitizeSenderLenient(sender)
            runCatching {
                configRepository.insertSender(safeSender)
            }.onFailure {
                configRepository.updateSender(safeSender)
            }
            configRepository.checkpoint()
        }
    }

    fun toggleSenderStatus(sender: Sender, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val newStatus = if (enabled) 1 else 0
            configRepository.updateSenderStatus(listOf(sender.id), newStatus)
            configRepository.checkpoint()
        }
    }

    fun validateSenderForEnable(sender: Sender): SenderValidationResult {
        return SenderValidator.validateForEnable(sender)
    }

    suspend fun getSender(id: Long): Sender? {
        return withContext(Dispatchers.IO) {
            configRepository.getSenderById(id)?.let { sender ->
                SenderSettingSanitizer.sanitizeSenderLenient(sender)
            }
        }
    }

    suspend fun saveSenderSync(sender: Sender) {
        withContext(Dispatchers.IO) {
            val safeSender = SenderSettingSanitizer.sanitizeSenderLenient(sender)
            if (!BuildConfig.ENABLE_SMS_CHANNEL && safeSender.type == SenderType.SMS) {
                return@withContext
            }
            if (safeSender.id == 0L) {
                configRepository.insertSender(safeSender)
            } else {
                configRepository.updateSender(safeSender)
            }
            _lastSavedStatus.value = safeSender.status
            configRepository.checkpoint()
        }
    }

    fun saveSender(sender: Sender) {
        viewModelScope.launch(Dispatchers.IO) {
            saveSenderSync(sender)
        }
    }

    fun clearLastSavedStatus() {
        _lastSavedStatus.value = null
    }

    fun senderAllowPackagesFlow(senderId: Long): Flow<Set<String>> {
        return configRepository.observeNotifyPackageNames(
            scope = NotifyRouteScope.SENDER_ALLOW_APP,
            senderId = senderId,
        )
    }

    fun senderDenyPackagesFlow(senderId: Long): Flow<Set<String>> {
        return configRepository.observeNotifyPackageNames(
            scope = NotifyRouteScope.SENDER_DENY_APP,
            senderId = senderId,
        )
    }

    fun senderNotifyScopeSummaryFlow(senderId: Long): Flow<String> {
        return combine(
            senderAllowPackagesFlow(senderId),
            senderDenyPackagesFlow(senderId),
        ) { allowPkgs, denyPkgs ->
            getApplication<Application>().getString(
                io.github.magisk317.relay.core.R.string.sender_scope_summary_format,
                allowPkgs.size,
                denyPkgs.size,
            )
        }
    }

    fun senderForwardRulesFlow(senderId: Long, msgType: String): Flow<List<ForwardFilterRule>> {
        return configRepository.observeForwardFiltersByScope(
            msgType = msgType,
            scopeType = ForwardFilterConst.SCOPE_SENDER,
            senderId = senderId,
        )
    }

    fun senderForwardFilterSummaryFlow(senderId: Long): Flow<String> {
        return combine(
            senderForwardRulesFlow(senderId, ForwardFilterConst.MSG_TYPE_SMS),
            senderForwardRulesFlow(senderId, ForwardFilterConst.MSG_TYPE_APP_NOTIFY),
            senderForwardRulesFlow(senderId, ForwardFilterConst.MSG_TYPE_CALL_NOTIFY),
        ) { smsRules, appRules, callRules ->
            val smsAllow = smsRules.count { it.policy == ForwardFilterConst.POLICY_ALLOW }
            val smsDeny = smsRules.count { it.policy == ForwardFilterConst.POLICY_DENY }
            val appAllow = appRules.count { it.policy == ForwardFilterConst.POLICY_ALLOW }
            val appDeny = appRules.count { it.policy == ForwardFilterConst.POLICY_DENY }
            val callAllow = callRules.count { it.policy == ForwardFilterConst.POLICY_ALLOW }
            val callDeny = callRules.count { it.policy == ForwardFilterConst.POLICY_DENY }
            getApplication<Application>().getString(
                io.github.magisk317.relay.core.R.string.sender_filter_summary_format,
                smsAllow,
                smsDeny,
                appAllow,
                appDeny,
                callAllow,
                callDeny,
            )
        }
    }

    fun saveSenderForwardFilterRule(
        senderId: Long,
        msgType: String,
        ruleId: Long,
        policy: String,
        matchMode: String,
        pattern: String,
        enabled: Boolean,
    ) {
        val normalizedPattern = pattern.trim()
        if (senderId <= 0L || normalizedPattern.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val rule = ForwardFilterRule(
                id = if (ruleId > 0L) ruleId else 0L,
                msgType = msgType,
                scopeType = ForwardFilterConst.SCOPE_SENDER,
                scopeKey = "",
                senderId = senderId,
                policy = policy,
                matchMode = matchMode,
                pattern = normalizedPattern,
                enabled = if (enabled) 1 else 0,
                updateTime = now,
            )
            if (rule.id <= 0L) {
                configRepository.insertForwardFilterRule(rule)
            } else {
                configRepository.updateForwardFilterRule(rule)
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

    data class SenderNotifyScopeSaveResult(
        val success: Boolean,
        val conflictPackages: Set<String> = emptySet(),
    )

    suspend fun saveSenderNotifyScopeSync(
        senderId: Long,
        allowPackages: Set<String>,
        denyPackages: Set<String>,
    ): SenderNotifyScopeSaveResult {
        val normalizedAllow = allowPackages.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val normalizedDeny = denyPackages.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val conflict = normalizedAllow.intersect(normalizedDeny)
        if (conflict.isNotEmpty()) {
            return SenderNotifyScopeSaveResult(success = false, conflictPackages = conflict)
        }
        withContext(Dispatchers.IO) {
            configRepository.deleteNotifyRouteByScopesAndSender(
                scopes = listOf(NotifyRouteScope.SENDER_ALLOW_APP, NotifyRouteScope.SENDER_DENY_APP),
                senderId = senderId,
            )
            val updateTime = System.currentTimeMillis()
            if (normalizedAllow.isNotEmpty()) {
                configRepository.insertNotifyRouteRules(
                    normalizedAllow.map { packageName ->
                        NotifyRouteRule(
                            scope = NotifyRouteScope.SENDER_ALLOW_APP,
                            packageName = packageName,
                            senderId = senderId,
                            updateTime = updateTime,
                        )
                    },
                )
            }
            if (normalizedDeny.isNotEmpty()) {
                configRepository.insertNotifyRouteRules(
                    normalizedDeny.map { packageName ->
                        NotifyRouteRule(
                            scope = NotifyRouteScope.SENDER_DENY_APP,
                            packageName = packageName,
                            senderId = senderId,
                            updateTime = updateTime,
                        )
                    },
                )
            }
        }
        return SenderNotifyScopeSaveResult(success = true)
    }
}
