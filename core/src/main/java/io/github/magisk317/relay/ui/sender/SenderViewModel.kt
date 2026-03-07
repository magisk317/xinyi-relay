package io.github.magisk317.relay.ui.sender

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.relay.data.db.AppDatabase
import io.github.magisk317.relay.forwarder.entity.ForwardFilterRule
import io.github.magisk317.relay.forwarder.entity.ForwardCommonConfig
import io.github.magisk317.relay.forwarder.entity.Sender
import io.github.magisk317.relay.data.db.entity.NotifyRouteRule
import io.github.magisk317.relay.forwarder.utils.DeviceIdentityUtils
import io.github.magisk317.relay.forwarder.utils.ForwardCommonConfigStore
import io.github.magisk317.relay.forwarder.filter.ForwardFilterConst
import io.github.magisk317.relay.forwarder.utils.SenderSettingSanitizer
import io.github.magisk317.relay.forwarder.utils.SenderType
import io.github.magisk317.relay.forwarder.utils.SenderValidationResult
import io.github.magisk317.relay.forwarder.utils.SenderValidator
import io.github.magisk317.relay.forwarder.routing.NotifyRouteScope
import io.github.magisk317.relay.core.BuildConfig
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

class SenderViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val senderDao = db.senderDao()
    private val notifyRouteDao = db.notifyRouteRuleDao()
    private val forwardFilterDao = db.forwardFilterRuleDao()

    private val _forwardCommonConfig = MutableStateFlow(
        ForwardCommonConfig(deviceName = DeviceIdentityUtils.resolveDefaultDeviceName()),
    )
    val forwardCommonConfig: StateFlow<ForwardCommonConfig> = _forwardCommonConfig.asStateFlow()
    private val _appNotifyTemplate = MutableStateFlow("")
    val appNotifyTemplate: StateFlow<String> = _appNotifyTemplate.asStateFlow()
    private val _callNotifyTemplate = MutableStateFlow("")
    val callNotifyTemplate: StateFlow<String> = _callNotifyTemplate.asStateFlow()

    val senderList: StateFlow<List<Sender>> = senderDao.getAllFlow()
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
    }

    fun loadSenders() {
        // no-op: senderList is now reactive from Room Flow.
    }

    fun saveForwardCommonConfig(config: ForwardCommonConfig) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            ForwardCommonConfigStore.save(context, config)
            _forwardCommonConfig.value = ForwardCommonConfigStore.load(context)
        }
    }

    private fun refreshForwardCommonConfig() {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _forwardCommonConfig.value = ForwardCommonConfigStore.load(context)
        }
    }

    fun saveAppNotifyTemplate(template: String) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            ForwardCommonConfigStore.saveAppNotifyTemplate(context, template)
            _appNotifyTemplate.value = ForwardCommonConfigStore.loadAppNotifyTemplate(context)
        }
    }

    fun saveCallNotifyTemplate(template: String) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            ForwardCommonConfigStore.saveCallNotifyTemplate(context, template)
            _callNotifyTemplate.value = ForwardCommonConfigStore.loadCallNotifyTemplate(context)
        }
    }

    private fun refreshAppNotifyTemplate() {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _appNotifyTemplate.value = ForwardCommonConfigStore.loadAppNotifyTemplate(context)
        }
    }

    private fun refreshCallNotifyTemplate() {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _callNotifyTemplate.value = ForwardCommonConfigStore.loadCallNotifyTemplate(context)
        }
    }

    /**
     * Force WAL checkpoint so that data written by the App UI process is flushed
     * to the main DB file and becomes visible to the Hook process (com.android.phone).
     */
    private fun walCheckpoint() {
        try {
            db.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .close()
        } catch (_: Throwable) {
            // Best-effort: if checkpoint fails, enableMultiInstanceInvalidation
            // should still handle cross-process visibility.
        }
    }

    fun deleteSender(sender: Sender) {
        viewModelScope.launch(Dispatchers.IO) {
            senderDao.delete(sender)
            walCheckpoint()
        }
    }

    fun restoreSender(sender: Sender) {
        viewModelScope.launch(Dispatchers.IO) {
            val safeSender = SenderSettingSanitizer.sanitizeSenderLenient(sender)
            runCatching {
                senderDao.insert(safeSender)
            }.onFailure {
                senderDao.update(safeSender)
            }
            walCheckpoint()
        }
    }

    fun toggleSenderStatus(sender: Sender, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val newStatus = if (enabled) 1 else 0
            senderDao.updateStatusByIds(listOf(sender.id), newStatus)
            walCheckpoint()
        }
    }

    fun validateSenderForEnable(sender: Sender): SenderValidationResult {
        return SenderValidator.validateForEnable(sender)
    }

    suspend fun getSender(id: Long): Sender? {
        return withContext(Dispatchers.IO) {
            senderDao.getOne(id)?.let { sender ->
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
                senderDao.insert(safeSender)
            } else {
                senderDao.update(safeSender)
            }
            _lastSavedStatus.value = safeSender.status
            walCheckpoint()
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
        return notifyRouteDao.observePackageNamesByScopeAndSender(
            scope = NotifyRouteScope.SENDER_ALLOW_APP,
            senderId = senderId,
        ).map { it.toSet() }
    }

    fun senderDenyPackagesFlow(senderId: Long): Flow<Set<String>> {
        return notifyRouteDao.observePackageNamesByScopeAndSender(
            scope = NotifyRouteScope.SENDER_DENY_APP,
            senderId = senderId,
        ).map { it.toSet() }
    }

    fun senderNotifyScopeSummaryFlow(senderId: Long): Flow<String> {
        return combine(
            senderAllowPackagesFlow(senderId),
            senderDenyPackagesFlow(senderId),
        ) { allowPkgs, denyPkgs ->
            "白名单${allowPkgs.size} / 黑名单${denyPkgs.size}"
        }
    }

    fun senderForwardRulesFlow(senderId: Long, msgType: String): Flow<List<ForwardFilterRule>> {
        return forwardFilterDao.observeByScope(
            msgType = msgType,
            scopeType = ForwardFilterConst.SCOPE_SENDER,
            senderId = senderId,
        )
    }

    fun senderForwardFilterSummaryFlow(senderId: Long): Flow<String> {
        return combine(
            senderForwardRulesFlow(senderId, ForwardFilterConst.MSG_TYPE_SMS),
            senderForwardRulesFlow(senderId, ForwardFilterConst.MSG_TYPE_APP_NOTIFY),
        ) { smsRules, appRules ->
            val smsAllow = smsRules.count { it.policy == ForwardFilterConst.POLICY_ALLOW }
            val smsDeny = smsRules.count { it.policy == ForwardFilterConst.POLICY_DENY }
            val appAllow = appRules.count { it.policy == ForwardFilterConst.POLICY_ALLOW }
            val appDeny = appRules.count { it.policy == ForwardFilterConst.POLICY_DENY }
            "短信 白$smsAllow/黑$smsDeny · 通知 白$appAllow/黑$appDeny"
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
                forwardFilterDao.insert(rule)
            } else {
                forwardFilterDao.update(rule)
            }
        }
    }

    fun deleteForwardFilterRule(id: Long) {
        if (id <= 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            forwardFilterDao.deleteById(id)
        }
    }

    fun setForwardFilterRuleEnabled(id: Long, enabled: Boolean) {
        if (id <= 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            forwardFilterDao.updateEnabledById(
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
            notifyRouteDao.deleteByScopesAndSender(
                scopes = listOf(NotifyRouteScope.SENDER_ALLOW_APP, NotifyRouteScope.SENDER_DENY_APP),
                senderId = senderId,
            )
            val updateTime = System.currentTimeMillis()
            if (normalizedAllow.isNotEmpty()) {
                notifyRouteDao.insertAll(
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
                notifyRouteDao.insertAll(
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
