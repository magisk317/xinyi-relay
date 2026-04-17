package io.github.magisk317.relay.data.db

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.magisk317.relay.data.db.entity.ForwardFilterRuleEntity
import io.github.magisk317.relay.data.db.dao.RuleDao
import io.github.magisk317.relay.data.db.dao.SenderDao
import io.github.magisk317.relay.data.db.ext.ConvertersDate
import io.github.magisk317.relay.data.db.ext.ConvertersSenderList
import io.github.magisk317.relay.data.db.entity.RuleEntity
import io.github.magisk317.relay.data.db.entity.SenderEntity
import io.github.magisk317.relay.data.db.dao.AppInfoDao
import io.github.magisk317.relay.data.db.dao.AutoInputEventDao
import io.github.magisk317.relay.data.db.dao.ForwardFilterRuleDao
import io.github.magisk317.relay.data.db.dao.NotifyRouteRuleDao
import io.github.magisk317.relay.data.db.dao.SenderDispatchLogDao
import io.github.magisk317.relay.data.db.dao.SmsCodeRuleDao
import io.github.magisk317.relay.data.db.dao.SmsMsgDao
import io.github.magisk317.relay.data.db.entity.AppInfo
import io.github.magisk317.relay.data.db.entity.AutoInputEvent
import io.github.magisk317.relay.data.db.entity.NotifyRouteRule
import io.github.magisk317.relay.data.db.entity.SmsCodeRule
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.data.db.entity.SenderDispatchLog
import io.github.magisk317.relay.common.utils.XLog

