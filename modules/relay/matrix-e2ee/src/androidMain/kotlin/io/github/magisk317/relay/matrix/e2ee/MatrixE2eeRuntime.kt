package io.github.magisk317.relay.matrix.e2ee

import android.content.Context
import io.github.magisk317.forward.engine.model.MsgInfo
import io.github.magisk317.forward.sender.MatrixE2eeAvailabilityProvider
import io.github.magisk317.forward.sender.MatrixE2eeSender
import io.github.magisk317.forward.sender.SenderSettingSanitizer
import io.github.magisk317.forward.sender.config.MatrixSetting
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.FormattedBody
import org.matrix.rustcomponents.sdk.MessageFormat
import org.matrix.rustcomponents.sdk.MessageType
import org.matrix.rustcomponents.sdk.QueueWedgeError
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.RoomMessageEventContentWithoutRelation
import org.matrix.rustcomponents.sdk.RoomSendQueueUpdate
import org.matrix.rustcomponents.sdk.SendQueueListener
import org.matrix.rustcomponents.sdk.SendQueueRoomErrorListener
import org.matrix.rustcomponents.sdk.SendQueueRoomUpdateListener
import org.matrix.rustcomponents.sdk.SyncService
import org.matrix.rustcomponents.sdk.SyncServiceState
import org.matrix.rustcomponents.sdk.SyncServiceStateObserver
import org.matrix.rustcomponents.sdk.SyncSettingsV2
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.TextMessageContent
import org.matrix.rustcomponents.sdk.Timeline
import io.github.magisk317.xposed.logging.MagiskOtel

@Suppress("TooGenericExceptionCaught", "DEPRECATION")
object MatrixE2eeRuntime : MatrixE2eeSender {
    private const val TAG = "MatrixE2eeUtils"
    private const val SENDER_MESSAGE_TYPE_MARKDOWN = "markdown"
    private const val DEVICE_KEY_QUERY_SYNC_TIMEOUT_MS = 15_000L
    private const val POST_SEND_SYNC_TIMEOUT_MS = 15_000L
    private const val SEND_QUEUE_TIMEOUT_MS = 45_000L
    private const val BACKGROUND_TASK_FLUSH_DELAY_MS = 3_000L
    private const val SYNC_SERVICE_WARMUP_DELAY_MS = 2_000L
    private const val POST_SEND_DIAGNOSTIC_FLUSH_DELAY_MS = 5_000L
    private const val SYNC_BATCH_LOG_CHARS = 12
    private const val SYNC_SERVICE_CONNECTION_ID_PREFIX = "xre2ee-"

    private val encryptedSendMutex = Mutex()


    private data class DiagnosticWindow(
        val syncService: SyncService?,
        val syncStateHandle: TaskHandle?,
        val clientQueueUpdateHandle: TaskHandle?,
        val clientQueueErrorHandle: TaskHandle?,
    )

    /**
     * Send a message to a Matrix room with intelligent routing.
     *
     * Routing logic:
     * 1. Check E2EE availability via [GithubPluginFeatureLoader] → if unavailable, delegate to plaintext
     * 2. Query [RoomCryptoState] for the target room → if not encrypted, delegate to plaintext
     * 3. Attempt E2EE send via matrix-rust-sdk Timeline (handles Olm/Megolm automatically)
     * 4. On any exception → log warning → fallback to plaintext via [MatrixUtils.sendMsg]
     *
     * This maintains the same method signature as the noE2ee variant.
     */

