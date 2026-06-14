package io.github.magisk317.relay.sender

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import io.github.magisk317.relay.sender.config.MatrixSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.SessionVerificationController
import org.matrix.rustcomponents.sdk.SessionVerificationControllerDelegate
import org.matrix.rustcomponents.sdk.SessionVerificationData
import org.matrix.rustcomponents.sdk.SessionVerificationRequestDetails
import org.matrix.rustcomponents.sdk.SyncListenerV2
import org.matrix.rustcomponents.sdk.SyncResponseV2
import org.matrix.rustcomponents.sdk.SyncSettingsV2
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.VerificationStateListener

@Suppress("TooGenericExceptionCaught")
object MatrixE2eeVerificationManager : MatrixE2eeVerification {
    private const val TAG = "MatrixE2eeVerification"
    private const val VERIFICATION_INIT_TIMEOUT_MS = 15_000L
    private const val VERIFICATION_SYNC_TIMEOUT_MS = 30_000L
    private const val VERIFICATION_KEY_QUERY_FLUSH_DELAY_MS = 3_000L
    private const val OUTGOING_SAS_START_INITIAL_DELAY_MS = 1_500L
    private const val OUTGOING_SAS_START_RETRY_DELAY_MS = 2_500L
    private const val OUTGOING_SAS_START_MAX_ATTEMPTS = 24
    private const val SYNC_BATCH_SUMMARY_LENGTH = 12
    private const val VALUE_SUMMARY_LENGTH = 16

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationMutex = Mutex()
    private val _state = MutableStateFlow(MatrixE2eeVerificationState())
    override val state: StateFlow<MatrixE2eeVerificationState> = _state.asStateFlow()

    private var client: Client? = null
    private var controller: SessionVerificationController? = null
    private var delegate: SessionVerificationControllerDelegate? = null
    private var syncHandle: TaskHandle? = null
    private var verificationStateHandle: TaskHandle? = null
    private var outgoingSasStartJob: Job? = null
    private val outgoingSasStartAttemptInProgress = AtomicBoolean(false)
    private var pendingRequest: SessionVerificationRequestDetails? = null

    override suspend fun prepare(context: Context, setting: MatrixSetting) {
        val safeSetting = SenderSettingSanitizer.sanitizeMatrixSetting(setting)
        if (safeSetting.username.isBlank() || safeSetting.password.isBlank()) {
            SLog.w(
                TAG,
                "Matrix verification prepare skipped: " +
                    "usernamePresent=${safeSetting.username.isNotBlank()} " +
                    "passwordPresent=${safeSetting.password.isNotBlank()}",
            )
            updateState {
                MatrixE2eeVerificationState(
                    status = MatrixE2eeVerificationStatus.UNSUPPORTED_AUTH,
                )
            }
            return
        }

        operationMutex.withLock {
            SLog.d(
                TAG,
                "Matrix verification prepare started: " +
                    "roomIdPresent=${safeSetting.roomId.isNotBlank()}",
            )
            updateState {
                pendingRequest = null
                it.copy(
                    status = MatrixE2eeVerificationStatus.PREPARING,
                    message = null,
                    requestUserId = "",
                    requestDeviceId = "",
                    requestDeviceDisplayName = "",
                    sasEmojis = emptyList(),
                    sasDecimals = emptyList(),
                )
            }

            try {
                val nextClient = withContext(Dispatchers.IO) {
                    MatrixE2eeUtils.getClientForVerification(context.applicationContext, safeSetting)
                }
                client = nextClient
                stopSync()
                SLog.d(TAG, "Matrix verification client acquired: ${summarizeClientSession(nextClient)}")
                waitForE2eeInitialization(nextClient)
                ensureOwnIdentityAvailable(nextClient)
                installController(nextClient)
                refreshDeviceKeysForVerification(nextClient, safeSetting)
                runInitialVerificationSync(nextClient)
                val readyState = mergeReadyState(buildReadyState(nextClient))
                if (readyState.status == MatrixE2eeVerificationStatus.VERIFIED) {
                    stopSync()
                } else {
                    startSync(nextClient)
                }
                SLog.d(
                    TAG,
                    "Matrix verification prepare completed: " +
                        "status=${readyState.status} verificationState=${readyState.verificationState} " +
                        "hasDevices=${readyState.hasDevicesToVerifyAgainst} ${summarizeStateSession(readyState)}",
                )
                updateState { readyState }
            } catch (e: Exception) {
                SLog.w(TAG, "Matrix verification prepare failed: ${e.javaClass.simpleName}: ${e.message}")
                updateFailure(e.message.orEmpty(), e)
            }
        }
    }

