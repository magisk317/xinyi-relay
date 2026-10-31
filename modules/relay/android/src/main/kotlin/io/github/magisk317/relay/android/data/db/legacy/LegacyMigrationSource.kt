package io.github.magisk317.relay.android.data.db.legacy

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import io.github.magisk317.relay.android.data.db.entity.ScheduledTaskEntity
import io.github.magisk317.relay.android.data.db.legacy.entity.LegacyAppInfo
import io.github.magisk317.relay.android.data.db.legacy.entity.LegacyForwardFilterRule
import io.github.magisk317.relay.android.data.db.legacy.entity.LegacyNotifyRouteRule
import io.github.magisk317.relay.android.data.db.legacy.entity.LegacyRule
import io.github.magisk317.relay.android.data.db.legacy.entity.LegacySender
import io.github.magisk317.relay.android.data.db.legacy.entity.LegacySmsMsg
import io.github.magisk317.relay.android.data.db.entity.SenderDispatchLog
import io.github.magisk317.xposed.contract.sim.SmsRoutingMetadata

/** Rows read from the legacy relay_room.db tables, ready for the importer projections. */
data class LegacyConfigRows(
    val senders: List<LegacySender>,
    val routes: List<LegacyNotifyRouteRule>,
    val filters: List<LegacyForwardFilterRule>,
    val tasks: List<ScheduledTaskEntity>,
    val appInfos: List<LegacyAppInfo>,
    val rules: List<LegacyRule>,
)

/**
 * Raw read/write access to the legacy relay_room.db during the one-shot
 * forward.db migration. Reads and the drain marker run through raw SQLite so
 * the bootstrapper never needs a Room handle on the legacy schema, which is
 * what lets the v34 schema drop the legacy entity types entirely.
 *
 * Every read tolerates an absent table: a device whose v34 open ran before the
 * bootstrapper (ContentProvider eager init) keeps its tables, and re-running
 * the migration later must not crash on them.
 */
interface LegacyMigrationSource : AutoCloseable {
    /** True when a relay_room.db file exists at all (false on fresh installs). */
    fun legacyFileExists(): Boolean

    fun readSmsMsgs(): List<LegacySmsMsg>

    fun readDispatchLogs(): List<SenderDispatchLog>

    fun readConfig(): LegacyConfigRows

    /** Records that the import completed; the caller drops the tables after this. */
    fun markDrained()

    /** Best-effort removal of the drained legacy tables. */
    fun dropLegacyTables()

    override fun close()
}

class LegacyRawSource(private val context: Context) : LegacyMigrationSource {
    @Volatile
    private var openedDb: SQLiteDatabase? = null

