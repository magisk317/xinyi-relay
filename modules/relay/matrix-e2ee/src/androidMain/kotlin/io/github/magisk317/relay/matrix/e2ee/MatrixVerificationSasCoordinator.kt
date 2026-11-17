package io.github.magisk317.relay.matrix.e2ee
import io.github.magisk317.forward.sender.MatrixE2eeVerification
import io.github.magisk317.forward.sender.MatrixE2eeVerificationState
import io.github.magisk317.forward.sender.MatrixE2eeVerificationStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.SessionVerificationController
import org.matrix.rustcomponents.sdk.SessionVerificationRequestDetails

/**
 * Outgoing-SAS auto-start retry loop and pending-request acknowledgement
 * bookkeeping for the verification machine. Owns the timing/ack fields;
 * machine state and controller live in the runtime and are injected.
 */
internal class MatrixVerificationSasCoordinator(
    private val managerScope: CoroutineScope,
    private val operationMutex: Mutex,
    private val state: MutableStateFlow<MatrixE2eeVerificationState>,
    private val controllerProvider: () -> SessionVerificationController?,
    private val snapshotLogger: (String) -> Unit,
) {
    internal var pendingRequest: SessionVerificationRequestDetails? = null
    private companion object {
        const val TAG = "MatrixE2eeVerification"
    private const val OUTGOING_SAS_START_INITIAL_DELAY_MS = 1_500L
    private const val OUTGOING_SAS_START_RETRY_DELAY_MS = 2_500L
    private const val OUTGOING_SAS_START_MAX_ATTEMPTS = 24
    }

    internal var outgoingSasStartJob: Job? = null
    internal val outgoingSasStartAttemptInProgress = AtomicBoolean(false)
    private var pendingRequestAckJob: Job? = null
    private var pendingRequestAckFlowId: String? = null
    private var acknowledgedRequestFlowId: String? = null

    internal fun scheduleOutgoingSasStart() {
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
                val currentStatus = state.value.status
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
                    val activeController = controllerProvider()
                    if (activeController == null) {
                        SLog.w(TAG, "Matrix outgoing SAS auto-start skipped without controller")
                        return@launch
                    }
                    val stateBeforeStart = state.value.status
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

                val stateAfterStart = state.value.status
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
                "Matrix outgoing SAS auto-start exhausted: state=${state.value.status}",
            )
        }
    }

    internal fun stopOutgoingSasStart() {
        outgoingSasStartJob?.cancel()
        outgoingSasStartJob = null
        outgoingSasStartAttemptInProgress.set(false)
    }

    internal fun resetPendingRequestAcknowledgement(cancelActiveJob: Boolean) {
        if (cancelActiveJob) {
            pendingRequestAckJob?.cancel()
        }
        pendingRequestAckJob = null
        pendingRequestAckFlowId = null
        acknowledgedRequestFlowId = null
        pendingRequest = null
    }

    internal fun schedulePendingRequestAcknowledgement(
        details: SessionVerificationRequestDetails,
        trigger: String,
    ) {
        val flowId = details.flowId
        if (acknowledgedRequestFlowId == flowId) {
            SLog.d(
                TAG,
                "Matrix verification request acknowledgement already completed: " +
                    "trigger=$trigger flow=${MatrixVerificationDiagnostics.summarizeValue(flowId)}",
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
                    "trigger=$trigger flow=${MatrixVerificationDiagnostics.summarizeValue(flowId)}",
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

    internal suspend fun awaitPendingRequestAcknowledgement(
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
                        "trigger=$trigger flow=${MatrixVerificationDiagnostics.summarizeValue(flowId)}",
                )
                job.join()
            }

        if (acknowledgedRequestFlowId == flowId) {
            SLog.d(
                TAG,
                "Matrix verification request acknowledgement confirmed before accept: " +
                    "trigger=$trigger flow=${MatrixVerificationDiagnostics.summarizeValue(flowId)}",
            )
            return
        }

        SLog.w(
            TAG,
            "Matrix verification request acknowledgement missing before accept, retrying inline: " +
                "trigger=$trigger request=${MatrixVerificationDiagnostics.summarizeRequestDetails(details)}",
        )
        acknowledgePendingRequest(details, "$trigger-inline")
    }

    internal suspend fun acknowledgePendingRequest(
        details: SessionVerificationRequestDetails,
        trigger: String,
    ) {
        operationMutex.withLock {
            val activeController = controllerProvider()
            if (activeController == null) {
                SLog.w(
                    TAG,
                    "Matrix verification request acknowledgement skipped without controller: " +
                        "trigger=$trigger request=${MatrixVerificationDiagnostics.summarizeRequestDetails(details)}",
                )
                return
            }
            if (pendingRequest?.flowId != details.flowId) {
                SLog.d(
                    TAG,
                    "Matrix verification request acknowledgement skipped for stale request: " +
                        "trigger=$trigger request=${MatrixVerificationDiagnostics.summarizeRequestDetails(details)} " +
                        "pending=${MatrixVerificationDiagnostics.summarizePendingRequest(pendingRequest)}",
                )
                return
            }
            if (acknowledgedRequestFlowId == details.flowId) {
                SLog.d(
                    TAG,
                    "Matrix verification request acknowledgement already completed in action: " +
                        "trigger=$trigger flow=${MatrixVerificationDiagnostics.summarizeValue(details.flowId)}",
                )
                return
            }

            SLog.d(
                TAG,
                "Matrix verification request acknowledgement started: " +
                    "trigger=$trigger request=${MatrixVerificationDiagnostics.summarizeRequestDetails(details)}",
            )
            snapshotLogger("acknowledgeVerificationRequest-start:$trigger")
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
                        "trigger=$trigger pending=${MatrixVerificationDiagnostics.summarizePendingRequest(pendingRequest)}",
                )
                snapshotLogger("acknowledgeVerificationRequest-completed:$trigger")
                snapshotLogger("acknowledgeVerificationRequest-completed:$trigger")
            } catch (e: Exception) {
                SLog.w(
                    TAG,
                    "Matrix verification request acknowledgement failed: " +
                        "trigger=$trigger error=${e.javaClass.simpleName}: ${e.message} " +
                        "request=${MatrixVerificationDiagnostics.summarizeRequestDetails(details)}",
                )
                snapshotLogger("acknowledgeVerificationRequest-failed:$trigger")
                snapshotLogger("acknowledgeVerificationRequest-failed:$trigger")
            }
        }
    }

}