    override suspend fun requestVerification() {
        runControllerAction(MatrixE2eeVerificationStatus.REQUESTING, "requestDeviceVerification") {
            requestDeviceVerification()
            SLog.d(TAG, "Matrix verification request sent: ${summarizeClientSession(client)}")
            updateState {
                it.copy(
                    status = MatrixE2eeVerificationStatus.REQUEST_SENT,
                    message = null,
                    requestUserId = "",
                    requestDeviceId = "",
                    requestDeviceDisplayName = "",
                    sasEmojis = emptyList(),
                    sasDecimals = emptyList(),
                )
            }
            scheduleOutgoingSasStart()
        }
    }

    override suspend fun acceptRequest() {
        runControllerAction(MatrixE2eeVerificationStatus.ACCEPTED, "acceptVerificationRequest") {
            pendingRequest?.let { request ->
                SLog.d(TAG, "Matrix verification acknowledging request: ${summarizeRequestDetails(request)}")
                acknowledgeVerificationRequest(request.senderProfile.userId, request.flowId)
            } ?: SLog.w(TAG, "Matrix verification accept called without a pending request")
            acceptVerificationRequest()
            SLog.d(TAG, "Matrix verification request accepted: pending=${summarizePendingRequest()}")
            updateState {
                it.copy(
                    status = MatrixE2eeVerificationStatus.ACCEPTED,
                    message = null,
                )
            }
        }
    }

    override suspend fun startSas() {
        val currentStatus = _state.value.status
        if (currentStatus != MatrixE2eeVerificationStatus.ACCEPTED) {
            SLog.w(
                TAG,
                "Matrix SAS verification start skipped: " +
                    "state=$currentStatus pending=${summarizePendingRequest()}",
            )
            return
        }

        runControllerAction(null, "startSasVerification") {
            startSasVerification()
            val statusAfterStart = _state.value.status
            if (statusAfterStart == MatrixE2eeVerificationStatus.FAILED) {
                SLog.w(
                    TAG,
                    "Matrix SAS verification start returned after failure callback: " +
                        "pending=${summarizePendingRequest()}",
                )
            } else {
                SLog.d(
                    TAG,
                    "Matrix SAS verification start returned: " +
                        "state=$statusAfterStart pending=${summarizePendingRequest()}",
                )
            }
        }
    }

    override suspend fun approve() {
        runControllerAction(MatrixE2eeVerificationStatus.SAS_READY, "approveVerification") {
            approveVerification()
            SLog.d(TAG, "Matrix SAS verification approved: pending=${summarizePendingRequest()}")
        }
    }

    override suspend fun decline() {
        runControllerAction(MatrixE2eeVerificationStatus.CANCELLED, "declineVerification") {
            declineVerification()
            SLog.d(TAG, "Matrix verification declined: pending=${summarizePendingRequest()}")
            updateState {
                it.copy(
                    status = MatrixE2eeVerificationStatus.CANCELLED,
                    message = null,
                )
            }
        }
    }

