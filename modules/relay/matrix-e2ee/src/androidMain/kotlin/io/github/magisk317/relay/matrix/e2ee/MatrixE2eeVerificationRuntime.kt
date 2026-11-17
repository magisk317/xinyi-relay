package io.github.magisk317.relay.matrix.e2ee

import android.content.Context
import io.github.magisk317.forward.sender.MatrixE2eeCancelInfo
import io.github.magisk317.forward.sender.MatrixE2eeVerification
import io.github.magisk317.forward.sender.MatrixE2eeVerificationEmoji
import io.github.magisk317.forward.sender.MatrixE2eeVerificationState
import io.github.magisk317.forward.sender.MatrixE2eeVerificationStatus
import io.github.magisk317.forward.sender.SenderSettingSanitizer
import io.github.magisk317.forward.sender.config.MatrixSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private const val VERIFICATION_SYNC_TIMEOUT_MS = 30_000L

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationMutex = Mutex()
    private val _state = MutableStateFlow(MatrixE2eeVerificationState())
    override val state: StateFlow<MatrixE2eeVerificationState> = _state.asStateFlow()

    private var client: Client? = null
    private var controller: SessionVerificationController? = null
    private var delegate: SessionVerificationControllerDelegate? = null
    private var syncHandle: TaskHandle? = null
    private var verificationStateHandle: TaskHandle? = null
    private var lastContext: Context? = null
    private var lastSetting: MatrixSetting? = null
    private val sasCoordinator = MatrixVerificationSasCoordinator(
        managerScope = managerScope,
        operationMutex = operationMutex,
        state = _state,
        controllerProvider = { controller },
        snapshotLogger = { reason -> logLocalSnapshot(reason) },
    )

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
            sasCoordinator.resetPendingRequestAcknowledgement(cancelActiveJob = true)
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
                    MatrixClientSession.getClientForVerification(context.applicationContext, safeSetting)
                }
                client = nextClient
                stopSync()
                SLog.d(TAG, "Matrix verification client acquired: ${MatrixVerificationDiagnostics.summarizeClientSession(nextClient)}")
                MatrixVerificationBootstrap.waitForE2eeInitialization(nextClient)
                MatrixVerificationBootstrap.ensureOwnIdentityAvailable(nextClient)
                installController(nextClient)
                MatrixVerificationBootstrap.refreshDeviceKeysForVerification(nextClient, safeSetting)
                MatrixVerificationBootstrap.runInitialVerificationSync(nextClient)
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
                        "hasDevices=${readyState.hasDevicesToVerifyAgainst} ${MatrixVerificationDiagnostics.summarizeStateSession(readyState)}",
                )
                updateState { readyState }
                logLocalSnapshot("prepare-completed")
                logSdkSnapshot("prepare-completed")
            } catch (e: Exception) {
                if (MatrixE2eeRuntime.isAuthError(e)) {
                    SLog.w(TAG, "Matrix verification prepare auth expired, auto-refreshing: ${e.message}")
                    try {
                        MatrixClientSession.invalidateClientAndSession(context.applicationContext, safeSetting)
                        val refreshedClient = withContext(Dispatchers.IO) {
                            MatrixClientSession.getClientForVerification(context.applicationContext, safeSetting, forceRefresh = true)
                        }
                        client = refreshedClient
                        stopSync()
                        MatrixVerificationBootstrap.waitForE2eeInitialization(refreshedClient)
                        MatrixVerificationBootstrap.ensureOwnIdentityAvailable(refreshedClient)
                        installController(refreshedClient)
                        MatrixVerificationBootstrap.refreshDeviceKeysForVerification(refreshedClient, safeSetting)
                        MatrixVerificationBootstrap.runInitialVerificationSync(refreshedClient)
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
            SLog.d(TAG, "Matrix verification request sent: ${MatrixVerificationDiagnostics.summarizeClientSession(client)}")
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
        sasCoordinator.pendingRequest?.let { request ->
            sasCoordinator.awaitPendingRequestAcknowledgement(
                details = request,
                trigger = "acceptVerificationRequest",
            )
        } ?: SLog.w(TAG, "Matrix verification accept called without a pending request")

        // matrix-rust-sdk 26.10.20 only fires didAcceptVerificationRequest for
        // requests we started, so accepting an incoming request must land the
        // local ACCEPTED state here rather than in the delegate callback.
        runControllerAction(MatrixE2eeVerificationStatus.ACCEPTED, "acceptVerificationRequest") {
            acceptVerificationRequest()
            SLog.d(TAG, "Matrix verification request accepted: pending=${MatrixVerificationDiagnostics.summarizePendingRequest(sasCoordinator.pendingRequest)}")
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
                    "state=$currentStatus pending=${MatrixVerificationDiagnostics.summarizePendingRequest(sasCoordinator.pendingRequest)}",
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
                        "pending=${MatrixVerificationDiagnostics.summarizePendingRequest(sasCoordinator.pendingRequest)}",
                )
            } else {
                SLog.d(
                    TAG,
                    "Matrix SAS verification start returned: " +
                        "state=$statusAfterStart pending=${MatrixVerificationDiagnostics.summarizePendingRequest(sasCoordinator.pendingRequest)}",
                )
            }
            logLocalSnapshot("startSasVerification-returned")
            logSdkSnapshot("startSasVerification-returned")
        }
    }

    override suspend fun approve() {
        runControllerAction(MatrixE2eeVerificationStatus.SAS_READY, "approveVerification") {
            approveVerification()
            SLog.d(TAG, "Matrix SAS verification approved: pending=${MatrixVerificationDiagnostics.summarizePendingRequest(sasCoordinator.pendingRequest)}")
            logLocalSnapshot("approveVerification-completed")
            logSdkSnapshot("approveVerification-completed")
        }
    }

    override suspend fun decline() {
        runControllerAction(MatrixE2eeVerificationStatus.CANCELLED, "declineVerification") {
            declineVerification()
            val sdkCancelInfo = controller?.requestCancelInfo() ?: controller?.sasCancelInfo()
            SLog.d(TAG, "Matrix verification declined: pending=${MatrixVerificationDiagnostics.summarizePendingRequest(sasCoordinator.pendingRequest)}")
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
            SLog.d(TAG, "Matrix verification cancelled by local action: pending=${MatrixVerificationDiagnostics.summarizePendingRequest(sasCoordinator.pendingRequest)}")
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
        SLog.d(TAG, "Matrix verification revoke device requested: ${MatrixVerificationDiagnostics.summarizeClientSession(client)}")
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
            MatrixClientSession.forceClearDeviceStore(context, setting)
            SLog.d(TAG, "Matrix verification device store forcefully cleared")
        } catch (e: Exception) {
            SLog.w(TAG, "Failed to forcefully clear Matrix device store: ${e.message}")
        }

        reset()
    }

    override fun reset() {
        SLog.d(TAG, "Matrix verification reset requested: ${MatrixVerificationDiagnostics.summarizeClientSession(client)}")
        sasCoordinator.stopOutgoingSasStart()
        stopSync()
        stopVerificationStateListener()
        controller?.setDelegate(null)
        runCatching { controller?.close() }.onFailure {
            // The controller is dropped on the next line; a session that is already
            // gone has nothing left to release.
        }
        controller = null
        delegate = null
        sasCoordinator.resetPendingRequestAcknowledgement(cancelActiveJob = true)
        updateState { MatrixE2eeVerificationState() }
        logLocalSnapshot("reset")
    }

    override fun stop() {
        SLog.d(TAG, "Matrix verification stop requested: ${MatrixVerificationDiagnostics.summarizeClientSession(client)}")
        sasCoordinator.stopOutgoingSasStart()
        stopSync()
        stopVerificationStateListener()
        controller?.setDelegate(null)
        runCatching { controller?.close() }.onFailure {
            // The controller is dropped on the next line; a session that is already
            // gone has nothing left to release.
        }
        controller = null
        delegate = null
        sasCoordinator.resetPendingRequestAcknowledgement(cancelActiveJob = true)
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
            SLog.d(TAG, "Matrix verification controller already installed: ${MatrixVerificationDiagnostics.summarizeClientSession(client)}")
            installVerificationStateListener(client)
            return
        }
        stopVerificationStateListener()
        controller?.setDelegate(null)
        runCatching { controller?.close() }.onFailure {
            // The controller is dropped on the next line; a session that is already
            // gone has nothing left to release.
        }

        val nextController = client.getSessionVerificationController()
        val nextDelegate = createDelegate()
        nextController.setDelegate(nextDelegate)
        controller = nextController
        delegate = nextDelegate
        installVerificationStateListener(client)
        SLog.d(TAG, "Matrix verification controller installed: ${MatrixVerificationDiagnostics.summarizeClientSession(client)}")
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
        SLog.d(TAG, "Matrix verification state listener installed: ${MatrixVerificationDiagnostics.summarizeClientSession(client)}")
    }

    private fun startSync(client: Client) {
        val currentHandle = syncHandle
        if (currentHandle != null && !runCatching { currentHandle.isFinished() }.getOrDefault(true)) {
            SLog.d(TAG, "Matrix verification sync already running: ${MatrixVerificationDiagnostics.summarizeClientSession(client)}")
            return
        }
        syncHandle = client.syncV2(
            SyncSettingsV2(timeoutMs = VERIFICATION_SYNC_TIMEOUT_MS.toULong()),
            object : SyncListenerV2 {
                override fun onUpdate(response: SyncResponseV2) {
                    SLog.d(TAG, "Matrix verification sync update: nextBatch=${MatrixVerificationDiagnostics.summarizeBatch(response.nextBatch)}")
                }
            },
        )
        SLog.d(TAG, "Matrix verification sync started: ${MatrixVerificationDiagnostics.summarizeClientSession(client)}")
    }

    private fun stopSync() {
        if (syncHandle != null) {
            SLog.d(TAG, "Matrix verification sync stopping: ${MatrixVerificationDiagnostics.summarizeClientSession(client)}")
        }
        syncHandle?.let { handle ->
            runCatching { handle.cancel() }.onFailure {
                // Tearing down a subscription that already ended is the normal case.
            }
            runCatching { handle.close() }.onFailure {
                // Same here: the handle is dropped immediately after this block.
            }
        }
        syncHandle = null
    }

    private fun stopVerificationStateListener() {
        verificationStateHandle?.let { handle ->
            runCatching { handle.cancel() }.onFailure {
                // Tearing down a subscription that already ended is the normal case.
            }
            runCatching { handle.close() }.onFailure {
                // Same here: the handle is dropped immediately after this block.
            }
        }
        verificationStateHandle = null
    }

    private fun createDelegate(): SessionVerificationControllerDelegate {
        return object : SessionVerificationControllerDelegate {
            override fun didReceiveVerificationRequest(details: SessionVerificationRequestDetails) {
                SLog.d(TAG, "Matrix verification callback didReceiveVerificationRequest: ${MatrixVerificationDiagnostics.summarizeRequestDetails(details)}")
                sasCoordinator.pendingRequest = details
                sasCoordinator.schedulePendingRequestAcknowledgement(
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
                // SDK >= 26.10.20: only fires when the other side accepted a
                // request we started; incoming accepts transition in acceptRequest().
                SLog.d(TAG, "Matrix verification callback didAcceptVerificationRequest: pending=${MatrixVerificationDiagnostics.summarizePendingRequest(sasCoordinator.pendingRequest)}")
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
                SLog.d(TAG, "Matrix verification callback didStartSasVerification: pending=${MatrixVerificationDiagnostics.summarizePendingRequest(sasCoordinator.pendingRequest)}")
                sasCoordinator.stopOutgoingSasStart()
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
                val sas = MatrixVerificationDiagnostics.summarizeSas(data)
                SLog.d(
                    TAG,
                    "Matrix verification callback didReceiveVerificationData: " +
                        "type=${data.javaClass.simpleName} decimals=${sas.decimals.size} emojis=${sas.emojis.size} " +
                        "pending=${MatrixVerificationDiagnostics.summarizePendingRequest(sasCoordinator.pendingRequest)}",
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
                if (sasCoordinator.outgoingSasStartAttemptInProgress.get()) {
                    SLog.d(
                        TAG,
                        "Matrix verification callback didFail suppressed during outgoing SAS auto-start: " +
                            "state=${_state.value.status} pending=${MatrixVerificationDiagnostics.summarizePendingRequest(sasCoordinator.pendingRequest)}",
                    )
                    logLocalSnapshot("callback-didFail-suppressed")
                    return
                }
                SLog.w(TAG, "Matrix verification callback didFail: pending=${MatrixVerificationDiagnostics.summarizePendingRequest(sasCoordinator.pendingRequest)}")
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
                    "Matrix verification callback didCancel: pending=${MatrixVerificationDiagnostics.summarizePendingRequest(sasCoordinator.pendingRequest)} " +
                        "cancelInfo=${sdkCancelInfo?.let { "code=${it.cancelCode} byUs=${it.cancelledByUs}" } ?: "n/a"}",
                )
                sasCoordinator.stopOutgoingSasStart()
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
                SLog.d(TAG, "Matrix verification callback didFinish: ${MatrixVerificationDiagnostics.summarizeClientSession(client)}")
                sasCoordinator.stopOutgoingSasStart()
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
                    "state=${_state.value.status} ${MatrixVerificationDiagnostics.summarizeClientSession(client)} " +
                    "pending=${MatrixVerificationDiagnostics.summarizePendingRequest(sasCoordinator.pendingRequest)}",
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
                        "state=${_state.value.status} pending=${MatrixVerificationDiagnostics.summarizePendingRequest(sasCoordinator.pendingRequest)}",
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
                        MatrixClientSession.invalidateClientAndSession(ctx, setting)
                        val refreshedClient = withContext(Dispatchers.IO) {
                            MatrixClientSession.getClientForVerification(ctx, setting, forceRefresh = true)
                        }
                        client = refreshedClient
                        stopSync()
                        MatrixVerificationBootstrap.waitForE2eeInitialization(refreshedClient)
                        MatrixVerificationBootstrap.ensureOwnIdentityAvailable(refreshedClient)
                        installController(refreshedClient)
                        MatrixVerificationBootstrap.refreshDeviceKeysForVerification(refreshedClient, setting)
                        MatrixVerificationBootstrap.runInitialVerificationSync(refreshedClient)
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
                        "error=${e.javaClass.simpleName}: ${e.message} pending=${MatrixVerificationDiagnostics.summarizePendingRequest(sasCoordinator.pendingRequest)}",
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
        val transition = _state.updateWithTransition(block)
        val before = transition.before
        val after = transition.after
        if (before != after) {
            SLog.d(
                TAG,
                "Matrix verification state updated: " +
                    "before={${MatrixVerificationDiagnostics.summarizeVerificationState(before)}} " +
                    "after={${MatrixVerificationDiagnostics.summarizeVerificationState(after)}} " +
                    "pending=${MatrixVerificationDiagnostics.summarizePendingRequest(sasCoordinator.pendingRequest)}",
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
    }

    private fun logLocalSnapshot(reason: String) {
        SLog.d(
            TAG,
            "Matrix verification snapshot[$reason]: " +
                "state={${MatrixVerificationDiagnostics.summarizeVerificationState(_state.value)}} " +
                "pending=${MatrixVerificationDiagnostics.summarizePendingRequest(sasCoordinator.pendingRequest)} " +
                "controller=${controller != null} " +
                "syncRunning=${isHandleRunning(syncHandle)} " +
                "stateListenerRunning=${isHandleRunning(verificationStateHandle)} " +
                "outgoingSasJobActive=${sasCoordinator.outgoingSasStartJob?.isActive == true} " +
                "outgoingSasAttempt=${sasCoordinator.outgoingSasStartAttemptInProgress.get()} " +
                "${MatrixVerificationDiagnostics.summarizeClientSession(client)}",
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
                    "${MatrixVerificationDiagnostics.summarizeClientSession(activeClient)}",
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