@Database(entities = [
    SmsCodeRule::class,
    SmsMsg::class,
    AppInfo::class,
    AutoInputEvent::class,
    SenderDispatchLog::class,
    NotifyRouteRule::class,
    ForwardFilterRuleEntity::class,
    SenderEntity::class,
    RuleEntity::class
], version = 26, exportSchema = false)
@TypeConverters(ConvertersDate::class, ConvertersSenderList::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun smsCodeRuleDao(): SmsCodeRuleDao
    abstract fun smsMsgDao(): SmsMsgDao
    abstract fun appInfoDao(): AppInfoDao
    abstract fun autoInputEventDao(): AutoInputEventDao
    abstract fun senderDispatchLogDao(): SenderDispatchLogDao
    abstract fun notifyRouteRuleDao(): NotifyRouteRuleDao
    abstract fun forwardFilterRuleDao(): ForwardFilterRuleDao
    abstract fun ruleDao(): RuleDao
    abstract fun senderDao(): SenderDao

    companion object {
        internal const val DATABASE_NAME = "relay_room.db"
        private val PREVIOUS_DATABASE_NAMES = listOf("xrelay_room.db", "xsmscode_room.db")

        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN package_name TEXT",
                    migration = "1_2",
                )
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "DELETE FROM sms_msg WHERE id NOT IN " +
                        "(SELECT MIN(id) FROM sms_msg GROUP BY sender, body, date)",
                    migration = "2_3",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE UNIQUE INDEX IF NOT EXISTS index_sms_msg_sender_body_date " +
                        "ON sms_msg(sender, body, date)",
                    migration = "2_3",
                )
            }
        }

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN forward_status INTEGER NOT NULL DEFAULT 0",
                    migration = "3_4",
                )
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN forward_target TEXT",
                    migration = "3_4",
                )
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN forward_message TEXT",
                    migration = "3_4",
                )
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN forward_time INTEGER NOT NULL DEFAULT 0",
                    migration = "3_4",
                )
            }
        }

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "CREATE TABLE IF NOT EXISTS Sender (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "type INTEGER NOT NULL DEFAULT 1, " +
                        "name TEXT NOT NULL DEFAULT '', " +
                        "json_setting TEXT NOT NULL DEFAULT '', " +
                        "status INTEGER NOT NULL DEFAULT 1, " +
                        "time INTEGER NOT NULL" +
                        ")",
                    migration = "4_5",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE TABLE IF NOT EXISTS Rule (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "type TEXT NOT NULL DEFAULT 'sms', " +
                        "filed TEXT NOT NULL DEFAULT 'transpond_all', " +
                        "`check` TEXT NOT NULL DEFAULT 'is', " +
                        "value TEXT NOT NULL DEFAULT '', " +
                        "sender_id INTEGER NOT NULL DEFAULT 0, " +
                        "sms_template TEXT NOT NULL DEFAULT '', " +
                        "regex_replace TEXT NOT NULL DEFAULT '', " +
                        "sim_slot TEXT NOT NULL DEFAULT 'ALL', " +
                        "status INTEGER NOT NULL DEFAULT 1, " +
                        "time INTEGER NOT NULL, " +
                        "sender_list TEXT NOT NULL DEFAULT '', " +
                        "sender_logic TEXT NOT NULL DEFAULT 'ALL', " +
                        "silent_period_start INTEGER NOT NULL DEFAULT 0, " +
                        "silent_period_end INTEGER NOT NULL DEFAULT 0, " +
                        "silent_day_of_week TEXT NOT NULL DEFAULT '', " +
                        "title TEXT NOT NULL DEFAULT '', " +
                        "FOREIGN KEY(sender_id) REFERENCES Sender(id) ON UPDATE CASCADE ON DELETE CASCADE" +
                        ")",
                    migration = "4_5",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE UNIQUE INDEX IF NOT EXISTS index_Rule_id ON Rule(id)",
                    migration = "4_5",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_Rule_sender_id ON Rule(sender_id)",
                    migration = "4_5",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_Rule_sender_list ON Rule(sender_list)",
                    migration = "4_5",
                )
            }
        }

        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE Sender ADD COLUMN receive_non_code INTEGER NOT NULL DEFAULT 0",
                    migration = "5_6",
                )
            }
        }

        private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Bridge migration kept as no-op so previous databases can connect to the 7+ chain.
            }
        }

        private val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN msg_type INTEGER NOT NULL DEFAULT 0",
                    migration = "7_8",
                )
            }
        }

        private val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE app_info ADD COLUMN forwarding INTEGER NOT NULL DEFAULT 0",
                    migration = "8_9",
                )
            }
        }


        private val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Ensure forwarding column exists in app_info if it wasn't added in 8_9
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE app_info ADD COLUMN forwarding INTEGER NOT NULL DEFAULT 0",
                    migration = "9_10",
                )
            }
        }

        private val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE app_info ADD COLUMN notify_template TEXT NOT NULL DEFAULT ''",
                    migration = "10_11",
                )
            }
        }

        private val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE Sender ADD COLUMN receive_app_notify INTEGER NOT NULL DEFAULT 1",
                    migration = "11_12",
                )
            }
        }

        private val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "DROP INDEX IF EXISTS index_sms_msg_sender_body_date",
                    migration = "12_13",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE UNIQUE INDEX IF NOT EXISTS index_sms_msg_sender_body_date_msg_type " +
                        "ON sms_msg(sender, body, date, msg_type)",
                    migration = "12_13",
                )
            }
        }

        private val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE Sender ADD COLUMN receive_code INTEGER NOT NULL DEFAULT 1",
                    migration = "13_14",
                )
            }
        }

        private val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE Sender ADD COLUMN receive_call_notify INTEGER NOT NULL DEFAULT 0",
                    migration = "14_15",
                )
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN call_type INTEGER NOT NULL DEFAULT 0",
                    migration = "14_15",
                )
            }
        }

        private val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "CREATE TABLE IF NOT EXISTS notify_route_rule (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "scope INTEGER NOT NULL, " +
                        "package_name TEXT NOT NULL, " +
                        "sender_id INTEGER NOT NULL, " +
                        "update_time INTEGER NOT NULL DEFAULT 0" +
                        ")",
                    migration = "15_16",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE UNIQUE INDEX IF NOT EXISTS index_notify_route_rule_scope_package_sender " +
                        "ON notify_route_rule(scope, package_name, sender_id)",
                    migration = "15_16",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_notify_route_rule_package_scope " +
                        "ON notify_route_rule(package_name, scope)",
                    migration = "15_16",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_notify_route_rule_sender_scope " +
                        "ON notify_route_rule(sender_id, scope)",
                    migration = "15_16",
                )
            }
        }

        private val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "CREATE TABLE IF NOT EXISTS forward_filter_rule (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "msg_type TEXT NOT NULL, " +
                        "scope_type TEXT NOT NULL, " +
                        "scope_key TEXT NOT NULL DEFAULT '', " +
                        "sender_id INTEGER NOT NULL DEFAULT 0, " +
                        "policy TEXT NOT NULL, " +
                        "match_mode TEXT NOT NULL, " +
                        "pattern TEXT NOT NULL, " +
                        "enabled INTEGER NOT NULL DEFAULT 1, " +
                        "update_time INTEGER NOT NULL DEFAULT 0" +
                        ")",
                    migration = "16_17",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE UNIQUE INDEX IF NOT EXISTS index_forward_filter_rule_unique " +
                        "ON forward_filter_rule(msg_type, scope_type, scope_key, sender_id, policy, match_mode, pattern)",
                    migration = "16_17",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_forward_filter_rule_msg_scope_key " +
                        "ON forward_filter_rule(msg_type, scope_type, scope_key)",
                    migration = "16_17",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_forward_filter_rule_msg_scope_sender " +
                        "ON forward_filter_rule(msg_type, scope_type, sender_id)",
                    migration = "16_17",
                )
            }
        }

        private val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN notify_channel_id TEXT NOT NULL DEFAULT ''",
                    migration = "17_18",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_sms_msg_pkg_type_channel_date " +
                        "ON sms_msg(package_name, msg_type, notify_channel_id, date)",
                    migration = "17_18",
                )
            }
        }

        private val MIGRATION_18_19 = object : androidx.room.migration.Migration(18, 19) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "CREATE TABLE IF NOT EXISTS sender_dispatch_log (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "record_id INTEGER, " +
                        "sender_id INTEGER NOT NULL, " +
                        "sender_type INTEGER NOT NULL, " +
                        "msg_type INTEGER NOT NULL, " +
                        "success INTEGER NOT NULL, " +
                        "created_at INTEGER NOT NULL" +
                        ")",
                    migration = "18_19",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_sender_dispatch_type_time " +
                        "ON sender_dispatch_log(sender_type, created_at)",
                    migration = "18_19",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_sender_dispatch_sender_time " +
                        "ON sender_dispatch_log(sender_id, created_at)",
                    migration = "18_19",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_sender_dispatch_record " +
                        "ON sender_dispatch_log(record_id)",
                    migration = "18_19",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE TABLE IF NOT EXISTS auto_input_event (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "record_id INTEGER, " +
                        "package_name TEXT, " +
                        "code_length INTEGER NOT NULL, " +
                        "attempt_at INTEGER NOT NULL, " +
                        "success INTEGER, " +
                        "fail_reason TEXT" +
                        ")",
                    migration = "18_19",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_auto_input_attempt_at " +
                        "ON auto_input_event(attempt_at)",
                    migration = "18_19",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_auto_input_record " +
                        "ON auto_input_event(record_id)",
                    migration = "18_19",
                )
            }
        }

        private val MIGRATION_19_20 = object : androidx.room.migration.Migration(19, 20) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "CREATE TABLE IF NOT EXISTS auto_input_event_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "record_id INTEGER, " +
                        "package_name TEXT, " +
                        "code_length INTEGER NOT NULL, " +
                        "attempt_at INTEGER NOT NULL, " +
                        "success INTEGER, " +
                        "fail_reason TEXT" +
                        ")",
                    migration = "19_20",
                )
                execSqlSafely(
                    db = db,
                    sql = "INSERT INTO auto_input_event_new " +
                        "(id, record_id, package_name, code_length, attempt_at, success, fail_reason) " +
                        "SELECT " +
                        "CASE WHEN id IS NULL THEN NULL ELSE id END, " +
                        "record_id, package_name, code_length, attempt_at, success, fail_reason " +
                        "FROM auto_input_event",
                    migration = "19_20",
                )
                execSqlSafely(
                    db = db,
                    sql = "DROP TABLE IF EXISTS auto_input_event",
                    migration = "19_20",
                )
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE auto_input_event_new RENAME TO auto_input_event",
                    migration = "19_20",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_auto_input_attempt_at " +
                        "ON auto_input_event(attempt_at)",
                    migration = "19_20",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_auto_input_record " +
                        "ON auto_input_event(record_id)",
                    migration = "19_20",
                )
            }
        }

        private val MIGRATION_20_21 = object : androidx.room.migration.Migration(20, 21) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "CREATE TABLE IF NOT EXISTS sender_dispatch_log_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "record_id INTEGER, " +
                        "sender_id INTEGER NOT NULL, " +
                        "sender_type INTEGER NOT NULL, " +
                        "msg_type INTEGER NOT NULL, " +
                        "success INTEGER NOT NULL, " +
                        "created_at INTEGER NOT NULL" +
                        ")",
                    migration = "20_21",
                )
                execSqlSafely(
                    db = db,
                    sql = "INSERT INTO sender_dispatch_log_new " +
                        "(id, record_id, sender_id, sender_type, msg_type, success, created_at) " +
                        "SELECT " +
                        "CASE WHEN id IS NULL THEN NULL ELSE id END, " +
                        "record_id, sender_id, sender_type, msg_type, success, created_at " +
                        "FROM sender_dispatch_log",
                    migration = "20_21",
                )
                execSqlSafely(
                    db = db,
                    sql = "DROP TABLE IF EXISTS sender_dispatch_log",
                    migration = "20_21",
                )
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sender_dispatch_log_new RENAME TO sender_dispatch_log",
                    migration = "20_21",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_sender_dispatch_type_time " +
                        "ON sender_dispatch_log(sender_type, created_at)",
                    migration = "20_21",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_sender_dispatch_sender_time " +
                        "ON sender_dispatch_log(sender_id, created_at)",
                    migration = "20_21",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_sender_dispatch_record " +
                        "ON sender_dispatch_log(record_id)",
                    migration = "20_21",
                )

                execSqlSafely(
                    db = db,
                    sql = "CREATE TABLE IF NOT EXISTS sms_msg_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "sender TEXT, " +
                        "body TEXT, " +
                        "date INTEGER NOT NULL, " +
                        "company TEXT, " +
                        "sms_code TEXT, " +
                        "package_name TEXT, " +
                        "notify_channel_id TEXT NOT NULL DEFAULT '', " +
                        "forward_status INTEGER NOT NULL DEFAULT 0, " +
                        "forward_target TEXT, " +
                        "forward_message TEXT, " +
                        "forward_time INTEGER NOT NULL DEFAULT 0, " +
                        "msg_type INTEGER NOT NULL DEFAULT 0, " +
                        "call_type INTEGER NOT NULL DEFAULT 0" +
                        ")",
                    migration = "20_21",
                )
                execSqlSafely(
                    db = db,
                    sql = "INSERT INTO sms_msg_new " +
                        "(id, sender, body, date, company, sms_code, package_name, notify_channel_id, " +
                        "forward_status, forward_target, forward_message, forward_time, msg_type, call_type) " +
                        "SELECT " +
                        "CASE WHEN id IS NULL THEN NULL ELSE id END, " +
                        "sender, body, " +
                        "COALESCE(date, 0), " +
                        "company, sms_code, package_name, " +
                        "COALESCE(notify_channel_id, ''), " +
                        "COALESCE(forward_status, 0), " +
                        "forward_target, forward_message, " +
                        "COALESCE(forward_time, 0), " +
                        "COALESCE(msg_type, 0), " +
                        "COALESCE(call_type, 0) " +
                        "FROM sms_msg",
                    migration = "20_21",
                )
                execSqlSafely(
                    db = db,
                    sql = "DROP TABLE IF EXISTS sms_msg",
                    migration = "20_21",
                )
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg_new RENAME TO sms_msg",
                    migration = "20_21",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE UNIQUE INDEX IF NOT EXISTS index_sms_msg_sender_body_date_msg_type " +
                        "ON sms_msg(sender, body, date, msg_type)",
                    migration = "20_21",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_sms_msg_pkg_type_channel_date " +
                        "ON sms_msg(package_name, msg_type, notify_channel_id, date)",
                    migration = "20_21",
                )

                execSqlSafely(
                    db = db,
                    sql = "CREATE TABLE IF NOT EXISTS sms_code_rule_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "company TEXT, " +
                        "code_keyword TEXT NOT NULL, " +
                        "code_regex TEXT NOT NULL" +
                        ")",
                    migration = "20_21",
                )
                execSqlSafely(
                    db = db,
                    sql = "INSERT INTO sms_code_rule_new " +
                        "(id, company, code_keyword, code_regex) " +
                        "SELECT " +
                        "CASE WHEN id IS NULL THEN NULL ELSE id END, " +
                        "company, " +
                        "COALESCE(code_keyword, ''), " +
                        "COALESCE(code_regex, '') " +
                        "FROM sms_code_rule",
                    migration = "20_21",
                )
                execSqlSafely(
                    db = db,
                    sql = "DROP TABLE IF EXISTS sms_code_rule",
                    migration = "20_21",
                )
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_code_rule_new RENAME TO sms_code_rule",
                    migration = "20_21",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE UNIQUE INDEX IF NOT EXISTS index_sms_code_rule_company_code_keyword_code_regex " +
                        "ON sms_code_rule(company, code_keyword, code_regex)",
                    migration = "20_21",
                )
            }
        }

        private val MIGRATION_21_22 = object : androidx.room.migration.Migration(21, 22) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "CREATE TABLE IF NOT EXISTS auto_input_event_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "record_id INTEGER, " +
                        "package_name TEXT, " +
                        "code_length INTEGER NOT NULL DEFAULT 0, " +
                        "attempt_at INTEGER NOT NULL DEFAULT 0, " +
                        "success INTEGER, " +
                        "fail_reason TEXT" +
                        ")",
                    migration = "21_22",
                )
                execSqlSafely(
                    db = db,
                    sql = "INSERT INTO auto_input_event_new " +
                        "(id, record_id, package_name, code_length, attempt_at, success, fail_reason) " +
                        "SELECT " +
                        "CASE WHEN id IS NULL THEN NULL ELSE id END, " +
                        "record_id, package_name, " +
                        "COALESCE(code_length, 0), " +
                        "COALESCE(attempt_at, 0), " +
                        "success, fail_reason " +
                        "FROM auto_input_event",
                    migration = "21_22",
                )
                execSqlSafely(
                    db = db,
                    sql = "DROP TABLE IF EXISTS auto_input_event",
                    migration = "21_22",
                )
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE auto_input_event_new RENAME TO auto_input_event",
                    migration = "21_22",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_auto_input_attempt_at " +
                        "ON auto_input_event(attempt_at)",
                    migration = "21_22",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_auto_input_record " +
                        "ON auto_input_event(record_id)",
                    migration = "21_22",
                )

                execSqlSafely(
                    db = db,
                    sql = "CREATE TABLE IF NOT EXISTS sender_dispatch_log_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "record_id INTEGER, " +
                        "sender_id INTEGER NOT NULL DEFAULT 0, " +
                        "sender_type INTEGER NOT NULL DEFAULT 0, " +
                        "msg_type INTEGER NOT NULL DEFAULT 0, " +
                        "success INTEGER NOT NULL DEFAULT 0, " +
                        "created_at INTEGER NOT NULL DEFAULT 0" +
                        ")",
                    migration = "21_22",
                )
                execSqlSafely(
                    db = db,
                    sql = "INSERT INTO sender_dispatch_log_new " +
                        "(id, record_id, sender_id, sender_type, msg_type, success, created_at) " +
                        "SELECT " +
                        "CASE WHEN id IS NULL THEN NULL ELSE id END, " +
                        "record_id, " +
                        "COALESCE(sender_id, 0), " +
                        "COALESCE(sender_type, 0), " +
                        "COALESCE(msg_type, 0), " +
                        "COALESCE(success, 0), " +
                        "COALESCE(created_at, 0) " +
                        "FROM sender_dispatch_log",
                    migration = "21_22",
                )
                execSqlSafely(
                    db = db,
                    sql = "DROP TABLE IF EXISTS sender_dispatch_log",
                    migration = "21_22",
                )
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sender_dispatch_log_new RENAME TO sender_dispatch_log",
                    migration = "21_22",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_sender_dispatch_type_time " +
                        "ON sender_dispatch_log(sender_type, created_at)",
                    migration = "21_22",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_sender_dispatch_sender_time " +
                        "ON sender_dispatch_log(sender_id, created_at)",
                    migration = "21_22",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_sender_dispatch_record " +
                        "ON sender_dispatch_log(record_id)",
                    migration = "21_22",
                )
            }
        }

        private val MIGRATION_22_23 = object : androidx.room.migration.Migration(22, 23) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE app_info ADD COLUMN forwarding_configured INTEGER NOT NULL DEFAULT 0",
                    migration = "22_23",
                )
                execSqlSafely(
                    db = db,
                    sql = "UPDATE app_info SET forwarding_configured = CASE WHEN forwarding = 1 THEN 1 ELSE 0 END",
                    migration = "22_23",
                )
            }
        }

        private val MIGRATION_23_24 = object : androidx.room.migration.Migration(23, 24) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN sim_slot INTEGER NOT NULL DEFAULT -1",
                    migration = "23_24",
                )
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN sub_id INTEGER NOT NULL DEFAULT 0",
                    migration = "23_24",
                )
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN contact_name TEXT NOT NULL DEFAULT ''",
                    migration = "23_24",
                )
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN phone_area TEXT NOT NULL DEFAULT ''",
                    migration = "23_24",
                )
            }
        }

        private val MIGRATION_24_25 = object : androidx.room.migration.Migration(24, 25) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN session_key TEXT NOT NULL DEFAULT ''",
                    migration = "24_25",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_sms_msg_type_session_key " +
                        "ON sms_msg(msg_type, session_key)",
                    migration = "24_25",
                )
            }
        }

        private val MIGRATION_25_26 = object : androidx.room.migration.Migration(25, 26) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN processed_time INTEGER NOT NULL DEFAULT 0",
                    migration = "25_26",
                )
            }
        }

        private fun execSqlSafely(
            db: androidx.sqlite.db.SupportSQLiteDatabase,
            sql: String,
            migration: String,
        ) {
            try {
                db.execSQL(sql)
            } catch (e: SQLiteException) {
                XLog.w("Room migration %s skipped SQL: %s (%s)", migration, sql, e.message ?: "unknown")
            }
        }

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            val dbContext = context.applicationContext ?: context
            migratePreviousDatabaseFiles(dbContext)
            instance ?: Room.databaseBuilder(
                dbContext,
                AppDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                    MIGRATION_20_21,
                    MIGRATION_21_22,
                    MIGRATION_22_23,
                    MIGRATION_23_24,
                    MIGRATION_24_25,
                    MIGRATION_25_26,
                )
                .enableMultiInstanceInvalidation()
                .build().also { instance = it }
        }

        private fun migratePreviousDatabaseFiles(context: Context) {
            val targetMainDb = context.getDatabasePath(DATABASE_NAME)
            if (targetMainDb.exists()) return

            PREVIOUS_DATABASE_NAMES.forEach { previousDbName ->
                listOf("", "-wal", "-shm").forEach { suffix ->
                    val previousName = "$previousDbName$suffix"
                    val targetName = "$DATABASE_NAME$suffix"
                    migrateSingleDatabaseFile(context, previousName, targetName)
                }
            }
        }

        private fun migrateSingleDatabaseFile(context: Context, previousName: String, targetName: String) {
            val source = context.getDatabasePath(previousName)
            if (!source.exists() || !source.isFile) return

            val target = context.getDatabasePath(targetName)
            if (target.exists()) return
            target.parentFile?.mkdirs()

            val moved = runCatching { source.renameTo(target) }.getOrDefault(false)
            if (!moved) {
                runCatching {
                    source.copyTo(target, overwrite = false)
                    source.delete()
                }.onFailure {
                    XLog.w(
                        "Failed to migrate previous db file %s -> %s (%s)",
                        source.absolutePath,
                        target.absolutePath,
                        it.message ?: "unknown",
                    )
                }
            } else {
                XLog.i("Migrated previous db file: %s -> %s", previousName, targetName)
            }
        }

        @JvmStatic
        fun closeInstance() {
            synchronized(this) {
                runCatching { instance?.close() }
                instance = null
            }
        }
    }
}
