package io.github.magisk317.relay.ui.record

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.relay.contract.constant.RelayAppConst as Const
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.smscode.runtime.common.utils.JsonUtils
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import io.github.magisk317.relay.engine.model.ReadRecordData
import io.github.magisk317.relay.engine.service.MessageRecordRepository
import io.github.magisk317.relay.contract.util.AppIconEncoder
import io.github.magisk317.relay.ui.common.AppIconCache
import kotlinx.serialization.Serializable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import io.github.magisk317.smscode.rule.utils.CodeRecordSimilarityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Immutable
data class CodeRecordUiState(
    val smsList: ImmutableList<SmsMsg> = persistentListOf(),
    val queryState: RecordQueryState = RecordQueryState(),
    val packageLabels: Map<String, String> = emptyMap(),
    val defaultSmsPackage: String? = null,
    val defaultDialerPackage: String? = null,
    val recordIcons: Map<String, Bitmap> = emptyMap(),
    val isLoading: Boolean = false,
)

@Immutable
data class RecordQueryState(
    val codeRecords: ImmutableList<SmsMsg> = persistentListOf(),
    val plainSmsRecords: ImmutableList<SmsMsg> = persistentListOf(),
    val appNotifyRecords: ImmutableList<SmsMsg> = persistentListOf(),
    val callNotifyRecords: ImmutableList<SmsMsg> = persistentListOf(),
) {
    fun recordsForTab(tab: Int): ImmutableList<SmsMsg> = when (tab) {
        0 -> codeRecords
        1 -> plainSmsRecords
        2 -> appNotifyRecords
        else -> callNotifyRecords
    }
}

private fun ReadRecordData.toSmsMsg(): SmsMsg = when (this) {
    is SmsMsg -> this
    else -> SmsMsg(
        id = id,
        sender = sender,
        body = body,
        date = date,
        processedTime = processedTime,
        company = company,
        smsCode = smsCode,
        packageName = packageName,
        notifyChannelId = notifyChannelId,
        simSlot = simSlot,
        subId = subId,
        contactName = contactName,
        phoneArea = phoneArea,
        forwardStatus = forwardStatus,
        forwardTarget = forwardTarget,
        forwardMessage = forwardMessage,
        forwardTime = forwardTime,
        msgType = msgType,
        callType = callType,
        sessionKey = sessionKey,
    )
}

@Serializable
private data class RecordExportPayload(
    val codeRecords: List<SmsMsg>,
    val plainSmsRecords: List<SmsMsg>,
    val appNotifyRecords: List<SmsMsg>,
    val callNotifyRecords: List<SmsMsg>,
)

private const val CODE_RECORD_DEDUP_WINDOW_MS = CodeRecordSimilarityUtils.DEFAULT_WINDOW_MS

@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> activePageFlow(
    active: Flow<Boolean>,
    source: () -> Flow<T>,
): Flow<T> = active.flatMapLatest { isActive ->
    if (isActive) source() else emptyFlow()
}

internal fun buildRecordQueryState(records: List<SmsMsg>): RecordQueryState {
    val codeRecords = records.filter {
        it.msgType == SmsMsg.MSG_TYPE_SMS && !it.smsCode.isNullOrBlank()
    }
    return RecordQueryState(
        codeRecords = deduplicateCodeRecords(codeRecords).toImmutableList(),
        plainSmsRecords = records.filter {
            it.msgType == SmsMsg.MSG_TYPE_SMS && it.smsCode.isNullOrBlank()
        }.toImmutableList(),
        appNotifyRecords = records.filter {
            it.msgType == SmsMsg.MSG_TYPE_APP_NOTIFY
        }.toImmutableList(),
        callNotifyRecords = records.filter {
            it.msgType == SmsMsg.MSG_TYPE_CALL_NOTIFY
        }.toImmutableList(),
    )
}

