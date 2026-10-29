package io.github.magisk317.relay.android.data.db

import io.github.magisk317.forward.db.ForwardDatabase
import io.github.magisk317.forward.db.ForwardEventEntity
import io.github.magisk317.forward.db.SenderDispatchLogEntity
import io.github.magisk317.smscode.db.entity.SmsMsg

/**
 * One-shot import of the legacy mixed schema into [ForwardDatabase].
 *
 * The pure projection/parity functions are separated from the Room I/O so the
 * mapping can be unit tested on the JVM. The old `sms_msg` rows keep living
 * until the product ships the field strip; the import only fills the new
 * database and proves parity.
 */
object ForwardLegacyImporter {

    data class ImportReport(
        val eventsImported: Int,
        val dispatchLogsImported: Int,
        val parity: ParityReport,
    )

    data class ParityReport(
        val eventCountMatches: Boolean,
        val dispatchLogCountMatches: Boolean,
        val firstEventMismatch: String? = null,
        val firstDispatchLogMismatch: String? = null,
    ) {
        val ok: Boolean
            get() = eventCountMatches && dispatchLogCountMatches &&
                firstEventMismatch == null && firstDispatchLogMismatch == null
    }

    /** Projects one legacy mixed row onto the forward-domain event record. */
    fun projectEvent(msg: SmsMsg): ForwardEventEntity = ForwardEventEntity(
        id = msg.id ?: 0,
        eventId = "",
        msgType = msg.msgType,
        sender = msg.sender,
        body = msg.body,
        date = msg.date,
        processedTime = msg.processedTime,
        company = msg.company,
        smsCode = msg.smsCode,
        packageName = msg.packageName,
        notifyChannelId = msg.notifyChannelId,
        callType = msg.callType,
        simSlot = msg.simSlot,
        subId = msg.subId,
        contactName = msg.contactName,
        phoneArea = msg.phoneArea,
        sessionKey = msg.sessionKey,
        forwardStatus = msg.forwardStatus,
        forwardTarget = msg.forwardTarget,
        forwardMessage = msg.forwardMessage,
        forwardTime = msg.forwardTime,
    )

    /**
     * Projects a legacy dispatch log row; [recordIdMap] translates the legacy
     * sms_msg row id to the forward_event id written by the event import.
     */
    fun projectDispatchLog(
        log: io.github.magisk317.relay.android.data.db.entity.SenderDispatchLog,
        recordIdMap: Map<Long, Long>,
    ): SenderDispatchLogEntity = SenderDispatchLogEntity(
        recordId = log.recordId?.let { recordIdMap[it] },
        senderId = log.senderId,
        senderType = log.senderType,
        msgType = log.msgType,
        success = log.success,
        createdAt = log.createdAt,
    )

    fun checkParity(
        legacy: List<SmsMsg>,
        imported: List<ForwardEventEntity>,
        legacyLogs: List<io.github.magisk317.relay.android.data.db.entity.SenderDispatchLog>,
        importedLogs: List<SenderDispatchLogEntity>,
        recordIdMap: Map<Long, Long>,
    ): ParityReport {
        val eventCountMatches = legacy.size == imported.size
        val logCountMatches = legacyLogs.size == importedLogs.size
        val byLegacyId = imported.associateBy { it.id }
        val firstEventMismatch = legacy.firstNotNullOfOrNull { msg ->
            val projected = projectEvent(msg)
            val actual = msg.id?.let { byLegacyId[it] }
            when {
                actual == null -> "missing event for legacy id=${msg.id}"
                actual.msgType != projected.msgType -> "msgType ${actual.msgType} != ${projected.msgType} id=${msg.id}"
                actual.date != projected.date -> "date ${actual.date} != ${projected.date} id=${msg.id}"
                actual.forwardStatus != projected.forwardStatus -> "forwardStatus ${actual.forwardStatus} != ${projected.forwardStatus} id=${msg.id}"
                actual.sender != projected.sender -> "sender mismatch id=${msg.id}"
                actual.body != projected.body -> "body mismatch id=${msg.id}"
                else -> null
            }
        }
        val importedBySignature = importedLogs.groupBy { Triple(it.senderId, it.createdAt, it.success) }
        val firstDispatchLogMismatch = legacyLogs.firstNotNullOfOrNull { log ->
            val expectedRecordId = log.recordId?.let { recordIdMap[it] }
            val candidates = importedBySignature[Triple(log.senderId, log.createdAt, log.success)].orEmpty()
            if (candidates.none { it.recordId == expectedRecordId }) {
                "dispatch log senderId=${log.senderId} createdAt=${log.createdAt} recordId=$expectedRecordId not imported"
            } else {
                null
            }
        }
        return ParityReport(
            eventCountMatches = eventCountMatches,
            dispatchLogCountMatches = logCountMatches,
            firstEventMismatch = firstEventMismatch,
            firstDispatchLogMismatch = firstDispatchLogMismatch,
        )
    }

    /** Room I/O shell: import everything and prove parity. Run once per empty target. */
    suspend fun importAll(legacy: AppDatabase, target: ForwardDatabase): ImportReport {
        val msgs = legacy.smsMsgDao().getAll()
        val logs = legacy.senderDispatchLogDao().getAllForImport()
        val events = msgs.map { projectEvent(it) }
        val legacyIds = msgs.mapNotNull { it.id }
        val insertedIds = target.forwardEventDao().insertAll(events)
        val recordIdMap = legacyIds.zip(insertedIds).toMap()
        val dispatchLogs = logs.map { projectDispatchLog(it, recordIdMap) }
        target.senderDispatchLogDao().insertAll(dispatchLogs)
        val importedEvents = insertedIds.mapNotNull { target.forwardEventDao().byId(it) }
        val importedLogs = recordIdMap.values.flatMap { target.senderDispatchLogDao().byRecordId(it) }
        val parity = checkParity(msgs, importedEvents, logs, importedLogs, recordIdMap)
        return ImportReport(
            eventsImported = events.size,
            dispatchLogsImported = dispatchLogs.size,
            parity = parity,
        )
    }
}
