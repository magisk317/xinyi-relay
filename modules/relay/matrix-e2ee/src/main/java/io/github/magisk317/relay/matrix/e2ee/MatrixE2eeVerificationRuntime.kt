package io.github.magisk317.relay.matrix.e2ee

import android.content.Context
import io.github.magisk317.relay.sender.MatrixE2eeCancelInfo
import io.github.magisk317.relay.sender.MatrixE2eeVerification
import io.github.magisk317.relay.sender.MatrixE2eeVerificationEmoji
import io.github.magisk317.relay.sender.MatrixE2eeVerificationState
import io.github.magisk317.relay.sender.MatrixE2eeVerificationStatus
import io.github.magisk317.relay.sender.SenderSettingSanitizer
import java.util.concurrent.atomic.AtomicBoolean
import io.github.magisk317.relay.sender.config.MatrixSetting
import kotlinx.coroutines.CoroutineScope
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
import io.github.magisk317.xposed.logging.MagiskOtel

@Suppress("TooGenericExceptionCaught")
object MatrixE2eeVerificationRuntime : MatrixE2eeVerification {
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
    private var pendingRequestAckJob: Job? = null
    private var pendingRequestAckFlowId: String? = null
    private var acknowledgedRequestFlowId: String? = null
    private var lastContext: Context? = null
    private var lastSetting: MatrixSetting? = null

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
            lastContext = context.applicationContext
            lastSetting = safeSetting
            SLog.d(
                TAG,
                "Matrix verification prepare started: " +
                    "roomIdPresent=${safeSetting.roomId.isNotBlank()}",
            )
            resetPendingRequestAcknowledgement(cancelActiveJob = true)
            updateState { current ->
                val nextStatus = if (current.status == MatrixE2eeVerificationStatus.VERIFIED || current.verificationState == "VERIFIED") {
                    MatrixE2eeVerificationStatus.VERIFIED
                } else {
                    MatrixE2eeVerificationStatus.PREPARING
                }
                current.copy(
                    status = nextStatus,
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
                    MatrixE2eeRuntime.getClientForVerification(context.applicationContext, safeSetting)
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
                logLocalSnapshot("prepare-completed")
                logSdkSnapshot("prepare-completed")
            } catch (e: Exception) {
                if (MatrixE2eeRuntime.isAuthError(e)) {
                    SLog.w(TAG, "Matrix verification prepare auth expired, auto-refreshing: ${e.message}")
                    try {
                        MatrixE2eeRuntime.invalidateClientAndSession(context.applicationContext, safeSetting)
                        val refreshedClient = withContext(Dispatchers.IO) {
                            MatrixE2eeRuntime.getClientForVerification(context.applicationContext, safeSetting, forceRefresh = true)
                        }
                        client = refreshedClient
                        stopSync()
                        waitForE2eeInitialization(refreshedClient)
                        ensureOwnIdentityAvailable(refreshedClient)
                        installController(refreshedClient)
                        refreshDeviceKeysForVerification(refreshedClient, safeSetting)
                        runInitialVerificationSync(refreshedClient)
                        val readyState = mergeReadyState(buildReadyState(refreshedClient))
                        if (readyState.status == MatrixE2eeVerificationStatus.VERIFIED) {
                            stopSync()
                        } else {
                            startSync(refreshedClient)
                        }
                        updateState { readyState }
                        logLocalSnapshot("prepare-completed-after-auth-refresh")
                        return@withLock
                    } catch (refreshErr: Exception) {
                        SLog.w(TAG, "Matrix verification prepare retry after auth error failed: ${refreshErr.message}")
                    }
                }
                SLog.w(TAG, "Matrix verification prepare failed: ${e.javaClass.simpleName}: ${e.message}")
                updateFailure(e.message.orEmpty(), e)
                logLocalSnapshot("prepare-failed")
                logSdkSnapshot("prepare-failed")
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
            logLocalSnapshot("requestVerification-sent")
            logSdkSnapshot("requestVerification-sent")
        }
    }