    override suspend fun cancel() {
        runControllerAction(MatrixE2eeVerificationStatus.CANCELLED, "cancelVerification") {
            cancelVerification()
            SLog.d(TAG, "Matrix verification cancelled by local action: pending=${summarizePendingRequest()}")
            updateState {
                it.copy(
                    status = MatrixE2eeVerificationStatus.CANCELLED,
                    message = null,
                )
            }
        }
    }

    override fun reset() {
        SLog.d(TAG, "Matrix verification reset requested: ${summarizeClientSession(client)}")
        stopOutgoingSasStart()
        stopSync()
        stopVerificationStateListener()
        controller?.setDelegate(null)
        runCatching { controller?.close() }
        controller = null
        delegate = null
        pendingRequest = null
        updateState { MatrixE2eeVerificationState() }
    }

    private suspend fun waitForE2eeInitialization(client: Client) {
        try {
            SLog.d(TAG, "Matrix verification waiting for E2EE init: ${summarizeClientSession(client)}")
            withTimeout(VERIFICATION_INIT_TIMEOUT_MS) {
                client.encryption().waitForE2eeInitializationTasks()
            }
            SLog.d(TAG, "Matrix verification E2EE init completed")
        } catch (e: TimeoutCancellationException) {
            SLog.w(TAG, "Matrix verification E2EE init timed out: ${e.message}")
        } catch (e: Exception) {
            SLog.w(TAG, "Matrix verification E2EE init failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private suspend fun ensureOwnIdentityAvailable(client: Client) {
        try {
            val session = client.session()
            val identity = withTimeout(VERIFICATION_INIT_TIMEOUT_MS) {
                client.encryption().userIdentity(session.userId, true)
            }
            SLog.d(
                TAG,
                "Matrix verification own identity checked: " +
                    "present=${identity != null} verified=${identity?.isVerified()} " +
                    "user=${summarizeValue(session.userId)}",
            )
        } catch (e: TimeoutCancellationException) {
            SLog.w(TAG, "Matrix verification own identity check timed out: ${e.message}")
        } catch (e: Exception) {
            SLog.w(TAG, "Matrix verification own identity check failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private suspend fun refreshDeviceKeysForVerification(client: Client, setting: MatrixSetting) {
        if (setting.roomId.isBlank()) {
            SLog.d(TAG, "Matrix verification device key refresh skipped: roomId missing")
            return
        }

        val room = runCatching { client.getRoom(setting.roomId) }.getOrElse { error ->
            SLog.w(
                TAG,
                "Matrix verification room lookup failed before key refresh: " +
                    "${error.javaClass.simpleName}: ${error.message}",
            )
            null
        }
        if (room == null) {
            SLog.w(TAG, "Matrix verification device key refresh skipped: room not found")
            return
        }

        try {
            val memberCount = withTimeout(VERIFICATION_INIT_TIMEOUT_MS) {
                room.members().use { members -> members.len() }
            }
            SLog.d(TAG, "Matrix verification room members fetched for key refresh: count=$memberCount")
        } catch (e: TimeoutCancellationException) {
            SLog.w(TAG, "Matrix verification member fetch timed out before key refresh: ${e.message}")
        } catch (e: Exception) {
            SLog.w(
                TAG,
                "Matrix verification member fetch failed before key refresh: " +
                    "${e.javaClass.simpleName}: ${e.message}",
            )
        }

        try {
            SLog.d(TAG, "Matrix verification running device key refresh sync")
            val response = withTimeout(VERIFICATION_INIT_TIMEOUT_MS) {
                client.syncOnceV2(SyncSettingsV2(timeoutMs = 0u.toULong()))
            }
            SLog.d(
                TAG,
                "Matrix verification device key refresh sync completed: " +
                    "nextBatch=${summarizeBatch(response.nextBatch)}",
            )
            delay(VERIFICATION_KEY_QUERY_FLUSH_DELAY_MS)
            val encryption = client.encryption()
            val verificationState = runCatching { encryption.verificationState().name }.getOrDefault("UNKNOWN")
            val hasDevices = runCatching { encryption.hasDevicesToVerifyAgainst() }.getOrDefault(false)
            SLog.d(
                TAG,
                "Matrix verification device key refresh state: " +
                    "verificationState=$verificationState hasDevices=$hasDevices",
            )
        } catch (e: TimeoutCancellationException) {
            SLog.w(TAG, "Matrix verification device key refresh sync timed out: ${e.message}")
        } catch (e: Exception) {
            SLog.w(
                TAG,
                "Matrix verification device key refresh sync failed: " +
                    "${e.javaClass.simpleName}: ${e.message}",
            )
        }
    }

    private suspend fun runInitialVerificationSync(client: Client) {
        try {
            SLog.d(TAG, "Matrix verification running initial syncOnce with controller installed")
            withTimeout(VERIFICATION_INIT_TIMEOUT_MS) {
                client.syncOnceV2(SyncSettingsV2(timeoutMs = 0u.toULong()))
            }
            SLog.d(TAG, "Matrix verification initial sync completed")
        } catch (e: TimeoutCancellationException) {
            SLog.w(TAG, "Matrix verification initial sync timed out: ${e.message}")
        } catch (e: Exception) {
            SLog.w(TAG, "Matrix verification initial sync failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun mergeReadyState(readyState: MatrixE2eeVerificationState): MatrixE2eeVerificationState {
        val current = _state.value
        if (current.status == MatrixE2eeVerificationStatus.PREPARING) {
            return readyState
        }
        return current.copy(
            userId = readyState.userId,
            deviceId = readyState.deviceId,
            verificationState = readyState.verificationState,
            hasDevicesToVerifyAgainst = readyState.hasDevicesToVerifyAgainst,
        )
    }

    private suspend fun buildReadyState(client: Client): MatrixE2eeVerificationState {
        val session = client.session()
        val encryption = client.encryption()
        val verificationState = runCatching { encryption.verificationState().name }.getOrDefault("UNKNOWN")
        val hasDevices = runCatching { encryption.hasDevicesToVerifyAgainst() }.getOrDefault(false)
        return MatrixE2eeVerificationState(
            status = if (verificationState == "VERIFIED") {
                MatrixE2eeVerificationStatus.VERIFIED
            } else {
                MatrixE2eeVerificationStatus.READY
            },
            userId = session.userId,
            deviceId = session.deviceId,
            verificationState = verificationState,
            hasDevicesToVerifyAgainst = hasDevices,
            message = null,
        )
    }

    private suspend fun installController(client: Client) {
        if (controller != null && this.client === client) {
            SLog.d(TAG, "Matrix verification controller already installed: ${summarizeClientSession(client)}")
            installVerificationStateListener(client)
            return
        }
        stopVerificationStateListener()
        controller?.setDelegate(null)
        runCatching { controller?.close() }

        val nextController = client.getSessionVerificationController()
        val nextDelegate = createDelegate()
        nextController.setDelegate(nextDelegate)
        controller = nextController
        delegate = nextDelegate
        installVerificationStateListener(client)
        SLog.d(TAG, "Matrix verification controller installed: ${summarizeClientSession(client)}")
    }

    private fun installVerificationStateListener(client: Client) {
        val currentHandle = verificationStateHandle
        if (currentHandle != null && !runCatching { currentHandle.isFinished() }.getOrDefault(true)) {
            return
        }
        verificationStateHandle = client.encryption().verificationStateListener(
            object : VerificationStateListener {
                override fun onUpdate(state: org.matrix.rustcomponents.sdk.VerificationState) {
                    SLog.d(TAG, "Matrix verification state listener update: ${state.name}")
                    updateState {
                        it.copy(verificationState = state.name)
                    }
                }
            },
        )
        SLog.d(TAG, "Matrix verification state listener installed: ${summarizeClientSession(client)}")
    }

    private fun startSync(client: Client) {
        val currentHandle = syncHandle
        if (currentHandle != null && !runCatching { currentHandle.isFinished() }.getOrDefault(true)) {
            SLog.d(TAG, "Matrix verification sync already running: ${summarizeClientSession(client)}")
            return
        }
        syncHandle = client.syncV2(
            SyncSettingsV2(timeoutMs = VERIFICATION_SYNC_TIMEOUT_MS.toULong()),
            object : SyncListenerV2 {
                override fun onUpdate(response: SyncResponseV2) {
                    SLog.d(TAG, "Matrix verification sync update: nextBatch=${summarizeBatch(response.nextBatch)}")
                }
            },
        )
        SLog.d(TAG, "Matrix verification sync started: ${summarizeClientSession(client)}")
    }

    private fun stopSync() {
        if (syncHandle != null) {
            SLog.d(TAG, "Matrix verification sync stopping: ${summarizeClientSession(client)}")
        }
        syncHandle?.let { handle ->
            runCatching { handle.cancel() }
            runCatching { handle.close() }
        }
        syncHandle = null
    }

    private fun stopVerificationStateListener() {
        verificationStateHandle?.let { handle ->
            runCatching { handle.cancel() }
            runCatching { handle.close() }
        }
        verificationStateHandle = null
    }

    private fun scheduleOutgoingSasStart() {
        stopOutgoingSasStart()
        outgoingSasStartJob = managerScope.launch {
            repeat(OUTGOING_SAS_START_MAX_ATTEMPTS) { attempt ->
                delay(
                    if (attempt == 0) {
                        OUTGOING_SAS_START_INITIAL_DELAY_MS
                    } else {
                        OUTGOING_SAS_START_RETRY_DELAY_MS
                    },
                )
                val currentStatus = _state.value.status
                if (currentStatus != MatrixE2eeVerificationStatus.REQUEST_SENT &&
                    currentStatus != MatrixE2eeVerificationStatus.ACCEPTED
                ) {
                    SLog.d(
                        TAG,
                        "Matrix outgoing SAS auto-start stopped: " +
                            "state=$currentStatus attempt=${attempt + 1}",
                    )
                    return@launch
                }

                operationMutex.withLock {
                    val activeController = controller
                    if (activeController == null) {
                        SLog.w(TAG, "Matrix outgoing SAS auto-start skipped without controller")
                        return@launch
                    }
                    val stateBeforeStart = _state.value.status
                    if (stateBeforeStart != MatrixE2eeVerificationStatus.REQUEST_SENT &&
                        stateBeforeStart != MatrixE2eeVerificationStatus.ACCEPTED
                    ) {
                        return@withLock
                    }

                    SLog.d(
                        TAG,
                        "Matrix outgoing SAS auto-start attempt: " +
                            "attempt=${attempt + 1} state=$stateBeforeStart",
                    )
                    outgoingSasStartAttemptInProgress.set(true)
                    try {
                        activeController.startSasVerification()
                    } catch (e: Exception) {
                        SLog.d(
                            TAG,
                            "Matrix outgoing SAS auto-start attempt failed: " +
                                "${e.javaClass.simpleName}: ${e.message}",
                        )
                    } finally {
                        outgoingSasStartAttemptInProgress.set(false)
                    }
                }

                val stateAfterStart = _state.value.status
                if (stateAfterStart == MatrixE2eeVerificationStatus.SAS_STARTED ||
                    stateAfterStart == MatrixE2eeVerificationStatus.SAS_READY ||
                    stateAfterStart == MatrixE2eeVerificationStatus.VERIFIED
                ) {
                    SLog.d(
                        TAG,
                        "Matrix outgoing SAS auto-start completed: state=$stateAfterStart",
                    )
                    return@launch
                }
            }
            SLog.w(
                TAG,
                "Matrix outgoing SAS auto-start exhausted: state=${_state.value.status}",
            )
        }
    }

    private fun stopOutgoingSasStart() {
        outgoingSasStartJob?.cancel()
        outgoingSasStartJob = null
        outgoingSasStartAttemptInProgress.set(false)
    }

    private fun createDelegate(): SessionVerificationControllerDelegate {
        return object : SessionVerificationControllerDelegate {
            override fun didReceiveVerificationRequest(details: SessionVerificationRequestDetails) {
                SLog.d(TAG, "Matrix verification callback didReceiveVerificationRequest: ${summarizeRequestDetails(details)}")
                pendingRequest = details
                updateState {
                    it.copy(
                        status = MatrixE2eeVerificationStatus.REQUEST_RECEIVED,
                        requestUserId = details.senderProfile.userId,
                        requestDeviceId = details.deviceId,
                        requestDeviceDisplayName = details.deviceDisplayName.orEmpty(),
                        message = null,
                    )
                }
            }

            override fun didAcceptVerificationRequest() {
                SLog.d(TAG, "Matrix verification callback didAcceptVerificationRequest: pending=${summarizePendingRequest()}")
                updateState {
                    it.copy(
                        status = MatrixE2eeVerificationStatus.ACCEPTED,
                        message = null,
                    )
                }
            }

            override fun didStartSasVerification() {
                SLog.d(TAG, "Matrix verification callback didStartSasVerification: pending=${summarizePendingRequest()}")
                stopOutgoingSasStart()
                updateState {
                    it.copy(
                        status = MatrixE2eeVerificationStatus.SAS_STARTED,
                        message = null,
                    )
                }
            }

            override fun didReceiveVerificationData(data: SessionVerificationData) {
                val sas = summarizeSas(data)
                SLog.d(
                    TAG,
                    "Matrix verification callback didReceiveVerificationData: " +
                        "type=${data.javaClass.simpleName} decimals=${sas.decimals.size} emojis=${sas.emojis.size} " +
                        "pending=${summarizePendingRequest()}",
                )
                data.destroy()
                updateState {
                    it.copy(
                        status = MatrixE2eeVerificationStatus.SAS_READY,
                        sasEmojis = sas.emojis,
                        sasDecimals = sas.decimals,
                        message = null,
                    )
                }
            }

            override fun didFail() {
                if (outgoingSasStartAttemptInProgress.get()) {
                    SLog.d(
                        TAG,
                        "Matrix verification callback didFail suppressed during outgoing SAS auto-start: " +
                            "state=${_state.value.status} pending=${summarizePendingRequest()}",
                    )
                    return
                }
                SLog.w(TAG, "Matrix verification callback didFail: pending=${summarizePendingRequest()}")
                updateState {
                    it.copy(
                        status = MatrixE2eeVerificationStatus.FAILED,
                        message = null,
                    )
                }
            }

            override fun didCancel() {
                SLog.d(TAG, "Matrix verification callback didCancel: pending=${summarizePendingRequest()}")
                stopOutgoingSasStart()
                updateState {
                    it.copy(
                        status = MatrixE2eeVerificationStatus.CANCELLED,
                        message = null,
                    )
                }
            }

            override fun didFinish() {
                SLog.d(TAG, "Matrix verification callback didFinish: ${summarizeClientSession(client)}")
                stopOutgoingSasStart()
                stopSync()
                updateState {
                    it.copy(
                        status = MatrixE2eeVerificationStatus.VERIFIED,
                        verificationState = "VERIFIED",
                        message = null,
                    )
                }
            }
        }
    }

    private suspend fun runControllerAction(
        interimStatus: MatrixE2eeVerificationStatus?,
        actionName: String,
        block: suspend SessionVerificationController.() -> Unit,
    ) {
        operationMutex.withLock {
            val activeController = controller
            if (activeController == null) {
                SLog.w(
                    TAG,
                    "Matrix verification action skipped without controller: " +
                        "action=$actionName state=${_state.value.status}",
                )
                updateState {
                    it.copy(
                        status = MatrixE2eeVerificationStatus.NOT_PREPARED,
                        message = null,
                    )
                }
                return
            }
            SLog.d(
                TAG,
                "Matrix verification action started: action=$actionName " +
                    "state=${_state.value.status} ${summarizeClientSession(client)} " +
                    "pending=${summarizePendingRequest()}",
            )
            updateState {
                if (interimStatus == null) {
                    it.copy(message = null)
                } else {
                    it.copy(status = interimStatus, message = null)
                }
            }
            try {
                withContext(Dispatchers.IO) {
                    activeController.block()
                }
                SLog.d(
                    TAG,
                    "Matrix verification action completed: action=$actionName " +
                        "state=${_state.value.status} pending=${summarizePendingRequest()}",
                )
            } catch (e: Exception) {
                SLog.w(
                    TAG,
                    "Matrix verification action failed: action=$actionName " +
                        "error=${e.javaClass.simpleName}: ${e.message} pending=${summarizePendingRequest()}",
                )
                updateFailure(e.message.orEmpty(), e)
            }
        }
    }

    private fun updateFailure(message: String, error: Exception) {
        updateState {
            it.copy(
                status = MatrixE2eeVerificationStatus.FAILED,
                message = message.ifBlank { error.javaClass.simpleName },
            )
        }
    }

    private fun updateState(block: (MatrixE2eeVerificationState) -> MatrixE2eeVerificationState) {
        _state.value = block(_state.value)
    }

    private fun summarizeSas(data: SessionVerificationData): SasSummary {
        return when (data) {
            is SessionVerificationData.Emojis -> SasSummary(
                emojis = data.emojis.map { emoji ->
                    MatrixE2eeVerificationEmoji(
                        symbol = emoji.symbol(),
                        description = emoji.description(),
                    )
                },
                decimals = emptyList(),
            )
            is SessionVerificationData.Decimals -> SasSummary(
                emojis = emptyList(),
                decimals = data.values.map { it.toString() },
            )
        }
    }

    private fun summarizeBatch(nextBatch: String): String {
        if (nextBatch.isBlank()) return "<blank>"
        return if (nextBatch.length <= SYNC_BATCH_SUMMARY_LENGTH) {
            nextBatch
        } else {
            "${nextBatch.take(SYNC_BATCH_SUMMARY_LENGTH)}..."
        }
    }

    private fun summarizeClientSession(client: Client?): String {
        if (client == null) return "session=<none>"
        val session = runCatching { client.session() }.getOrNull() ?: return "session=<unavailable>"
        return "user=${summarizeValue(session.userId)} device=${summarizeValue(session.deviceId)}"
    }

    private fun summarizeStateSession(state: MatrixE2eeVerificationState): String {
        return "user=${summarizeValue(state.userId)} device=${summarizeValue(state.deviceId)}"
    }

    private fun summarizePendingRequest(): String {
        return pendingRequest?.let(::summarizeRequestDetails) ?: "<none>"
    }

    private fun summarizeRequestDetails(details: SessionVerificationRequestDetails): String {
        return "flow=${summarizeValue(details.flowId)} " +
            "sender=${summarizeValue(details.senderProfile.userId)} " +
            "device=${summarizeValue(details.deviceId)} " +
            "display=${summarizeValue(details.deviceDisplayName)}"
    }

    private fun summarizeValue(value: String?): String {
        val normalized = value.orEmpty()
        if (normalized.isBlank()) return "<blank>"
        return if (normalized.length <= VALUE_SUMMARY_LENGTH) {
            normalized
        } else {
            "${normalized.take(VALUE_SUMMARY_LENGTH)}...(${normalized.length})"
        }
    }

    private data class SasSummary(
        val emojis: List<MatrixE2eeVerificationEmoji>,
        val decimals: List<String>,
    )
}
