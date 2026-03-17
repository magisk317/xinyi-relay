package io.github.magisk317.relay.ui.record

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.relay.common.constant.Const
import io.github.magisk317.relay.common.utils.JsonUtils
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.data.repository.RelayRecordRepository
import kotlinx.serialization.Serializable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

@Immutable
data class CodeRecordUiState(val smsList: ImmutableList<SmsMsg> = persistentListOf(), val isLoading: Boolean = false)

@Serializable
private data class RecordExportPayload(
    val codeRecords: List<SmsMsg>,
    val plainSmsRecords: List<SmsMsg>,
    val appNotifyRecords: List<SmsMsg>,
    val callNotifyRecords: List<SmsMsg>,
)

class CodeRecordViewModel(
    application: Application,
    private val repository: RelayRecordRepository,
) : AndroidViewModel(application) {

    private val _loading = MutableStateFlow(false)

    val uiState: StateFlow<CodeRecordUiState> = repository.queryAllFlow()
        .combine(_loading) { smsList, loading ->
            CodeRecordUiState(smsList.toImmutableList(), loading)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Const.FLOW_STOP_TIMEOUT_MS),
            initialValue = CodeRecordUiState(isLoading = true),
        )

    fun loadData() {
        // Data is automatically loaded via queryAllFlow() in uiState
    }

    fun refreshData() {
        viewModelScope.launch {
            _loading.value = true
            try {
                withContext(Dispatchers.IO) {
                    repository.queryAll()
                }
            } finally {
                _loading.value = false
            }
        }
    }

    fun removeSmsMsg(smsMsgList: List<SmsMsg>) {
        viewModelScope.launch {
            try {
                repository.removeList(smsMsgList)
            } catch (ignored: Throwable) {
                XLog.e("Error occurs when remove SMS records", ignored)
            }
        }
    }

    fun restoreSmsMsgList(smsMsgList: List<SmsMsg>) {
        viewModelScope.launch {
            try {
                repository.insertList(smsMsgList)
            } catch (ignored: Throwable) {
                XLog.e("Error occurs when restore SMS records", ignored)
            }
        }
    }

    fun exportRecords(context: Context, uri: Uri, currentTab: Int, exportAllTabs: Boolean) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val allRecords = uiState.value.smsList.toList()
                val codeRecords = allRecords.filter {
                    it.msgType == SmsMsg.MSG_TYPE_SMS && !it.smsCode.isNullOrBlank()
                }
                val plainSmsRecords = allRecords.filter {
                    it.msgType == SmsMsg.MSG_TYPE_SMS && it.smsCode.isNullOrBlank()
                }
                val appNotifyRecords = allRecords.filter {
                    it.msgType == SmsMsg.MSG_TYPE_APP_NOTIFY
                }
                val callNotifyRecords = allRecords.filter {
                    it.msgType == SmsMsg.MSG_TYPE_CALL_NOTIFY
                }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        OutputStreamWriter(os, StandardCharsets.UTF_8).use { osw ->
                            if (exportAllTabs) {
                                JsonUtils.toJson(
                                    RecordExportPayload(
                                        codeRecords = codeRecords,
                                        plainSmsRecords = plainSmsRecords,
                                        appNotifyRecords = appNotifyRecords,
                                        callNotifyRecords = callNotifyRecords,
                                    ),
                                    osw,
                                    true,
                                )
                            } else {
                                val currentRecords = when (currentTab) {
                                    0 -> codeRecords
                                    1 -> plainSmsRecords
                                    2 -> appNotifyRecords
                                    else -> callNotifyRecords
                                }
                                JsonUtils.toJson(currentRecords, osw, true)
                            }
                        }
                    }
                }
            } catch (ignored: Throwable) {
                XLog.e("Export records failed", ignored)
            } finally {
                _loading.value = false
            }
        }
    }
}