    override suspend fun acceptRequest() {
        pendingRequest?.let { request ->
            awaitPendingRequestAcknowledgement(
                details = request,
                trigger = "acceptVerificationRequest",
            )
        } ?: SLog.w(TAG, "Matrix verification accept called without a pending request")

        runControllerAction(MatrixE2eeVerificationStatus.ACCEPTED, "acceptVerificationRequest") {
            acceptVerificationRequest()
            SLog.d(TAG, "Matrix verification request accepted: pending=${summarizePendingRequest()}")
            updateState {
                it.copy(
                    status = MatrixE2eeVerificationStatus.ACCEPTED,
                    message = null,
                )
            }
            logLocalSnapshot("acceptVerificationRequest-completed")
            logSdkSnapshot("acceptVerificationRequest-completed")
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
            logLocalSnapshot("startSasVerification-returned")
            logSdkSnapshot("startSasVerification-returned")
        }
    }

    override suspend fun approve() {
        runControllerAction(MatrixE2eeVerificationStatus.SAS_READY, "approveVerification") {
            approveVerification()
            SLog.d(TAG, "Matrix SAS verification approved: pending=${summarizePendingRequest()}")
            logLocalSnapshot("approveVerification-completed")
            logSdkSnapshot("approveVerification-completed")
        }
    }

    override suspend fun decline() {
        runControllerAction(MatrixE2eeVerificationStatus.CANCELLED, "declineVerification") {
            declineVerification()
            val sdkCancelInfo = controller?.requestCancelInfo() ?: controller?.sasCancelInfo()
            SLog.d(TAG, "Matrix verification declined: pending=${summarizePendingRequest()}")
            updateState {
                it.copy(
                    status = MatrixE2eeVerificationStatus.CANCELLED,
                    message = null,
                    cancelInfo = MatrixE2eeCancelInfo(
                        reason = sdkCancelInfo?.reason.orEmpty(),
                        code = sdkCancelInfo?.cancelCode.orEmpty(),
                        cancelledByUs = sdkCancelInfo?.cancelledByUs ?: true,
                    ),
                )
            }
            logLocalSnapshot("declineVerification-completed")
            logSdkSnapshot("declineVerification-completed")
        }
    }

    override suspend fun cancel() {
        runControllerAction(MatrixE2eeVerificationStatus.CANCELLED, "cancelVerification") {
            cancelVerification()
            val sdkCancelInfo = controller?.requestCancelInfo() ?: controller?.sasCancelInfo()
            SLog.d(TAG, "Matrix verification cancelled by local action: pending=${summarizePendingRequest()}")
            updateState {
                it.copy(
                    status = MatrixE2eeVerificationStatus.CANCELLED,
                    message = null,
                    cancelInfo = MatrixE2eeCancelInfo(
                        reason = sdkCancelInfo?.reason.orEmpty(),
                        code = sdkCancelInfo?.cancelCode.orEmpty(),
                        cancelledByUs = sdkCancelInfo?.cancelledByUs ?: true,
                    ),
                )
            }
            logLocalSnapshot("cancelVerification-completed")
            logSdkSnapshot("cancelVerification-completed")
        }
    }

