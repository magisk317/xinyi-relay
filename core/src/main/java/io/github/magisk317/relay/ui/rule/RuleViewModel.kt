package io.github.magisk317.relay.ui.rule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.relay.forwarder.entity.Rule
import io.github.magisk317.relay.forwarder.entity.Sender
import io.github.magisk317.relay.forwarder.utils.SenderType
import io.github.magisk317.relay.common.constant.Const
import io.github.magisk317.relay.core.BuildConfig
import io.github.magisk317.relay.data.db.AppDatabase
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
class RuleViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val ruleDao = db.ruleDao()
    private val senderDao = db.senderDao()

    private val currentSenderId = MutableStateFlow(0L)

    val ruleList: StateFlow<List<Rule>> = currentSenderId
        .flatMapLatest { senderId ->
            if (senderId == 0L) ruleDao.observeAll() else ruleDao.observeBySender(senderId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Const.FLOW_STOP_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    val senderList: StateFlow<List<Sender>> = senderDao.getAllFlow()
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
            ruleDao.delete(rule)
        }
    }

    fun toggleRuleStatus(rule: Rule, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            rule.status = if (enabled) 1 else 0
            ruleDao.update(rule)
        }
    }

    suspend fun getRule(id: Long): Rule? = withContext(Dispatchers.IO) {
        runCatching { ruleDao.getOne(id) }.getOrNull()
    }

    suspend fun saveRuleSync(rule: Rule) {
        withContext(Dispatchers.IO) {
            if (rule.id == 0L) {
                ruleDao.insert(rule)
            } else {
                ruleDao.update(rule)
            }
        }
    }

    fun saveRule(rule: Rule) {
        viewModelScope.launch(Dispatchers.IO) {
            saveRuleSync(rule)
        }
    }
}