private fun deduplicateCodeRecords(records: List<SmsMsg>): List<SmsMsg> {
    return CodeRecordSimilarityUtils.deduplicateRecords(
        records = records,
        projection = { record ->
            CodeRecordSimilarityUtils.Projection(
                code = record.smsCode,
                body = record.body,
                company = record.company,
                sender = record.sender,
                packageName = record.packageName,
                date = record.date,
            )
        },
        windowMs = CODE_RECORD_DEDUP_WINDOW_MS,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class CodeRecordViewModel(
    application: Application,
    private val repository: MessageRecordRepository,
) : AndroidViewModel(application) {

    private val _loading = MutableStateFlow(false)
    private val _recordEnvironment = MutableStateFlow(RecordEnvironmentState())
    internal val recordEnvironment: StateFlow<RecordEnvironmentState> = _recordEnvironment.asStateFlow()
    private val _recordIcons = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    internal val recordIcons: StateFlow<Map<String, Bitmap>> = _recordIcons.asStateFlow()
    private val pageActive = MutableStateFlow(false)
    private val loadingIconPackages = ConcurrentHashMap<String, Long>()
    private val packageLabelCache = ConcurrentHashMap<String, String>()
    private val iconLoadJobs = ConcurrentHashMap.newKeySet<Job>()
    private val iconBatchSequence = AtomicLong(0L)
    private var activationGeneration = 0L
    private var refreshGeneration = 0L
    private var refreshJob: Job? = null

    private val recordsWithLabelsFlow: Flow<Triple<ImmutableList<SmsMsg>, RecordQueryState, Map<String, String>>> =
        activePageFlow(pageActive) {
            repository.queryAllFlow().mapLatest { smsList ->
                val (records, queryState) = withContext(Dispatchers.Default) {
                    val normalized = smsList.map { it.toSmsMsg() }.toImmutableList()
                    normalized to buildRecordQueryState(normalized)
                }
                Triple(records, queryState, resolvePackageLabels(records))
            }
        }

    val uiState: StateFlow<CodeRecordUiState> = recordsWithLabelsFlow
        .combine(_recordEnvironment) { records, environment ->
            records to environment
        }
        .combine(_recordIcons) { recordsAndEnvironment, recordIcons ->
            recordsAndEnvironment to recordIcons
        }
        .combine(_loading) { (recordsAndEnvironment, recordIcons), loading ->
            val (records, environment) = recordsAndEnvironment
            val (smsList, queryState, packageLabels) = records
            CodeRecordUiState(
                smsList = smsList,
                queryState = queryState,
                packageLabels = packageLabels,
                defaultSmsPackage = environment.defaultSmsPackage,
                defaultDialerPackage = environment.defaultDialerPackage,
                recordIcons = recordIcons,
                isLoading = loading,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Const.FLOW_STOP_TIMEOUT_MS),
            initialValue = CodeRecordUiState(isLoading = true),
        )

    fun setPageActive(active: Boolean) {
        if (pageActive.value == active) return
        pageActive.value = active
        activationGeneration++
        refreshGeneration++
        if (active) {
            refreshRecordEnvironment(showLoading = false)
        } else {
            refreshJob?.cancel()
            refreshJob = null
            iconLoadJobs.forEach { it.cancel() }
            iconLoadJobs.clear()
            loadingIconPackages.clear()
            _loading.value = false
        }
    }

    fun loadData() {
        // Active pages are automatically loaded through queryAllFlow().
    }

    fun refreshData() {
        if (!pageActive.value) return
        refreshRecordEnvironment(showLoading = true)
    }

    fun preloadRecordIcons(packageNames: Collection<String?>, sizePx: Int) {
        if (!pageActive.value) return
        val batchId = iconBatchSequence.incrementAndGet()
        val normalizedPackages = packageNames
            .asSequence()
            .mapNotNull { it?.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .filter { packageName ->
                !_recordIcons.value.containsKey(packageName) &&
                    loadingIconPackages.putIfAbsent(packageName, batchId) == null
            }
            .toList()
        if (normalizedPackages.isEmpty()) return

        val generation = activationGeneration
        val job = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            val context = getApplication<Application>().applicationContext
            val loaded = mutableMapOf<String, Bitmap>()
            try {
                for (packageName in normalizedPackages) {
                    currentCoroutineContext().ensureActive()
                    val bitmap = AppIconCache.load(
                        context = context,
                        packageName = packageName,
                        sizePx = sizePx,
                    )
                    currentCoroutineContext().ensureActive()
                    if (bitmap != null) {
                        loaded[packageName] = bitmap
                    }
                }
            } finally {
                normalizedPackages.forEach { packageName ->
                    loadingIconPackages.remove(packageName, batchId)
                }
            }
            if (pageActive.value && generation == activationGeneration && loaded.isNotEmpty()) {
                _recordIcons.update { current -> current + loaded }
            }
        }
        iconLoadJobs += job
        job.invokeOnCompletion { iconLoadJobs -= job }
        job.start()
    }

    fun removeSmsMsg(smsMsgList: List<ReadRecordData>) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.removeList(smsMsgList)
                }
            } catch (ignored: Throwable) {
                XLog.e("Error occurs when remove SMS records", ignored)
            }
        }
    }

    fun restoreSmsMsgList(smsMsgList: List<ReadRecordData>) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.insertList(smsMsgList)
                }
            } catch (ignored: Throwable) {
                XLog.e("Error occurs when restore SMS records", ignored)
            }
        }
    }

    fun refund(context: Context, smsMsg: SmsMsg) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val intent = Intent(PrefConst.ACTION_FORWARD_SMS).apply {
                        setClassName(context.packageName, "io.github.magisk317.relay.platform.ipc.ForwardReceiver")
                        putExtra("sender", smsMsg.sender ?: "")
                        putExtra("body", smsMsg.body ?: "")
                        putExtra("date", smsMsg.date)
                        putExtra("company", smsMsg.company ?: "")
                        putExtra("smsCode", smsMsg.smsCode ?: "")
                        putExtra("packageName", smsMsg.packageName ?: "")
                        putExtra("notify_channel_id", smsMsg.notifyChannelId)
                        putExtra("msgType", when (smsMsg.msgType) {
                            SmsMsg.MSG_TYPE_APP_NOTIFY -> "app_notify"
                            SmsMsg.MSG_TYPE_CALL_NOTIFY -> "call_notify"
                            else -> "sms"
                        })
                        putExtra("forward_source", "manual_refund")
                        putExtra("call_type", smsMsg.callType)
                    }
                    context.sendBroadcast(intent)
                }
                XLog.i("Re-forward triggered for record id=${smsMsg.id}")
            } catch (e: Throwable) {
                XLog.e("Re-forward failed", e)
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

    private suspend fun resolvePackageLabels(records: List<SmsMsg>): Map<String, String> = withContext(Dispatchers.IO) {
        val packages = records
            .mapNotNull { it.packageName?.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (packages.isEmpty()) return@withContext emptyMap()

        val pm = getApplication<Application>().packageManager
        buildMap {
            for (pkg in packages) {
                currentCoroutineContext().ensureActive()
                val label = packageLabelCache[pkg] ?: runCatching {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString().ifBlank { pkg }
                }.getOrDefault(pkg).also { resolved ->
                    packageLabelCache[pkg] = resolved
                }
                currentCoroutineContext().ensureActive()
                put(pkg, label)
            }
        }
    }

    private fun refreshRecordEnvironment(showLoading: Boolean) {
        refreshJob?.cancel()
        val generation = ++refreshGeneration
        if (showLoading) _loading.value = true
        refreshJob = viewModelScope.launch {
            try {
                val environment = withContext(Dispatchers.IO) {
                    resolveRecordEnvironment()
                }
                if (pageActive.value && generation == refreshGeneration) {
                    _recordEnvironment.value = environment
                }
            } finally {
                if (generation == refreshGeneration) {
                    _loading.value = false
                }
            }
        }
    }

    private fun resolveRecordEnvironment(): RecordEnvironmentState {
        val context = getApplication<Application>()
        return RecordEnvironmentState(
            defaultSmsPackage = AppIconEncoder.resolveDefaultSmsPackage(context),
            defaultDialerPackage = AppIconEncoder.resolveDefaultDialerPackage(context),
        )
    }
}

internal data class RecordEnvironmentState(
    val defaultSmsPackage: String? = null,
    val defaultDialerPackage: String? = null,
)
