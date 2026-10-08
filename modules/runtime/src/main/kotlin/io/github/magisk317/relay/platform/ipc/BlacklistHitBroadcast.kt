package io.github.magisk317.relay.platform.ipc

import android.content.Context
import android.content.Intent
import android.os.Process
import io.github.magisk317.relay.contract.xpbridge.XpSmsBlacklistHitRecord
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * Cross-process transport for blacklist hit records.
 *
 * The SMS hooks run inside the phone process (UID != app), so they cannot open the relay app's
 * private Room database directly. Instead they reuse the forward broadcast channel (same action,
 * same IPC token) to hand the hit record to [ForwardReceiver], which persists it from the app
 * process. This mirrors how forwarded SMS already cross the process boundary.
 */
object BlacklistHitBroadcast {
    /**
     * Dispatch a blacklist hit record from a hook process to the app process.
     * Fire-and-forget: the actual row id is unknown to the sender.
     */
    fun dispatch(context: Context, hit: XpSmsBlacklistHitRecord): Boolean {
        val startedAt = System.nanoTime()
        val intent = buildIntent(context, hit)
        val result = ForwardBroadcastDispatcher.dispatchFromSmsHook(
            context = context,
            payload = ForwardBroadcastPayload(
                sender = hit.sender,
                body = hit.body,
                date = hit.smsDate,
                msgType = ForwardBroadcastContract.MSG_TYPE_BLACKLIST_HIT,
                forwardSource = ForwardBroadcastContract.SOURCE_SMS_HOOK,
                eventId = hit.eventId,
            ),
            sentFromUid = Process.myUid(),
            dispatchBlock = { resolvedToken ->
                ForwardBroadcastContract.putIpcToken(intent, resolvedToken)
                context.sendBroadcast(intent)
            },
        )
        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        MagiskOtel.event(
            name = "sms.block",
            attributes = mapOf(
                "result" to if (result.dispatched) "ok" else "error",
                "duration_ms" to durationMs.toString(),
                "process" to "hook",
                "stage" to "blacklist_hit_ipc",
                "reason" to if (result.dispatched) "dispatched" else "dispatch_failed",
                "source" to hit.source.ifBlank { "unknown" },
            ),
            statusOk = result.dispatched,
        )
        return result.dispatched
    }

    private fun buildIntent(context: Context, hit: XpSmsBlacklistHitRecord): Intent {
        return ForwardReceiverIntentFactory.newHostIntent(context).apply {
            putExtra(ForwardBroadcastContract.EXTRA_MSG_TYPE, ForwardBroadcastContract.MSG_TYPE_BLACKLIST_HIT)
            putExtra(ForwardBroadcastContract.EXTRA_FORWARD_SOURCE, ForwardBroadcastContract.SOURCE_SMS_HOOK)
            putExtra(ForwardBroadcastContract.EXTRA_EVENT_ID, hit.eventId)
            putExtra(BLACKLIST_HIT_SOURCE, hit.source)
            putExtra(ForwardBroadcastContract.EXTRA_SENDER, hit.sender)
            putExtra(ForwardBroadcastContract.EXTRA_BODY, hit.body)
            putExtra(ForwardBroadcastContract.EXTRA_DATE, hit.smsDate)
            putExtra(ForwardBroadcastContract.EXTRA_MATCH_TYPE, hit.matchType)
            putExtra(ForwardBroadcastContract.EXTRA_PATTERN, hit.pattern)
            putExtra(ForwardBroadcastContract.EXTRA_ACTION_DELETE, hit.actionDelete)
            putExtra(ForwardBroadcastContract.EXTRA_ACTION_BLOCK, hit.actionBlock)
            putExtra(ForwardBroadcastContract.EXTRA_BLOCK_REASON, hit.blockReason)
            putExtra(ForwardBroadcastContract.EXTRA_CREATED_AT, hit.createdAt)
        }
    }

    /**
     * Reconstruct the hit record on the app side. Returns null when the broadcast is not a
     * blacklist hit (so the caller can fall through to the normal forward path).
     */
    fun fromIntent(intent: Intent): XpSmsBlacklistHitRecord? {
        val msgType = intent.getStringExtra(ForwardBroadcastContract.EXTRA_MSG_TYPE)
        if (msgType != ForwardBroadcastContract.MSG_TYPE_BLACKLIST_HIT) return null
        val eventId = intent.getStringExtra(ForwardBroadcastContract.EXTRA_EVENT_ID).orEmpty()
        val source = intent.getStringExtra(BLACKLIST_HIT_SOURCE).orEmpty()
        if (eventId.isBlank() || source.isBlank()) return null
        return XpSmsBlacklistHitRecord(
            eventId = eventId,
            source = source,
            sender = intent.getStringExtra(ForwardBroadcastContract.EXTRA_SENDER),
            body = intent.getStringExtra(ForwardBroadcastContract.EXTRA_BODY),
            smsDate = intent.getLongExtra(ForwardBroadcastContract.EXTRA_DATE, 0L),
            matchType = intent.getStringExtra(ForwardBroadcastContract.EXTRA_MATCH_TYPE),
            pattern = intent.getStringExtra(ForwardBroadcastContract.EXTRA_PATTERN),
            actionDelete = intent.getBooleanExtra(ForwardBroadcastContract.EXTRA_ACTION_DELETE, false),
            actionBlock = intent.getBooleanExtra(ForwardBroadcastContract.EXTRA_ACTION_BLOCK, false),
            blockReason = intent.getStringExtra(ForwardBroadcastContract.EXTRA_BLOCK_REASON),
            createdAt = intent.getLongExtra(
                ForwardBroadcastContract.EXTRA_CREATED_AT,
                System.currentTimeMillis(),
            ),
        )
    }

    private const val BLACKLIST_HIT_SOURCE = "blacklist_hit_source"
}
