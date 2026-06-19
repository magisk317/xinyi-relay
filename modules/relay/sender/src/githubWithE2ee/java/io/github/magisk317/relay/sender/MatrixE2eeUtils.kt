package io.github.magisk317.relay.sender

import android.content.Context
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.sender.config.MatrixSetting
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import org.json.JSONObject
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientBuilder
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
import org.matrix.rustcomponents.sdk.Session
import org.matrix.rustcomponents.sdk.SyncService
import org.matrix.rustcomponents.sdk.SyncServiceState
import org.matrix.rustcomponents.sdk.SyncServiceStateObserver
import org.matrix.rustcomponents.sdk.SyncSettingsV2
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.TextMessageContent
import org.matrix.rustcomponents.sdk.Timeline

@Suppress("TooGenericExceptionCaught", "DEPRECATION")
object MatrixE2eeUtils {
    private const val TAG = "MatrixE2eeUtils"
    private const val SENDER_MESSAGE_TYPE_MARKDOWN = "markdown"
    private const val WHOAMI_CACHE_FILENAME = "whoami_cache.json"
    private const val LOGIN_SESSION_FILENAME = "session.json"
    private const val LOGIN_DEVICE_DISPLAY_NAME = "XinyiRelay"
    internal const val LOGIN_DEVICE_ID = "XINYI_RELAY_E2EE"
    private const val CLIENT_INIT_TIMEOUT_MS = 30_000L // 30 seconds
    private const val E2EE_INIT_TIMEOUT_MS = 15_000L
    private const val DEVICE_KEY_QUERY_SYNC_TIMEOUT_MS = 15_000L
    private const val INITIAL_KEY_SYNC_TIMEOUT_MS = 30_000L
    private const val POST_SEND_SYNC_TIMEOUT_MS = 15_000L
    private const val SEND_QUEUE_TIMEOUT_MS = 45_000L
    private const val BACKGROUND_TASK_FLUSH_DELAY_MS = 3_000L
    private const val SYNC_SERVICE_WARMUP_DELAY_MS = 2_000L
    private const val POST_SEND_DIAGNOSTIC_FLUSH_DELAY_MS = 5_000L
    private const val SYNC_BATCH_LOG_CHARS = 12
    private const val SYNC_SERVICE_CONNECTION_ID_PREFIX = "xre2ee-"

    private val clientMutex = Mutex()
    private val encryptedSendMutex = Mutex()
    private var cachedClient: Client? = null
    private var cachedAccessToken: String? = null
    // Tracks whether the initial sync has been performed for the current client.
    // The SDK's high-level Timeline API handles encryption automatically,
    // but requires at least one sync to:
    // 1. Upload device identity keys and one-time keys to the homeserver
    // 2. Process incoming to-device events (key shares, Olm session establishment)
    // 3. Replenish one-time keys when the server-side count falls below threshold
    // After the initial sync, these operations are triggered internally by the SDK
    // on subsequent sends.
    private var initialSyncDone: Boolean = false

