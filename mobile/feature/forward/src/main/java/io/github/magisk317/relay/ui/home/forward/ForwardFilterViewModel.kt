package io.github.magisk317.relay.ui.home.forward

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.relay.engine.filter.ForwardFilterConst
import io.github.magisk317.relay.engine.model.ForwardFilterRule
import io.github.magisk317.relay.engine.service.AppConfigRepository
import io.github.magisk317.relay.engine.service.MessageRecordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ForwardFilterViewModel(
    private val configRepository: AppConfigRepository,
    private val recordRepository: MessageRecordRepository,
) : ViewModel() {

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
        return configRepository.observeForwardFiltersByScopePrefix(
            msgType = ForwardFilterConst.MSG_TYPE_APP_NOTIFY,
            scopeType = ForwardFilterConst.SCOPE_ANDROID_CHANNEL,
            scopeKeyPrefix = "${packageName.trim()}::%",
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
            val normalized = rule.copy(
                updateTime = System.currentTimeMillis(),
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
}
