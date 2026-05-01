package io.github.magisk317.relay.ui.rule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.relay.engine.model.Rule
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.contract.constant.RelayAppConst as Const
import io.github.magisk317.relay.mobileui.BuildConfig
import io.github.magisk317.relay.data.repository.ConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class RuleViewModel(
    application: Application,
    private val configRepository: ConfigRepository,
) : AndroidViewModel(application) {

    private val currentSenderId = MutableStateFlow(0L)

    val ruleList: StateFlow<List<Rule>> = currentSenderId
        .flatMapLatest { senderId ->
            if (senderId == 0L) configRepository.getAllRulesFlow() else configRepository.observeRulesBySender(senderId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Const.FLOW_STOP_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    val senderList: StateFlow<List<Sender>> = configRepository.getAllSendersFlow()
        .map { list ->
            if (BuildConfig.ENABLE_SMS_CHANNEL) {
                list
            } else {
                list.filterNot { it.type == SenderType.SMS }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Const.FLOW_STOP_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    fun loadRules(senderId: Long = 0L) {
        currentSenderId.value = senderId
    }

    fun deleteRule(rule: Rule) {
        viewModelScope.launch(Dispatchers.IO) {
            configRepository.deleteRule(rule)
        }
    }

    fun toggleRuleStatus(rule: Rule, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            rule.status = if (enabled) 1 else 0
            configRepository.updateRule(rule)
        }
    }

    suspend fun getRule(id: Long): Rule? = withContext(Dispatchers.IO) {
        runCatching { configRepository.getRuleById(id) }.getOrNull()
    }

    suspend fun saveRuleSync(rule: Rule) {
        withContext(Dispatchers.IO) {
            if (rule.id == 0L) {
                configRepository.insertRule(rule)
            } else {
                configRepository.updateRule(rule)
            }
        }
    }

    fun saveRule(rule: Rule) {
        viewModelScope.launch(Dispatchers.IO) {
            saveRuleSync(rule)
        }
    }
}