    /**
     * Holds the result of a /whoami API call.
     */
    internal data class WhoamiInfo(
        val userId: String,
        val deviceId: String
    )

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
     * 1. Check E2EE availability via [GithubFeatureLoader] → if unavailable, delegate to plaintext
     * 2. Query [RoomCryptoState] for the target room → if not encrypted, delegate to plaintext
     * 3. Attempt E2EE send via matrix-rust-sdk Timeline (handles Olm/Megolm automatically)
     * 4. On any exception → log warning → fallback to plaintext via [MatrixUtils.sendMsg]
     *
     * This maintains the same method signature as the noE2ee variant.
     */
    suspend fun sendMsg(context: Context, setting: MatrixSetting, msgInfo: MsgInfo) {
        val safeSetting = SenderSettingSanitizer.sanitizeMatrixSetting(setting)

        // Step 1: Check E2EE module availability
        if (!GithubFeatureLoader.isAvailable) {
            SLog.d(TAG, "E2EE module not available (status=${GithubFeatureLoader.status}), using plaintext")
            MatrixUtils.sendMsg(setting, msgInfo)
            return
        }

        // Step 2: Check room encryption state
        val roomEncrypted = try {
            RoomCryptoState.isRoomEncrypted(
                homeserver = safeSetting.homeserver,
                accessToken = MatrixUtils.getAuthToken(safeSetting),
                roomId = safeSetting.roomId,
            )
        } catch (e: Exception) {
            // If query fails due to auth error, assume encrypted (safer default).
            // If it fails for other reasons, treat as unencrypted.
            val isAuthError = e.message?.contains("token may be expired") == true
            if (isAuthError) {
                SLog.w(TAG, "Room crypto state auth failed, assuming encrypted: ${e.message}")
                true
            } else {
                SLog.w(TAG, "Room crypto state query failed, treating as unencrypted: ${e.message}")
                false
            }
        }

        if (!roomEncrypted) {
            SLog.d(TAG, "Room ${safeSetting.roomId} is not encrypted, using plaintext")
            MatrixUtils.sendMsg(setting, msgInfo)
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
            } catch (e: Exception) {
                // Step 4: Fallback to plaintext on any encryption/send failure
                val fallbackReason = categorizeFailureReason(e)
                SLog.w(
                    TAG,
                    "E2EE send failed, falling back to plaintext " +
                        "(reason=$fallbackReason, roomId=${safeSetting.roomId}): " +
                        "${e.javaClass.simpleName}: ${e.message}",
                    e,
                )
                MatrixUtils.sendMsg(setting, msgInfo)
            }
        }
    }

    /**
     * Performs the actual encrypted send via matrix-rust-sdk Timeline.
     * Throws on any failure so the caller can handle fallback.
     *
     * Retry logic: if timeline send fails, retry once with the same parameters.
     * If the retry also fails, the exception propagates to the caller for plaintext fallback.
     */
    private suspend fun sendEncrypted(
        context: Context,
        setting: MatrixSetting,
        msgInfo: MsgInfo,
    ) = withContext(Dispatchers.IO) {
        val client = getOrCreateClient(context, setting)
        logClientSession(client, "after_client_restore")
        val diagnosticWindow = openDiagnosticWindow(client, setting.roomId)

        try {
            // Ensure key sync is ready before sending.
            // The SDK's Timeline handles encryption transparently,
            // but it needs at least one sync to have processed to-device events
            // and uploaded device keys. This is a no-op after the first successful sync.
            ensureKeySyncReady(client)

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
                    .withRoomListConnectionId("$SYNC_SERVICE_CONNECTION_ID_PREFIX${sha256Hex(roomId).take(8)}")
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
                    SLog.w(
                        TAG,
                        "Matrix client send queue room error: roomId=$roomId " +
                            "error=${error.javaClass.simpleName}: ${error.message}",
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
     */
    internal fun categorizeFailureReason(error: Exception): String {
        return when (error) {
            is TimeoutCancellationException -> "encryption_timeout"
            is java.net.SocketTimeoutException -> "encryption_timeout"
            else -> "encryption_error"
        }
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
    private suspend fun ensureKeySyncReady(client: Client) {
        if (initialSyncDone) return

        // Wait for background E2EE initialization tasks (key upload, etc.)
        // that were started by restoreSession(). This ensures device keys are
        // uploaded to the homeserver before we attempt to send.
        SLog.d(TAG, "Waiting for E2EE initialization tasks to complete...")
        withTimeout(E2EE_INIT_TIMEOUT_MS) {
            client.encryption().waitForE2eeInitializationTasks()
        }
        SLog.d(TAG, "E2EE initialization tasks completed")

        // Attempt a quick sync to process to-device events and establish
        // Megolm sessions. Use a short timeout — if the server takes too long
        // (e.g. large initial sync), skip it and let Timeline send handle key
        // distribution inline (the SDK does this automatically).
        try {
            SLog.d(TAG, "Performing initial sync for key synchronization...")
            withTimeout(INITIAL_KEY_SYNC_TIMEOUT_MS) {
                client.syncOnceV2(SyncSettingsV2(timeoutMs = 0u.toULong()))
            }
            SLog.i(TAG, "Initial key sync completed successfully")
        } catch (e: Exception) {
            // Sync timeout/failure is acceptable for send-only usage.
            // The SDK's Timeline send will attempt to establish Megolm sessions
            // and distribute keys inline if no prior sync has occurred.
            SLog.w(
                TAG,
                "Initial sync skipped (${e.javaClass.simpleName}: ${e.message}). " +
                    "Proceeding with Timeline send — SDK will handle key distribution inline.",
            )
        }

        initialSyncDone = true
    }

    private suspend fun getOrCreateClient(context: Context, setting: MatrixSetting): Client {
        // In login mode, cache key is username; in token mode, it's accessToken
        val useLoginMode = setting.username.isNotBlank() && setting.password.isNotBlank()
        val cacheKey = if (useLoginMode) setting.username.trim() else setting.accessToken.trim()
        val token = setting.accessToken.trim()
        clientMutex.withLock {
            // Return cached client if credentials haven't changed
            cachedClient?.let { client ->
                if (cachedAccessToken == cacheKey) return client
                // Access token changed — close old client safely before building new one
                closeClientSafely(client)
                cachedClient = null
                cachedAccessToken = null
                initialSyncDone = false
            }

            // Build new client with a 30-second timeout
            try {
                val client = withTimeout(CLIENT_INIT_TIMEOUT_MS) {
                    buildAndRestoreClient(context, setting, token)
                }
                cachedAccessToken = cacheKey
                return client
            } catch (e: TimeoutCancellationException) {
                SLog.e(TAG, "Client initialization timed out after ${CLIENT_INIT_TIMEOUT_MS}ms")
                throw IllegalStateException(
                    "CryptoEngine initialization timed out (${CLIENT_INIT_TIMEOUT_MS / 1000}s limit exceeded)",
                    e
                )
            }
        }
    }

    internal suspend fun getClientForVerification(context: Context, setting: MatrixSetting): Client {
        val safeSetting = SenderSettingSanitizer.sanitizeMatrixSetting(setting)
        return getOrCreateClient(context, safeSetting)
    }

    /**
     * Build a new Client instance and restore session.
     * This is separated from getOrCreateClient to keep the timeout boundary clear.
     *
     * If build() or restoreSession() throws a store-related exception (e.g. database corruption,
     * crypto store errors), the store directory is deleted and the operation is retried once.
     * If the retry also fails, the exception propagates (triggering plaintext fallback).
     */
    private suspend fun buildAndRestoreClient(context: Context, setting: MatrixSetting, token: String): Client {
        // Determine store directory based on auth mode
        val useLoginMode = setting.username.isNotBlank() && setting.password.isNotBlank()
        val storeDir: File
        val whoami: WhoamiInfo?

        if (useLoginMode) {
            // Login mode: use username hash for store directory (stable across token changes)
            storeDir = getStoreDir(context, setting.username.trim())
            whoami = null
        } else {
            // Legacy token mode: resolve userId via whoami
            whoami = resolveWhoami(context, setting)
            storeDir = getStoreDir(context, whoami.userId)
            saveWhoamiCache(storeDir, whoami, token)
        }

        return try {
            buildClientWithSession(setting, storeDir, whoami, token)
        } catch (e: Exception) {
            val messageLower = (e.message ?: "").lowercase()
            val isAuthError = messageLower.contains("m_unknown_token") ||
                              messageLower.contains("token is not active") ||
                              messageLower.contains("unauthorized") ||
                              messageLower.contains("authentication")

            if (isStoreCorruptionException(e) || isAuthError) {
                SLog.w(
                    TAG,
                    "Crypto store corrupted or auth failed, deleting store and retrying. " +
                        "Reason: ${e.javaClass.simpleName}: ${e.message}",
                    e,
                )
                // Delete the corrupted store directory and session file
                storeDir.deleteRecursively()
                // Recreate the store directory
                storeDir.mkdirs()
                // Re-save whoami cache after directory recreation (only in token mode)
                if (whoami != null) {
                    saveWhoamiCache(storeDir, whoami, token)
                }
                // Retry once — if this also fails, let it propagate
                buildClientWithSession(setting, storeDir, whoami, token)
            } else {
                throw e
            }
        }.also { client ->
            cachedClient = client
            // Enable send queues so the SDK processes outgoing to-device messages
            // (Megolm session key distribution) during sync.
            client.enableAllSendQueues(true)
        }
    }

    /**
     * Builds a Client and restores the session. Throws on any failure.
     * Extracted to allow retry logic in [buildAndRestoreClient].
     */
    private suspend fun buildClientWithSession(
        setting: MatrixSetting,
        storeDir: File,
        whoami: WhoamiInfo?,
        token: String,
    ): Client {
        val useLoginMode = setting.username.isNotBlank() && setting.password.isNotBlank()
        if (useLoginMode) {
            prepareLoginStoreForExpectedSession(setting, storeDir)
        }

        SLog.d(TAG, "Matrix E2EE room key recipient strategy: all_devices")
        val builder = ClientBuilder()
            .homeserverUrl(normalizeHomeserverForSession(setting.homeserver))
            .sessionPaths(storeDir.absolutePath, storeDir.absolutePath)
            .autoEnableCrossSigning(true)
            // Distribute Megolm session keys to ALL devices in the room,
            // regardless of cross-signing verification status. Without this,
            // the default IDENTITY_BASED_STRATEGY only shares keys with devices
            // signed by their owner's cross-signing identity — causing
            // "sender's device has not sent us the keys" for unverified recipients.
            .roomKeyRecipientStrategy(uniffi.matrix_sdk_crypto.CollectStrategy.ALL_DEVICES)

        // Configure proxy if needed
        if (setting.proxyType != java.net.Proxy.Type.DIRECT &&
            setting.proxyHost.isNotBlank() &&
            setting.proxyPort.isNotBlank()
        ) {
            val port = setting.proxyPort.toIntOrNull() ?: 0
            if (port > 0) {
                val proxyUrl = when (setting.proxyType) {
                    java.net.Proxy.Type.HTTP -> "http://${setting.proxyHost}:$port"
                    java.net.Proxy.Type.SOCKS -> "socks5://${setting.proxyHost}:$port"
                    else -> null
                }
                proxyUrl?.let { builder.proxy(it) }
            }
        }

        val client = builder.build()

        // Authenticate: prefer username+password login (stable device session),
        // fall back to restoreSession with access token.
        if (useLoginMode) {
            val sessionFile = File(storeDir, LOGIN_SESSION_FILENAME)
            if (sessionFile.exists()) {
                try {
                    val sessionStr = sessionFile.readText()
                    val sessionJson = JSONObject(sessionStr)
                    val session = Session(
                        accessToken = sessionJson.getString("accessToken"),
                        refreshToken = sessionJson.optString("refreshToken").takeIf { it.isNotBlank() },
                        userId = sessionJson.getString("userId"),
                        deviceId = sessionJson.getString("deviceId"),
                        homeserverUrl = sessionJson.getString("homeserverUrl"),
                        oauthData = null,
                        slidingSyncVersion = org.matrix.rustcomponents.sdk.SlidingSyncVersion.NONE,
                    )
                    client.restoreSession(session)
                    SLog.d(TAG, "Restored session for ${session.userId} (${session.deviceId})")
                    return client
                } catch (e: Exception) {
                    SLog.w(TAG, "Failed to restore session from file: ${e.message}, will login fresh")
                }
            }

            // Login with credentials — SDK manages token lifecycle, device_id is stable.
            try {
                SLog.d(TAG, "Logging in with username/password...")
                client.login(
                    setting.username.trim(),
                    setting.password,
                    LOGIN_DEVICE_DISPLAY_NAME,
                    LOGIN_DEVICE_ID,
                )
                SLog.i(TAG, "Login successful")

                // Save session for future restore
                val session = client.session()
                val sessionJson = JSONObject().apply {
                    put("accessToken", session.accessToken)
                    put("refreshToken", session.refreshToken ?: "")
                    put("userId", session.userId)
                    put("deviceId", session.deviceId)
                    put("homeserverUrl", session.homeserverUrl)
                }
                sessionFile.writeText(sessionJson.toString())
            } catch (e: Exception) {
                closeClientSafely(client)
                throw IllegalStateException("Matrix login failed: ${e.message}", e)
            }
        } else {
            // Legacy: restore session with manually provided access token
            try {
                client.restoreSession(
                    Session(
                        accessToken = token,
                        refreshToken = null,
                        userId = whoami?.userId ?: "",
                        deviceId = whoami?.deviceId ?: "",
                        homeserverUrl = normalizeHomeserverForSession(setting.homeserver),
                        oauthData = null,
                        slidingSyncVersion = org.matrix.rustcomponents.sdk.SlidingSyncVersion.NONE,
                    )
                )
            } catch (e: Exception) {
                closeClientSafely(client)
                throw e
            }
        }

        return client
    }

    /**
     * Login mode intentionally pins the Matrix device id so users can verify one stable relay device.
     * Older cached stores may contain a session restored from an arbitrary token-created device; those
     * stores must be cleared before building the SDK client because the crypto store is device-scoped.
     */
    private fun prepareLoginStoreForExpectedSession(setting: MatrixSetting, storeDir: File) {
        val sessionFile = File(storeDir, LOGIN_SESSION_FILENAME)
        if (!sessionFile.exists()) {
            if (storeDir.listFiles()?.isNotEmpty() == true) {
                clearLoginStore(storeDir, "missing cached login session")
            }
            return
        }

        val sessionJson = try {
            JSONObject(sessionFile.readText())
        } catch (e: Exception) {
            SLog.w(TAG, "Failed to read cached Matrix login session metadata: ${e.message}")
            clearLoginStore(storeDir, "unreadable cached login session")
            return
        }

        val cachedDeviceId = sessionJson.optString("deviceId", "")
        val cachedHomeserver = sessionJson.optString("homeserverUrl", "")
        val expectedHomeserver = normalizeHomeserverForSession(setting.homeserver)
        if (!shouldReuseLoginSession(cachedDeviceId, cachedHomeserver, expectedHomeserver)) {
            val reason = when {
                cachedDeviceId != LOGIN_DEVICE_ID -> "device_id_mismatch"
                normalizeHomeserverForSession(cachedHomeserver) != expectedHomeserver -> "homeserver_mismatch"
                else -> "unknown"
            }
            SLog.w(TAG, "Cached Matrix login session does not match expected relay device; clearing store. reason=$reason")
            clearLoginStore(storeDir, reason)
        }
    }

    private fun clearLoginStore(storeDir: File, reason: String) {
        SLog.w(TAG, "Clearing Matrix login crypto store for fresh login: reason=$reason")
        storeDir.deleteRecursively()
        storeDir.mkdirs()
    }

    internal fun shouldReuseLoginSession(
        cachedDeviceId: String,
        cachedHomeserverUrl: String,
        expectedHomeserverUrl: String,
    ): Boolean {
        return cachedDeviceId == LOGIN_DEVICE_ID &&
            normalizeHomeserverForSession(cachedHomeserverUrl) ==
            normalizeHomeserverForSession(expectedHomeserverUrl)
    }

    internal fun normalizeHomeserverForSession(homeserver: String): String {
        return homeserver.trim().trimEnd('/')
    }

    /**
     * Determines whether an exception indicates crypto store corruption.
     * Common indicators: database corruption, SQLite errors, crypto store deserialization failures,
     * schema version mismatches, or file I/O errors in the store layer.
     */
    private fun isStoreCorruptionException(e: Exception): Boolean {
        val message = (e.message ?: "") + (e.cause?.message ?: "")
        val messageLower = message.lowercase()
        return messageLower.contains("corrupt") ||
            messageLower.contains("database") ||
            messageLower.contains("sqlite") ||
            messageLower.contains("crypto store") ||
            messageLower.contains("cryptostore") ||
            messageLower.contains("deseriali") ||
            messageLower.contains("schema") ||
            messageLower.contains("migration") ||
            messageLower.contains("open_store") ||
            messageLower.contains("openstore") ||
            messageLower.contains("sled") ||
            messageLower.contains("io error")
    }

    /**
     * Safely close a Client instance, catching any exceptions to prevent
     * cleanup from disrupting the main flow.
     */
    private fun closeClientSafely(client: Client) {
        try {
            client.close()
            SLog.d(TAG, "Previous client instance closed successfully")
        } catch (e: Exception) {
            SLog.w(TAG, "Error closing client instance (non-fatal): ${e.message}")
        }
    }

    /**
     * Resolve the userId and deviceId for the current session.
     *
     * Resolution order:
     * 1. Try to load from cached whoami file in the store directory (keyed by accessToken hash)
     * 2. Call /_matrix/client/v3/account/whoami API
     * 3. If whoami fails (network error, token expired), throw an exception
     *    since userId/deviceId are required for correct Session construction.
     */
    internal fun resolveWhoami(context: Context, setting: MatrixSetting): WhoamiInfo {
        val token = setting.accessToken.trim()

        // Try loading from cache first (keyed by token hash to find the right store dir)
        val cachedResult = loadWhoamiCacheByToken(context, token)
        if (cachedResult != null) {
            SLog.d(TAG, "Using cached whoami result for userId=${cachedResult.userId}")
            return cachedResult
        }

        // Call /whoami API
        val homeserver = setting.homeserver.trim().trimEnd('/')
        val url = "$homeserver/_matrix/client/v3/account/whoami"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $token")
            .build()

        return try {
            val client = MatrixUtils.buildClient(setting)
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body.string()
                    val json = JSONObject(body)
                    val userId = json.getString("user_id")
                    val deviceId = json.optString("device_id", "")
                    if (userId.isBlank()) {
                        throw IllegalStateException("whoami returned blank user_id")
                    }
                    SLog.d(TAG, "whoami resolved: userId=$userId, deviceId=$deviceId")
                    WhoamiInfo(userId = userId, deviceId = deviceId)
                } else {
                    val code = response.code
                    SLog.e(TAG, "whoami failed with HTTP $code")
                    throw IllegalStateException(
                        "whoami API failed with HTTP $code. " +
                            "Cannot construct Session without userId/deviceId."
                    )
                }
            }
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            SLog.e(TAG, "whoami request failed: ${e.message}")
            throw IllegalStateException(
                "whoami request failed: ${e.message}. " +
                    "Cannot construct Session without userId/deviceId.",
                e
            )
        }
    }

    /**
     * Try to load cached whoami info by scanning existing store directories for a matching token.
     * The cache file stores the accessToken hash so we can validate it matches.
     */
    private fun loadWhoamiCacheByToken(context: Context, accessToken: String): WhoamiInfo? {
        val tokenHash = sha256Hex(accessToken).take(16)
        val cryptoDir = File(context.filesDir, "matrix-crypto")
        if (!cryptoDir.exists()) return null

        // Scan existing store directories for a matching cache file
        val storeDirs = cryptoDir.listFiles() ?: return null
        for (dir in storeDirs) {
            if (!dir.isDirectory) continue
            val cacheFile = File(dir, WHOAMI_CACHE_FILENAME)
            if (!cacheFile.exists()) continue
            try {
                val json = JSONObject(cacheFile.readText())
                val cachedTokenHash = json.optString("token_hash", "")
                if (cachedTokenHash == tokenHash) {
                    val userId = json.getString("user_id")
                    val deviceId = json.optString("device_id", "")
                    if (userId.isNotBlank()) {
                        return WhoamiInfo(userId = userId, deviceId = deviceId)
                    }
                }
            } catch (e: Exception) {
                SLog.w(TAG, "Failed to read whoami cache from ${dir.name}: ${e.message}")
                // Continue scanning other directories
            }
        }
        return null
    }

    /**
     * Save whoami result to the store directory for future use.
     * Includes a token hash so we can validate the cache matches the current token.
     */
    private fun saveWhoamiCache(storeDir: File, whoami: WhoamiInfo, accessToken: String) {
        val tokenHash = sha256Hex(accessToken).take(16)
        val cacheFile = File(storeDir, WHOAMI_CACHE_FILENAME)
        try {
            val json = JSONObject().apply {
                put("user_id", whoami.userId)
                put("device_id", whoami.deviceId)
                put("token_hash", tokenHash)
            }
            cacheFile.writeText(json.toString())
        } catch (e: Exception) {
            SLog.w(TAG, "Failed to write whoami cache: ${e.message}")
            // Non-fatal: next time we'll just call /whoami again
        }
    }

    /**
     * Compute user-isolated store directory path.
     * Format: {appFilesDir}/matrix-crypto/{sha256(userId).take(16)}/
     */
    internal fun getStoreDir(context: Context, userId: String): File {
        val hash = sha256Hex(userId).take(16)
        val dir = File(context.filesDir, "matrix-crypto/$hash")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Pure routing decision function.
     *
     * Determines whether to use the E2EE send path or the plaintext send path
     * based on two boolean conditions:
     * - e2eeAvailable: whether the E2EE module is loaded and ready
     * - roomEncrypted: whether the target room has encryption enabled
     *
     * Returns true (use E2EE path) if and only if BOTH conditions are true.
     * Otherwise returns false (use plaintext path).
     */
    internal fun shouldUseE2ee(e2eeAvailable: Boolean, roomEncrypted: Boolean): Boolean {
        return e2eeAvailable && roomEncrypted
    }

    /**
     * Defines the ordered sequence of operations in the E2EE send pipeline.
     *
     * This function represents the structural contract that to-device event processing
     * (via sync) MUST complete before message encryption begins. The ordering is:
     * 1. "sync_to_device" — poll and process to-device events to update session state
     * 2. "encrypt_and_send" — encrypt the message using the latest session state and send
     *
     * This ordering guarantees that the latest Olm/Megolm session state (including any
     * new room keys received via to-device messages) is used for encryption.
     *
     * @param operations A list of operation names representing the execution plan.
     *   Valid operations: "sync_to_device", "encrypt_and_send"
     * @return true if the ordering constraint is satisfied (sync before encrypt),
     *   false otherwise.
     */
    internal fun verifySendOperationOrder(operations: List<String>): Boolean {
        val syncIndex = operations.indexOf("sync_to_device")
        val encryptIndex = operations.indexOf("encrypt_and_send")

        // Both operations must be present
        if (syncIndex == -1 || encryptIndex == -1) return false

        // sync_to_device must come before encrypt_and_send
        return syncIndex < encryptIndex
    }

    /**
     * Returns the canonical operation sequence for an E2EE send pipeline execution.
     *
     * The returned list always places "sync_to_device" before "encrypt_and_send",
     * reflecting the implementation in [sendEncrypted] where [ensureKeySyncReady]
     * is called before [room.sendRaw()].
     *
     * This is the ground-truth ordering that [sendEncrypted] follows.
     */
    internal fun getCanonicalSendOperationOrder(): List<String> {
        return listOf("sync_to_device", "encrypt_and_send")
    }

    /**
     * Defines the serialized E2EE send pipeline used around [sendEncrypted].
     *
     * Only one Matrix E2EE send may manipulate the shared SDK client, room key,
     * device-key sync, sendRaw(), and post-send sync at a time.
     */
    internal fun getCanonicalSerializedSendOperationOrder(): List<String> {
        return listOf(
            "acquire_send_lock",
            "sync_to_device",
            "encrypt_and_send",
            "post_send_sync",
            "release_send_lock",
        )
    }

    internal fun verifySerializedSendOperationOrder(operations: List<String>): Boolean {
        val acquireIndex = operations.indexOf("acquire_send_lock")
        val syncIndex = operations.indexOf("sync_to_device")
        val encryptIndex = operations.indexOf("encrypt_and_send")
        val postSendIndex = operations.indexOf("post_send_sync")
        val releaseIndex = operations.indexOf("release_send_lock")

        if (acquireIndex == -1 ||
            syncIndex == -1 ||
            encryptIndex == -1 ||
            postSendIndex == -1 ||
            releaseIndex == -1
        ) {
            return false
        }

        return acquireIndex < syncIndex &&
            syncIndex < encryptIndex &&
            encryptIndex < postSendIndex &&
            postSendIndex < releaseIndex
    }

    /**
     * Compute SHA-256 hex string for a given input.
     * This is a pure deterministic function.
     */
    internal fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    internal suspend fun forceClearDeviceStore(context: Context, setting: MatrixSetting) {
        val safeSetting = SenderSettingSanitizer.sanitizeMatrixSetting(setting)
        val useLoginMode = safeSetting.username.isNotBlank() && safeSetting.password.isNotBlank()

        clientMutex.withLock {
            cachedClient?.let { client ->
                SLog.w(TAG, "Force clearing device: closing existing client")
                closeClientSafely(client)
                cachedClient = null
            }
            cachedAccessToken = null
            initialSyncDone = false

            val cacheKey = if (useLoginMode) safeSetting.username.trim() else safeSetting.accessToken.trim()
            if (cacheKey.isNotBlank()) {
                val storeDir = getStoreDir(context, cacheKey)
                SLog.w(TAG, "Force clearing device: deleting store directory $storeDir")
                storeDir.deleteRecursively()
                storeDir.mkdirs()
            }
        }
    }
}