    private fun emitMatrixE2ee(
        result: String,
        reason: String,
        durationMs: Long,
        statusOk: Boolean = true,
    ) {
        MagiskOtel.event(
            name = "sms.forward",
            attributes = mapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "app",
                "stage" to "matrix_e2ee",
                "reason" to reason,
                "sender_type" to "matrix",
            ),
            statusOk = statusOk,
        )
    }

    override suspend fun sendMsg(context: Context, setting: MatrixSetting, msgInfo: MsgInfo) {
        val safeSetting = SenderSettingSanitizer.sanitizeMatrixSetting(setting)
        val startedAt = System.nanoTime()

        // Step 1: Check E2EE module availability
        val availability = MatrixE2eeAvailabilityProvider.get()
        if (!availability.isAvailable) {
            SLog.d(TAG, "E2EE module not available (status=${availability.status}), using plaintext")
            MatrixUtils.sendMsg(setting, msgInfo)
            emitMatrixE2ee(
                result = "ok",
                reason = "plaintext_unavailable",
                durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
            )
            return
        }

        // Step 2: Check room encryption state
        val roomEncrypted = try {
            RoomCryptoState.isRoomEncrypted(
                homeserver = safeSetting.homeserver,
                accessToken = MatrixUtils.getAuthToken(safeSetting),
                roomId = safeSetting.roomId,
            )
        } catch (e: RoomCryptoState.AuthenticationException) {
            SLog.w(TAG, "Room crypto state auth failed, assuming encrypted: ${e.message}")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SLog.w(TAG, "Room crypto state query failed, treating as unencrypted: ${e.message}")
            false
        }

        if (!roomEncrypted) {
            SLog.d(TAG, "Room ${safeSetting.roomId} is not encrypted, using plaintext")
            MatrixUtils.sendMsg(setting, msgInfo)
            emitMatrixE2ee(
                result = "ok",
                reason = "plaintext_unencrypted",
                durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
            )
            return
        }

        if (encryptedSendMutex.isLocked) {
            SLog.d(TAG, "Matrix E2EE send queued behind in-flight send: roomId=${safeSetting.roomId}")
        }
        encryptedSendMutex.withLock {
            SLog.d(TAG, "Matrix E2EE send lock acquired: roomId=${safeSetting.roomId}")
            // Step 3: Attempt E2EE send
            try {
                sendEncrypted(context, safeSetting, msgInfo)
                emitMatrixE2ee(
                    result = "ok",
                    reason = "e2ee_sent",
                    durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
                )
            } catch (e: TimeoutCancellationException) {
                throw failEncryptedSend(e, safeSetting, startedAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw failEncryptedSend(e, safeSetting, startedAt)
            }
        }
    }

    /**
     * An encrypted room must never receive a plaintext downgrade: surface the
     * failure to the dispatcher instead of reporting a successful fallback.
     */
    private fun failEncryptedSend(
        error: Exception,
        setting: MatrixSetting,
        startedAt: Long,
    ): IllegalStateException {
        val reason = categorizeFailureReason(error)
        SLog.e(
            TAG,
            "E2EE send failed; refusing plaintext downgrade for encrypted room " +
                "(reason=$reason, roomId=${setting.roomId}): " +
                "${error.javaClass.simpleName}: ${error.message}",
            error,
        )
        emitMatrixE2ee(
            result = "error",
            reason = "e2ee_failed_$reason",
            durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
            statusOk = false,
        )
        return IllegalStateException(
            "Matrix E2EE send failed (reason=$reason): " +
                "${error.javaClass.simpleName}: ${error.message}",
            error,
        )
    }

    /**
     * Performs the actual encrypted send via matrix-rust-sdk Timeline.
     * Throws on any failure; [sendMsg] converts it into a dispatch error instead
     * of downgrading an encrypted room to plaintext.
     *
     * Retry logic: if timeline send fails, retry once with the same parameters.
     * If the retry also fails, the exception propagates to the caller.
     */
    private suspend fun sendEncrypted(
        context: Context,
        setting: MatrixSetting,
        msgInfo: MsgInfo,
    ) = withContext(Dispatchers.IO) {
        MatrixE2eePlatform.initialize(context)
        val client = MatrixClientSession.getOrCreateClient(context, setting)
        logClientSession(client, "after_client_restore")
        val diagnosticWindow = openDiagnosticWindow(client, setting.roomId)

        try {
            // Ensure key sync is ready before sending.
            // The SDK's Timeline handles encryption transparently,
            // but it needs at least one sync to have processed to-device events
            // and uploaded device keys. This is a no-op after the first successful sync.
            MatrixClientSession.ensureKeySyncReady(client)

            val room = client.getRoom(setting.roomId)
                ?: throw IllegalStateException("Matrix room not found: ${setting.roomId}")
            SLog.d(TAG, "Matrix E2EE room resolved: roomId=${setting.roomId}")

            // Force the SDK to fetch full room member list from the server.
            // This is required because sync with lazy-load members may not include
            // all members. After this call, the SDK knows who is in the room.
            try {
                val memberStartMs = System.currentTimeMillis()
                SLog.d(TAG, "Fetching room members to enable key distribution...")
                room.members().use { members ->
                    SLog.d(
                        TAG,
                        "Room members fetched: count=${members.len()} " +
                            "elapsedMs=${System.currentTimeMillis() - memberStartMs}",
                    )
                }
            } catch (e: Exception) {
                SLog.w(
                    TAG,
                    "Room member fetch failed (${e.javaClass.simpleName}: ${e.message}), proceeding anyway",
                )
            }

            // Second sync AFTER loading members: this triggers /keys/query for the
            // newly discovered members' devices. Without this, the SDK doesn't know
            // the device keys needed to establish Olm sessions for key distribution.
            SLog.d(TAG, "Syncing to query member device keys...")
            try {
                val deviceKeySyncStartMs = System.currentTimeMillis()
                val response = withTimeout(DEVICE_KEY_QUERY_SYNC_TIMEOUT_MS) {
                    client.syncOnceV2(SyncSettingsV2(timeoutMs = 0u.toULong()))
                }
                SLog.d(
                    TAG,
                    "Device key query sync completed: elapsedMs=" +
                        "${System.currentTimeMillis() - deviceKeySyncStartMs} " +
                        "nextBatch=${summarizeSyncBatch(response.nextBatch)}; waiting for background tasks...",
                )
                // The Rust SDK processes /keys/query asynchronously. We must wait
                // briefly to ensure it has downloaded the recipient device keys
                // before we attempt to encrypt the message.
                delay(BACKGROUND_TASK_FLUSH_DELAY_MS)
            } catch (e: Exception) {
                SLog.w(TAG, "Device key query sync failed (${e.javaClass.simpleName}), proceeding anyway")
            }

            val title = setting.titleTemplate.ifBlank { "信息驿站: ${msgInfo.from}" }
            val body = "$title\n${msgInfo.content}"
            val messageType = setting.messageType.ifBlank { MatrixSetting().messageType }

            val timelineStartMs = System.currentTimeMillis()
            val timeline = room.timeline()
            SLog.d(TAG, "Matrix timeline opened: elapsedMs=${System.currentTimeMillis() - timelineStartMs}")
            val content = buildTimelineMessageContent(timeline, body, messageType, title, msgInfo.content)

            SLog.d(TAG, "Matrix E2EE send: homeserver=${setting.homeserver}, roomId=${setting.roomId}")

            try {
                // Attempt 1: send through Timeline so the SDK send queue can report device trust/key errors.
                try {
                    val sentEventId = sendTimelineMessage(room, timeline, content)
                    SLog.i(TAG, "Matrix E2EE send success eventId=$sentEventId")

                    // Trigger an immediate sync to flush outgoing to-device requests
                    try {
                        SLog.d(TAG, "Post-send sync for key distribution...")
                        val postSendSyncStartMs = System.currentTimeMillis()
                        val response = withTimeout(POST_SEND_SYNC_TIMEOUT_MS) {
                            client.syncOnceV2(SyncSettingsV2(timeoutMs = 0u.toULong()))
                        }
                        SLog.d(
                            TAG,
                            "Post-send sync completed: elapsedMs=" +
                                "${System.currentTimeMillis() - postSendSyncStartMs} " +
                                "nextBatch=${summarizeSyncBatch(response.nextBatch)}",
                        )
                    } catch (e: Exception) {
                        SLog.w(TAG, "Post-send sync failed (non-fatal): ${e.message}")
                    }

                    SLog.d(
                        TAG,
                        "Waiting for diagnostic flush after send: delayMs=$POST_SEND_DIAGNOSTIC_FLUSH_DELAY_MS",
                    )
                    delay(POST_SEND_DIAGNOSTIC_FLUSH_DELAY_MS)
                    return@withContext
                } catch (firstAttemptError: Exception) {
                    val reason = categorizeFailureReason(firstAttemptError)
                    SLog.w(
                        TAG,
                        "E2EE timeline send failed (attempt=1, reason=$reason, roomId=${setting.roomId}): " +
                            "${firstAttemptError.javaClass.simpleName}: ${firstAttemptError.message}",
                    )

                    // Attempt 2: retry timeline send once
                    try {
                        val sentEventId = sendTimelineMessage(room, timeline, content)
                        SLog.i(TAG, "Matrix E2EE send success (retry) eventId=$sentEventId")
                        delay(POST_SEND_DIAGNOSTIC_FLUSH_DELAY_MS)
                        return@withContext
                    } catch (secondAttemptError: Exception) {
                        SLog.e(
                            TAG,
                            "E2EE timeline send failed twice! (roomId=${setting.roomId}): " +
                                "${secondAttemptError.javaClass.simpleName}: ${secondAttemptError.message}",
                        )
                        throw secondAttemptError
                    }
                }
            } catch (e: Exception) {
                throw e
            }
        } finally {
            closeDiagnosticWindow(diagnosticWindow)
        }
    }

    private fun buildTimelineMessageContent(
        timeline: Timeline,
        body: String,
        messageType: String,
        title: String,
        content: String,
    ): RoomMessageEventContentWithoutRelation {
        val formattedBody = if (messageType == SENDER_MESSAGE_TYPE_MARKDOWN) {
            FormattedBody(
                MessageFormat.Html,
                MatrixUtils.buildFormattedBody(title, content),
            )
        } else {
            null
        }
        return timeline.createMessageContent(
            MessageType.Text(
                TextMessageContent(
                    body = body,
                    formatted = formattedBody,
                ),
            ),
        ) ?: throw IllegalStateException("Failed to create Matrix timeline message content")
    }

    private suspend fun sendTimelineMessage(
        room: Room,
        timeline: Timeline,
        content: RoomMessageEventContentWithoutRelation,
    ): String {
        room.enableSendQueue(true)

        val initialLocalTransactions = Collections.synchronizedSet(mutableSetOf<String>())
        val sendStarted = AtomicBoolean(false)
        val targetTransactionId = AtomicReference<String?>(null)
        val completion = CompletableDeferred<RoomSendQueueUpdate>()

        fun belongsToCurrentSend(transactionId: String): Boolean {
            targetTransactionId.get()?.let { return it == transactionId }
            if (!sendStarted.get()) return false
            if (initialLocalTransactions.contains(transactionId)) return false
            targetTransactionId.compareAndSet(null, transactionId)
            return targetTransactionId.get() == transactionId
        }

        val listener = object : SendQueueListener {
            override fun onUpdate(update: RoomSendQueueUpdate) {
                SLog.d(TAG, "Matrix send queue update: ${summarizeSendQueueUpdate(update)}")
                when (update) {
                    is RoomSendQueueUpdate.NewLocalEvent -> {
                        if (sendStarted.get()) {
                            belongsToCurrentSend(update.transactionId)
                        } else {
                            initialLocalTransactions.add(update.transactionId)
                        }
                    }
                    is RoomSendQueueUpdate.SentEvent -> {
                        if (!completion.isCompleted && belongsToCurrentSend(update.transactionId)) {
                            completion.complete(update)
                        }
                    }
                    is RoomSendQueueUpdate.SendError -> {
                        if (!completion.isCompleted && belongsToCurrentSend(update.transactionId)) {
                            completion.complete(update)
                        }
                    }
                    else -> Unit
                }
            }
        }

        val taskHandle = room.subscribeToSendQueueUpdates(listener)
        try {
            sendStarted.set(true)
            timeline.send(content)
            val update = withTimeout(SEND_QUEUE_TIMEOUT_MS) {
                completion.await()
            }
            return when (update) {
                is RoomSendQueueUpdate.SentEvent -> update.eventId
                is RoomSendQueueUpdate.SendError -> {
                    throw IllegalStateException(
                        "Matrix send queue failed: ${summarizeQueueError(update.error)} " +
                            "recoverable=${update.isRecoverable}",
                    )
                }
                else -> throw IllegalStateException("Unexpected Matrix send queue update: $update")
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException(
                "Matrix send queue did not report SentEvent within timeout " +
                    "(targetTransactionId=${targetTransactionId.get()}, " +
                    "initialLocalTransactions=${initialLocalTransactions.size})",
                e,
            )
        } finally {
            taskHandle.cancel()
            taskHandle.close()
        }
    }

    private fun summarizeSendQueueUpdate(update: RoomSendQueueUpdate): String {
        return when (update) {
            is RoomSendQueueUpdate.NewLocalEvent -> "new_local transactionId=${update.transactionId}"
            is RoomSendQueueUpdate.ReplacedLocalEvent -> "replaced_local transactionId=${update.transactionId}"
            is RoomSendQueueUpdate.SentEvent -> {
                "sent transactionId=${update.transactionId} eventId=${update.eventId}"
            }
            is RoomSendQueueUpdate.SendError -> {
                "send_error transactionId=${update.transactionId} " +
                    "recoverable=${update.isRecoverable} error=${summarizeQueueError(update.error)}"
            }
            is RoomSendQueueUpdate.RetryEvent -> "retry transactionId=${update.transactionId}"
            is RoomSendQueueUpdate.CancelledLocalEvent -> "cancelled transactionId=${update.transactionId}"
            is RoomSendQueueUpdate.MediaUpload -> {
                "media_upload relatedTo=${update.relatedTo} index=${update.index} progress=${update.progress}"
            }
        }
    }

    private fun summarizeQueueError(error: QueueWedgeError): String {
        return when (error) {
            is QueueWedgeError.InsecureDevices -> {
                "InsecureDevices users=${error.userDeviceMap.keys.joinToString(",")}"
            }
            is QueueWedgeError.IdentityViolations -> {
                "IdentityViolations users=${error.users.joinToString(",")}"
            }
            is QueueWedgeError.GenericApiError -> "GenericApiError msg=${error.msg}"
            is QueueWedgeError.CrossVerificationRequired -> "CrossVerificationRequired"
            is QueueWedgeError.ExpiredAccessToken -> "ExpiredAccessToken"
            is QueueWedgeError.InvalidMimeType -> "InvalidMimeType"
            is QueueWedgeError.MissingMediaContent -> "MissingMediaContent"
        }
    }

    private fun summarizeSyncBatch(nextBatch: String): String {
        if (nextBatch.isBlank()) return "<blank>"
        return if (nextBatch.length <= SYNC_BATCH_LOG_CHARS) {
            nextBatch
        } else {
            "${nextBatch.take(SYNC_BATCH_LOG_CHARS)}..."
        }
    }

    private fun logClientSession(client: Client, stage: String) {
        try {
            val session = client.session()
            SLog.d(
                TAG,
                "Matrix client session: stage=$stage userId=${session.userId} " +
                    "deviceId=${session.deviceId} homeserver=${session.homeserverUrl} " +
                    "slidingSync=${session.slidingSyncVersion} " +
                    "hasRefreshToken=${session.refreshToken != null}",
            )
        } catch (e: Exception) {
            SLog.w(TAG, "Matrix client session unavailable at $stage: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private suspend fun openDiagnosticWindow(client: Client, roomId: String): DiagnosticWindow {
        val queueErrorHandle = openClientSendQueueErrorLog(client)
        val queueUpdateHandle = openClientSendQueueUpdateLog(client)
        var syncService: SyncService? = null
        var syncStateHandle: TaskHandle? = null

        try {
                val buildStartMs = System.currentTimeMillis()
                syncService = withTimeout(POST_SEND_SYNC_TIMEOUT_MS) {
                    client.syncService()
                    .withRoomListConnectionId("$SYNC_SERVICE_CONNECTION_ID_PREFIX${MatrixE2eeSendPolicy.sha256Hex(roomId).take(8)}")
                    .withRoomListTimelineLimit(0u)
                    .withSharePos(false)
                    .finish()
            }
            SLog.d(TAG, "Matrix sync service built: elapsedMs=${System.currentTimeMillis() - buildStartMs}")

            syncStateHandle = syncService.state(object : SyncServiceStateObserver {
                override fun onUpdate(state: SyncServiceState) {
                    SLog.d(TAG, "Matrix sync service state: $state")
                }
            })

            val startMs = System.currentTimeMillis()
            withTimeout(POST_SEND_SYNC_TIMEOUT_MS) {
                syncService.start()
            }
            SLog.d(TAG, "Matrix sync service started: elapsedMs=${System.currentTimeMillis() - startMs}")
            delay(SYNC_SERVICE_WARMUP_DELAY_MS)
        } catch (e: Exception) {
            SLog.w(
                TAG,
                "Matrix diagnostic sync service unavailable " +
                    "(${e.javaClass.simpleName}: ${e.message}); continuing with one-shot sync",
            )
            closeDiagnosticHandle("sync_service_state", syncStateHandle)
            try {
                syncService?.close()
            } catch (closeError: Exception) {
                SLog.w(TAG, "Matrix sync service close failed after start error: ${closeError.message}")
            }
            syncService = null
            syncStateHandle = null
        }

        return DiagnosticWindow(
            syncService = syncService,
            syncStateHandle = syncStateHandle,
            clientQueueUpdateHandle = queueUpdateHandle,
            clientQueueErrorHandle = queueErrorHandle,
        )
    }

    private fun openClientSendQueueErrorLog(client: Client): TaskHandle? {
        return try {
            client.subscribeToSendQueueStatus(object : SendQueueRoomErrorListener {
                override fun onError(roomId: String, error: org.matrix.rustcomponents.sdk.ClientException) {
                    val summary = "${error.javaClass.simpleName}: ${error.message}"
                    SLog.w(TAG, "Matrix client send queue room error: roomId=$roomId error=$summary")
                    // Room-level send queue errors are the wedge surface of the SDK send queue.
                    // Since matrix-sdk 0.19 a wedged request blocks everything queued behind it
                    // in the same room (intentional: ordering over progress), so surface it as a
                    // failed observable event instead of a log line only.
                    MagiskOtel.event(
                        name = "matrix.send_queue_room_error",
                        attributes = mapOf(
                            "process" to "app",
                            "stage" to "matrix_e2ee",
                            "sender_type" to "matrix",
                            "room_id" to roomId,
                            "error" to summary,
                        ),
                        statusOk = false,
                    )
                }
            })
        } catch (e: Exception) {
            SLog.w(TAG, "Matrix client send queue error listener unavailable: ${e.message}")
            null
        }
    }

    private suspend fun openClientSendQueueUpdateLog(client: Client): TaskHandle? {
        return try {
            client.subscribeToSendQueueUpdates(object : SendQueueRoomUpdateListener {
                override fun onUpdate(roomId: String, update: RoomSendQueueUpdate) {
                    SLog.d(TAG, "Matrix client send queue update: roomId=$roomId ${summarizeSendQueueUpdate(update)}")
                }
            })
        } catch (e: Exception) {
            SLog.w(TAG, "Matrix client send queue update listener unavailable: ${e.message}")
            null
        }
    }

    private suspend fun closeDiagnosticWindow(window: DiagnosticWindow) {
        window.syncService?.let { syncService ->
            try {
                val stopStartMs = System.currentTimeMillis()
                withTimeout(POST_SEND_SYNC_TIMEOUT_MS) {
                    syncService.stop()
                }
                SLog.d(TAG, "Matrix sync service stopped: elapsedMs=${System.currentTimeMillis() - stopStartMs}")
            } catch (e: Exception) {
                SLog.w(TAG, "Matrix sync service stop failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        closeDiagnosticHandle("sync_service_state", window.syncStateHandle)
        closeDiagnosticHandle("client_send_queue_update", window.clientQueueUpdateHandle)
        closeDiagnosticHandle("client_send_queue_error", window.clientQueueErrorHandle)

        try {
            window.syncService?.close()
        } catch (e: Exception) {
            SLog.w(TAG, "Matrix sync service close failed: ${e.message}")
        }
    }

    private fun closeDiagnosticHandle(name: String, handle: TaskHandle?) {
        if (handle == null) return
        val finishedBeforeCancel = try {
            handle.isFinished()
        } catch (e: Exception) {
            SLog.w(TAG, "Matrix diagnostic handle status check failed: name=$name error=${e.message}")
            null
        }
        try {
            handle.cancel()
        } catch (e: Exception) {
            SLog.w(TAG, "Matrix diagnostic handle cancel failed: name=$name error=${e.message}")
        }
        try {
            handle.close()
            SLog.d(TAG, "Matrix diagnostic handle closed: name=$name finishedBeforeCancel=$finishedBeforeCancel")
        } catch (e: Exception) {
            SLog.w(TAG, "Matrix diagnostic handle close failed: name=$name error=${e.message}")
        }
    }

    /**
     * Categorize an exception into a failure reason for logging.
     * Categories: "encryption_timeout", "encryption_error"
     *
     * 协程超时由 commonMain 的 [MatrixE2eeSendPolicy.categorizeFailureReason] 判定，socket 读超时
     * 需要 `java.net.SocketTimeoutException`，只有 Android 侧能引用，所以补在这里。
     */
    internal fun categorizeFailureReason(error: Exception): String {
        val reason = MatrixE2eeSendPolicy.categorizeFailureReason(error)
        if (reason == MatrixE2eeSendPolicy.FAILURE_ENCRYPTION_ERROR &&
            error is java.net.SocketTimeoutException
        ) {
            return MatrixE2eeSendPolicy.FAILURE_ENCRYPTION_TIMEOUT
        }
        return reason
    }

    /**
     * Ensures the SDK's key synchronization is ready before sending encrypted messages.
     *
     * The matrix-rust-sdk high-level Timeline API automatically handles:
     * - Encrypting messages with Megolm for encrypted rooms
     * - Distributing group keys to room members' devices via Olm to-device messages
     * - Creating and rotating Megolm outbound sessions
     *
     * However, the SDK requires at least one sync to complete the following before
     * encryption can work properly:
     * - Device identity key upload (/keys/upload) — Requirements 3.1
     * - One-time key upload and replenishment — Requirements 3.3
     * - Processing incoming to-device events (key requests, Olm sessions) — Requirements 9.1-9.5
     * - Querying room member device keys — Requirements 3.2
     *
     * After the initial sync, the SDK internally manages:
     * - One-time key replenishment when server-side count drops below threshold
     * - to-device event processing on subsequent syncs
     * - Key distribution as part of sendRaw() encryption flow
     *
     * This method performs a one-time non-blocking sync (timeout=0) on first use,
     * then becomes a no-op for subsequent calls with the same client instance.
     * If the sync fails, it logs a warning but does NOT block sending — the SDK
     * will attempt encryption with whatever state it has (Requirement 9.5).
     */
    internal fun isAuthError(e: Throwable): Boolean {
        val messageLower = (e.message ?: "").lowercase() + (e.cause?.message ?: "").lowercase()
        return messageLower.contains("m_unknown_token") ||
            messageLower.contains("token is not active") ||
            messageLower.contains("unauthorized") ||
            messageLower.contains("authentication") ||
            messageLower.contains("401")
    }

}