    override suspend fun revokeDevice(context: Context, setting: MatrixSetting) {
        SLog.d(TAG, "Matrix verification revoke device requested: ${summarizeClientSession(client)}")
        val activeClient = client
        if (activeClient != null) {
            try {
                withContext(Dispatchers.IO) {
                    activeClient.logout()
                }
                SLog.d(TAG, "Matrix verification logout completed")
            } catch (e: Exception) {
                SLog.w(TAG, "Matrix verification logout failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        } else {
            SLog.d(TAG, "Matrix verification revoke skipped without client")
        }

        // Force clear the DB even if logout failed
        try {
            MatrixE2eeRuntime.forceClearDeviceStore(context, setting)
            SLog.d(TAG, "Matrix verification device store forcefully cleared")
        } catch (e: Exception) {
            SLog.w(TAG, "Failed to forcefully clear Matrix device store: ${e.message}")
        }

        reset()
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
        resetPendingRequestAcknowledgement(cancelActiveJob = true)
        updateState { MatrixE2eeVerificationState() }
        logLocalSnapshot("reset")
    }

    override fun stop() {
        SLog.d(TAG, "Matrix verification stop requested: ${summarizeClientSession(client)}")
        stopOutgoingSasStart()
        stopSync()
        stopVerificationStateListener()
        controller?.setDelegate(null)
        runCatching { controller?.close() }
        controller = null
        delegate = null
        resetPendingRequestAcknowledgement(cancelActiveJob = true)
        // Keep verified state if it was verified, so returning to the page doesn't flash unverified
        updateState { current ->
            if (current.status == MatrixE2eeVerificationStatus.VERIFIED || current.verificationState == "VERIFIED") {
                current.copy(
                    status = MatrixE2eeVerificationStatus.VERIFIED,
                    verificationState = "VERIFIED",
                    message = null,
                )
            } else {
                current.copy(
                    status = MatrixE2eeVerificationStatus.READY,
                    message = null,
                )
            }
        }
        logLocalSnapshot("stop")
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
        val isIdentityVerified = runCatching {
            encryption.userIdentity(session.userId, true)?.isVerified() == true
        }.getOrDefault(false)
        val hasDevices = runCatching { encryption.hasDevicesToVerifyAgainst() }.getOrDefault(false)
        val currentStatus = _state.value.status
        val currentVerificationState = _state.value.verificationState
        val isVerified = verificationState == "VERIFIED" || isIdentityVerified ||
            currentStatus == MatrixE2eeVerificationStatus.VERIFIED || currentVerificationState == "VERIFIED"

        return MatrixE2eeVerificationState(
            status = if (isVerified) {
                MatrixE2eeVerificationStatus.VERIFIED
            } else {
                MatrixE2eeVerificationStatus.READY
            },
            userId = session.userId,
            deviceId = session.deviceId,
            verificationState = if (isVerified) "VERIFIED" else verificationState,
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
                override fun onUpdate(status: org.matrix.rustcomponents.sdk.VerificationState) {
                    SLog.d(TAG, "Matrix verification state listener update: ${status.name}")
                    updateState { current ->
                        val nextStatus = if (status == org.matrix.rustcomponents.sdk.VerificationState.VERIFIED) {
                            MatrixE2eeVerificationStatus.VERIFIED
                        } else {
                            current.status
                        }
                        current.copy(
                            verificationState = status.name,
                            status = nextStatus,
                        )
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

    private fun resetPendingRequestAcknowledgement(cancelActiveJob: Boolean) {
        if (cancelActiveJob) {
            pendingRequestAckJob?.cancel()
        }
        pendingRequestAckJob = null
        pendingRequestAckFlowId = null
        acknowledgedRequestFlowId = null
        pendingRequest = null
    }

    private fun schedulePendingRequestAcknowledgement(
        details: SessionVerificationRequestDetails,
        trigger: String,
    ) {
        val flowId = details.flowId
        if (acknowledgedRequestFlowId == flowId) {
            SLog.d(
                TAG,
                "Matrix verification request acknowledgement already completed: " +
                    "trigger=$trigger flow=${summarizeValue(flowId)}",
            )
            return
        }

        val activeJob = pendingRequestAckJob
        if (pendingRequestAckFlowId == flowId && activeJob != null &&
            !runCatching { activeJob.isCompleted }.getOrDefault(false)
        ) {
            SLog.d(
                TAG,
                "Matrix verification request acknowledgement already pending: " +
                    "trigger=$trigger flow=${summarizeValue(flowId)}",
            )
            return
        }

        if (pendingRequestAckFlowId != null && pendingRequestAckFlowId != flowId) {
            pendingRequestAckJob?.cancel()
        }

        pendingRequestAckFlowId = flowId
        val nextJob = managerScope.launch {
            acknowledgePendingRequest(details, trigger)
        }
        pendingRequestAckJob = nextJob
        nextJob.invokeOnCompletion {
            if (pendingRequestAckJob === nextJob) {
                pendingRequestAckJob = null
            }
        }
    }

    private suspend fun awaitPendingRequestAcknowledgement(
        details: SessionVerificationRequestDetails,
        trigger: String,
    ) {
        val flowId = details.flowId
        pendingRequestAckJob
            ?.takeIf { pendingRequestAckFlowId == flowId }
            ?.let { job ->
                SLog.d(
                    TAG,
                    "Matrix verification waiting for request acknowledgement: " +
                        "trigger=$trigger flow=${summarizeValue(flowId)}",
                )
                job.join()
            }

        if (acknowledgedRequestFlowId == flowId) {
            SLog.d(
                TAG,
                "Matrix verification request acknowledgement confirmed before accept: " +
                    "trigger=$trigger flow=${summarizeValue(flowId)}",
            )
            return
        }

        SLog.w(
            TAG,
            "Matrix verification request acknowledgement missing before accept, retrying inline: " +
                "trigger=$trigger request=${summarizeRequestDetails(details)}",
        )
        acknowledgePendingRequest(details, "$trigger-inline")
    }

    private suspend fun acknowledgePendingRequest(
        details: SessionVerificationRequestDetails,
        trigger: String,
    ) {
        operationMutex.withLock {
            val activeController = controller
            if (activeController == null) {
                SLog.w(
                    TAG,
                    "Matrix verification request acknowledgement skipped without controller: " +
                        "trigger=$trigger request=${summarizeRequestDetails(details)}",
                )
                return
            }
            if (pendingRequest?.flowId != details.flowId) {
                SLog.d(
                    TAG,
                    "Matrix verification request acknowledgement skipped for stale request: " +
                        "trigger=$trigger request=${summarizeRequestDetails(details)} " +
                        "pending=${summarizePendingRequest()}",
                )
                return
            }
            if (acknowledgedRequestFlowId == details.flowId) {
                SLog.d(
                    TAG,
                    "Matrix verification request acknowledgement already completed in action: " +
                        "trigger=$trigger flow=${summarizeValue(details.flowId)}",
                )
                return
            }

            SLog.d(
                TAG,
                "Matrix verification request acknowledgement started: " +
                    "trigger=$trigger request=${summarizeRequestDetails(details)}",
            )
            logLocalSnapshot("acknowledgeVerificationRequest-start:$trigger")
            try {
                withContext(Dispatchers.IO) {
                    activeController.acknowledgeVerificationRequest(
                        details.senderProfile.userId,
                        details.flowId,
                    )
                }
                acknowledgedRequestFlowId = details.flowId
                SLog.d(
                    TAG,
                    "Matrix verification request acknowledgement completed: " +
                        "trigger=$trigger pending=${summarizePendingRequest()}",
                )
                logLocalSnapshot("acknowledgeVerificationRequest-completed:$trigger")
                logSdkSnapshot("acknowledgeVerificationRequest-completed:$trigger")
            } catch (e: Exception) {
                SLog.w(
                    TAG,
                    "Matrix verification request acknowledgement failed: " +
                        "trigger=$trigger error=${e.javaClass.simpleName}: ${e.message} " +
                        "request=${summarizeRequestDetails(details)}",
                )
                logLocalSnapshot("acknowledgeVerificationRequest-failed:$trigger")
                logSdkSnapshot("acknowledgeVerificationRequest-failed:$trigger")
            }
        }
    }

    private fun createDelegate(): SessionVerificationControllerDelegate {
        return object : SessionVerificationControllerDelegate {
            override fun didReceiveVerificationRequest(details: SessionVerificationRequestDetails) {
                SLog.d(TAG, "Matrix verification callback didReceiveVerificationRequest: ${summarizeRequestDetails(details)}")
                pendingRequest = details
                schedulePendingRequestAcknowledgement(
                    details = details,
                    trigger = "didReceiveVerificationRequest",
                )
                updateState {
                    it.copy(
                        status = MatrixE2eeVerificationStatus.REQUEST_RECEIVED,
                        requestUserId = details.senderProfile.userId,
                        requestDeviceId = details.deviceId,
                        requestDeviceDisplayName = details.deviceDisplayName.orEmpty(),
                        message = null,
                        cancelInfo = null,
                    )
                }
                logLocalSnapshot("callback-didReceiveVerificationRequest")
                logSdkSnapshot("callback-didReceiveVerificationRequest")
            }

            override fun didAcceptVerificationRequest() {
                SLog.d(TAG, "Matrix verification callback didAcceptVerificationRequest: pending=${summarizePendingRequest()}")
                updateState {
                    it.copy(
                        status = MatrixE2eeVerificationStatus.ACCEPTED,
                        message = null,
                    )
                }
                logLocalSnapshot("callback-didAcceptVerificationRequest")
                logSdkSnapshot("callback-didAcceptVerificationRequest")
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
                logLocalSnapshot("callback-didStartSasVerification")
                logSdkSnapshot("callback-didStartSasVerification")
            }

            override fun didReceiveVerificationData(data: SessionVerificationData) {
                val sas = summarizeSas(data)
                SLog.d(
                    TAG,
                    "Matrix verification callback didReceiveVerificationData: " +
                        "type=${data.javaClass.simpleName} decimals=${sas.decimals.size} emojis=${sas.emojis.size} " +
                        "pending=${summarizePendingRequest()}",
                )
                updateState {
                    it.copy(
                        status = MatrixE2eeVerificationStatus.SAS_READY,
                        sasEmojis = sas.emojis,
                        sasDecimals = sas.decimals,
                        message = null,
                    )
                }
                logLocalSnapshot("callback-didReceiveVerificationData")
                logSdkSnapshot("callback-didReceiveVerificationData")
            }

            override fun didFail() {
                if (outgoingSasStartAttemptInProgress.get()) {
                    SLog.d(
                        TAG,
                        "Matrix verification callback didFail suppressed during outgoing SAS auto-start: " +
                            "state=${_state.value.status} pending=${summarizePendingRequest()}",
                    )
                    logLocalSnapshot("callback-didFail-suppressed")
                    return
                }
                SLog.w(TAG, "Matrix verification callback didFail: pending=${summarizePendingRequest()}")
                updateState {
                    it.copy(
                        status = MatrixE2eeVerificationStatus.FAILED,
                        message = null,
                    )
                }
                logLocalSnapshot("callback-didFail")
                logSdkSnapshot("callback-didFail")
            }

            override fun didCancel() {
                val sdkCancelInfo = controller?.requestCancelInfo() ?: controller?.sasCancelInfo()
                SLog.d(
                    TAG,
                    "Matrix verification callback didCancel: pending=${summarizePendingRequest()} " +
                        "cancelInfo=${sdkCancelInfo?.let { "code=${it.cancelCode} byUs=${it.cancelledByUs}" } ?: "n/a"}",
                )
                stopOutgoingSasStart()
                updateState {
                    it.copy(
                        status = MatrixE2eeVerificationStatus.CANCELLED,
                        message = null,
                        cancelInfo = sdkCancelInfo?.let {
                            MatrixE2eeCancelInfo(
                                reason = it.reason,
                                code = it.cancelCode,
                                cancelledByUs = it.cancelledByUs,
                            )
                        },
                    )
                }
                logLocalSnapshot("callback-didCancel")
                logSdkSnapshot("callback-didCancel")
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
                logLocalSnapshot("callback-didFinish")
                logSdkSnapshot("callback-didFinish")
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
            logLocalSnapshot("action-start:$actionName")
            try {
                withContext(Dispatchers.IO) {
                    activeController.block()
                }
                SLog.d(
                    TAG,
                    "Matrix verification action completed: action=$actionName " +
                        "state=${_state.value.status} pending=${summarizePendingRequest()}",
                )
                logLocalSnapshot("action-complete:$actionName")
                logSdkSnapshot("action-complete:$actionName")
            } catch (e: Exception) {
                val ctx = lastContext
                val setting = lastSetting
                if (MatrixE2eeRuntime.isAuthError(e) && ctx != null && setting != null) {
                    SLog.w(
                        TAG,
                        "Matrix verification action auth expired ($actionName): ${e.message}. Auto-refreshing session and retrying...",
                    )
                    try {
                        MatrixE2eeRuntime.invalidateClientAndSession(ctx, setting)
                        val refreshedClient = withContext(Dispatchers.IO) {
                            MatrixE2eeRuntime.getClientForVerification(ctx, setting, forceRefresh = true)
                        }
                        client = refreshedClient
                        stopSync()
                        waitForE2eeInitialization(refreshedClient)
                        ensureOwnIdentityAvailable(refreshedClient)
                        installController(refreshedClient)
                        refreshDeviceKeysForVerification(refreshedClient, setting)
                        runInitialVerificationSync(refreshedClient)
                        startSync(refreshedClient)

                        val retriedController = controller
                        if (retriedController != null) {
                            withContext(Dispatchers.IO) {
                                retriedController.block()
                            }
                            SLog.d(TAG, "Matrix verification action retry succeeded after auth refresh: action=$actionName")
                            logLocalSnapshot("action-complete-after-auth-refresh:$actionName")
                            logSdkSnapshot("action-complete-after-auth-refresh:$actionName")
                            return@withLock
                        }
                    } catch (retryErr: Exception) {
                        SLog.w(TAG, "Matrix verification action retry failed after auth error: ${retryErr.message}")
                    }
                }

                SLog.w(
                    TAG,
                    "Matrix verification action failed: action=$actionName " +
                        "error=${e.javaClass.simpleName}: ${e.message} pending=${summarizePendingRequest()}",
                )
                updateFailure(e.message.orEmpty(), e)
                logLocalSnapshot("action-failed:$actionName")
                logSdkSnapshot("action-failed:$actionName")
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
        val before = _state.value
        val after = block(before)
        if (before != after) {
            SLog.d(
                TAG,
                "Matrix verification state updated: " +
                    "before={${summarizeVerificationState(before)}} " +
                    "after={${summarizeVerificationState(after)}} " +
                    "pending=${summarizePendingRequest()}",
            )
            if (before.status != after.status) {
                val statusName = after.status.name.lowercase()
                val result = when (after.status) {
                    MatrixE2eeVerificationStatus.FAILED -> "error"
                    MatrixE2eeVerificationStatus.CANCELLED,
                    MatrixE2eeVerificationStatus.UNSUPPORTED_AUTH -> "skip"
                    MatrixE2eeVerificationStatus.VERIFIED -> "ok"
                    else -> "ok"
                }
                MagiskOtel.event(
                    name = "sms.forward",
                    attributes = mapOf(
                        "result" to result,
                        "duration_ms" to "0",
                        "process" to "app",
                        "stage" to "matrix_verify",
                        "reason" to statusName,
                        "sender_type" to "matrix",
                    ),
                    statusOk = result != "error",
                )
            }
        }
        _state.value = after
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

    private fun summarizeVerificationState(state: MatrixE2eeVerificationState): String {
        return "status=${state.status} " +
            "trust=${state.verificationState.ifBlank { "<blank>" }} " +
            "hasDevices=${state.hasDevicesToVerifyAgainst} " +
            "requestUser=${summarizeValue(state.requestUserId)} " +
            "requestDevice=${summarizeValue(state.requestDeviceId)} " +
            "sasEmojis=${state.sasEmojis.size} " +
            "sasDecimals=${state.sasDecimals.size} " +
            "message=${summarizeValue(state.message)}"
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

    private fun logLocalSnapshot(reason: String) {
        SLog.d(
            TAG,
            "Matrix verification snapshot[$reason]: " +
                "state={${summarizeVerificationState(_state.value)}} " +
                "pending=${summarizePendingRequest()} " +
                "controller=${controller != null} " +
                "syncRunning=${isHandleRunning(syncHandle)} " +
                "stateListenerRunning=${isHandleRunning(verificationStateHandle)} " +
                "outgoingSasJobActive=${outgoingSasStartJob?.isActive == true} " +
                "outgoingSasAttempt=${outgoingSasStartAttemptInProgress.get()} " +
                "${summarizeClientSession(client)}",
        )
    }

    private fun logSdkSnapshot(reason: String) {
        val activeClient = client
        if (activeClient == null) {
            SLog.d(TAG, "Matrix verification sdk snapshot[$reason]: session=<none>")
            return
        }
        managerScope.launch {
            val verificationState = runCatching {
                activeClient.encryption().verificationState().name
            }.getOrElse { error ->
                "ERROR:${error.javaClass.simpleName}:${error.message.orEmpty()}"
            }
            val hasDevices = runCatching {
                activeClient.encryption().hasDevicesToVerifyAgainst()
            }.getOrElse { error ->
                SLog.d(
                    TAG,
                    "Matrix verification sdk snapshot[$reason] hasDevices query failed: " +
                        "${error.javaClass.simpleName}: ${error.message}",
                )
                false
            }
            SLog.d(
                TAG,
                "Matrix verification sdk snapshot[$reason]: " +
                    "verificationState=$verificationState hasDevices=$hasDevices " +
                    "${summarizeClientSession(activeClient)}",
            )
        }
    }

    private fun isHandleRunning(handle: TaskHandle?): Boolean {
        if (handle == null) return false
        return !runCatching { handle.isFinished() }.getOrDefault(true)
    }

    private data class SasSummary(
        val emojis: List<MatrixE2eeVerificationEmoji>,
        val decimals: List<String>,
    )
}