    private val db: SQLiteDatabase
        get() = openedDb ?: synchronized(this) {
            openedDb ?: run {
                val path = context.getDatabasePath(DATABASE_FILE).absolutePath
                SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE, null)
                    .also { openedDb = it }
            }
        }

    override fun legacyFileExists(): Boolean = context.getDatabasePath(DATABASE_FILE).exists()

    override fun readSmsMsgs(): List<LegacySmsMsg> = readRows("sms_msg") { c ->
        LegacySmsMsg(
            id = c.optLong("id"),
            sender = c.optStringOrNull("sender"),
            body = c.optStringOrNull("body"),
            date = c.optLong("date"),
            processedTime = c.optLong("processed_time"),
            company = c.optStringOrNull("company"),
            smsCode = c.optStringOrNull("sms_code"),
            packageName = c.optStringOrNull("package_name"),
            notifyChannelId = c.optString("notify_channel_id"),
            simSlot = c.optInt("sim_slot", SmsRoutingMetadata.UNKNOWN_SIM_SLOT),
            subId = c.optInt("sub_id", SmsRoutingMetadata.UNKNOWN_SUB_ID),
            contactName = c.optString("contact_name"),
            phoneArea = c.optString("phone_area"),
            forwardStatus = c.optInt("forward_status"),
            forwardTarget = c.optStringOrNull("forward_target"),
            forwardMessage = c.optStringOrNull("forward_message"),
            forwardTime = c.optLong("forward_time"),
            msgType = c.optInt("msg_type", LegacySmsMsg.MSG_TYPE_SMS),
            callType = c.optInt("call_type"),
            sessionKey = c.optString("session_key"),
        )
    }

    override fun readDispatchLogs(): List<SenderDispatchLog> = readRows("sender_dispatch_log") { c ->
        SenderDispatchLog(
            id = c.optLong("id"),
            recordId = if (c.isNull(c.optColumn("record_id"))) null else c.getLong(c.optColumn("record_id")),
            senderId = c.optLong("sender_id"),
            senderType = c.optInt("sender_type"),
            msgType = c.optInt("msg_type"),
            success = c.optInt("success") != 0,
            createdAt = c.optLong("created_at"),
        )
    }

    override fun readConfig(): LegacyConfigRows = LegacyConfigRows(
        senders = readRows("Sender") { c ->
            LegacySender(
                id = c.optLong("id"),
                type = c.optInt("type", 1),
                name = c.optString("name"),
                jsonSetting = c.optString("json_setting"),
                status = c.optInt("status", 1),
                time = java.util.Date(c.optLong("time")),
                receiveCode = c.optInt("receive_code", 1),
                receiveNonCode = c.optInt("receive_non_code"),
                receiveAppNotify = c.optInt("receive_app_notify", 1),
                receiveCallNotify = c.optInt("receive_call_notify"),
                activeScheduleJson = c.optString("active_schedule_json"),
                priority = c.optInt("priority"),
                customTemplate = c.optString("custom_template"),
            )
        },
        routes = readRows("notify_route_rule") { c ->
            LegacyNotifyRouteRule(
                id = c.optLong("id"),
                scope = c.optInt("scope"),
                packageName = c.optString("package_name"),
                senderId = c.optLong("sender_id"),
                updateTime = c.optLong("update_time"),
            )
        },
        filters = readRows("forward_filter_rule") { c ->
            LegacyForwardFilterRule(
                id = c.optLong("id"),
                msgType = c.optString("msg_type"),
                scopeType = c.optString("scope_type"),
                scopeKey = c.optString("scope_key"),
                senderId = c.optLong("sender_id"),
                policy = c.optString("policy"),
                matchMode = c.optString("match_mode"),
                pattern = c.optString("pattern"),
                enabled = c.optInt("enabled", 1),
                updateTime = c.optLong("update_time"),
            )
        },
        tasks = readRows("scheduled_task") { c ->
            ScheduledTaskEntity(
                id = c.optLong("id"),
                name = c.optString("name"),
                taskType = c.optString("task_type"),
                cronExpression = c.optString("cron_expression"),
                simSlot = c.optInt("sim_slot"),
                mobiles = c.optString("mobiles"),
                content = c.optString("content"),
                status = c.optInt("status", 1),
                lastRunTime = c.optLong("last_run_time"),
                nextRunTime = c.optLong("next_run_time"),
                createdAt = c.optLong("created_at"),
            )
        },
        appInfos = readRows("app_info") { c ->
            LegacyAppInfo(
                packageName = c.optString("package_name"),
                label = c.optStringOrNull("label"),
                blocked = c.optInt("blocked") != 0,
                forwarding = c.optInt("forwarding") != 0,
                forwardingConfigured = c.optInt("forwarding_configured") != 0,
                notifyTemplate = c.optString("notify_template"),
            )
        },
        rules = readRows("Rule") { c ->
            LegacyRule(
                id = c.optLong("id"),
                type = c.optString("type", "sms"),
                filed = c.optString("filed", "transpond_all"),
                check = c.optString("check", "is"),
                value = c.optString("value"),
                senderId = c.optLong("sender_id"),
                smsTemplate = c.optString("sms_template"),
                regexReplace = c.optString("regex_replace"),
                simSlot = c.optString("sim_slot", "ALL"),
                status = c.optInt("status", 1),
                time = java.util.Date(c.optLong("time")),
                senderList = emptyList(),
                senderLogic = c.optString("sender_logic", "ALL"),
                silentPeriodStart = c.optInt("silent_period_start"),
                silentPeriodEnd = c.optInt("silent_period_end"),
                silentDayOfWeek = c.optString("silent_day_of_week"),
                title = c.optString("title"),
            )
        },
    )

    override fun markDrained() {
        db.execSQL("CREATE TABLE IF NOT EXISTS legacy_import_done (proceed INTEGER NOT NULL)")
        db.execSQL("INSERT OR REPLACE INTO legacy_import_done (proceed) VALUES (1)")
    }

    override fun dropLegacyTables() {
        LEGACY_DRAINABLE_TABLES.forEach { table ->
            db.execSQL("DROP TABLE IF EXISTS `$table`")
        }
        db.execSQL("DROP TABLE IF EXISTS legacy_import_done")
    }

    override fun close() {
        runCatching { openedDb?.close() }
        openedDb = null
    }

    private fun tableExists(table: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table))
            .use { it.count > 0 }

    private fun <T> readRows(table: String, map: (Cursor) -> T): List<T> {
        if (!tableExists(table)) return emptyList()
        return db.query(table, null, null, null, null, null, null).use { c ->
            val rows = mutableListOf<T>()
            while (c.moveToNext()) rows += map(c)
            rows
        }
    }

    private fun Cursor.optColumn(name: String): Int = getColumnIndexOrThrow(name)

    private fun Cursor.optLong(name: String, def: Long = 0L): Long {
        val i = getColumnIndex(name)
        return if (i < 0 || isNull(i)) def else getLong(i)
    }

    private fun Cursor.optInt(name: String, def: Int = 0): Int {
        val i = getColumnIndex(name)
        return if (i < 0 || isNull(i)) def else getInt(i)
    }

    private fun Cursor.optStringOrNull(name: String): String? {
        val i = getColumnIndex(name)
        return if (i < 0 || isNull(i)) null else getString(i)
    }

    private fun Cursor.optString(name: String, def: String = ""): String = optStringOrNull(name) ?: def

    companion object {
        const val DATABASE_FILE = "relay_room.db"

        /** The Room entity tables removed in v34; dropped after a successful import. */
        val LEGACY_DRAINABLE_TABLES = listOf(
            "sms_msg",
            "app_info",
            "Sender",
            "Rule",
            "forward_filter_rule",
            "notify_route_rule",
            "sender_dispatch_log",
            "scheduled_task",
        )

    }
}
