package io.github.magisk317.relay.android.data.db

import io.github.magisk317.xposed.logging.XLog

// Room schema migrations for AppDatabase (v21-v34). Helpers live in
// DatabaseMigrations.kt, same package.

internal val MIGRATION_21_22 = object : androidx.room.migration.Migration(21, 22) {
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

internal val MIGRATION_22_23 = object : androidx.room.migration.Migration(22, 23) {
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

internal val MIGRATION_23_24 = object : androidx.room.migration.Migration(23, 24) {
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

internal val MIGRATION_24_25 = object : androidx.room.migration.Migration(24, 25) {
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

internal val MIGRATION_25_26 = object : androidx.room.migration.Migration(25, 26) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        execSqlSafely(
            db = db,
            sql = "ALTER TABLE sms_msg ADD COLUMN processed_time INTEGER NOT NULL DEFAULT 0",
            migration = "25_26",
        )
    }
}

internal val MIGRATION_26_27 = object : androidx.room.migration.Migration(26, 27) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        execSqlSafely(
            db = db,
            sql = "ALTER TABLE Sender ADD COLUMN active_schedule_json TEXT NOT NULL DEFAULT ''",
            migration = "26_27",
        )
    }
}

internal val MIGRATION_27_28 = object : androidx.room.migration.Migration(27, 28) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        execSqlSafely(
            db = db,
            sql = "ALTER TABLE Sender ADD COLUMN priority INTEGER NOT NULL DEFAULT 0",
            migration = "27_28",
        )
    }
}

internal val MIGRATION_28_29 = object : androidx.room.migration.Migration(28, 29) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        execSqlSafely(
            db = db,
            sql = "CREATE TABLE IF NOT EXISTS scheduled_task (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL DEFAULT '', " +
                "task_type TEXT NOT NULL DEFAULT 'sms', " +
                "cron_expression TEXT NOT NULL DEFAULT '', " +
                "sim_slot INTEGER NOT NULL DEFAULT 0, " +
                "mobiles TEXT NOT NULL DEFAULT '', " +
                "content TEXT NOT NULL DEFAULT '', " +
                "status INTEGER NOT NULL DEFAULT 1, " +
                "last_run_time INTEGER NOT NULL DEFAULT 0, " +
                "next_run_time INTEGER NOT NULL DEFAULT 0, " +
                "created_at INTEGER NOT NULL" +
                ")",
            migration = "28_29",
        )
        execSqlSafely(
            db = db,
            sql = "CREATE UNIQUE INDEX IF NOT EXISTS index_scheduled_task_id " +
                "ON scheduled_task(id)",
            migration = "28_29",
        )
        execSqlSafely(
            db = db,
            sql = "CREATE INDEX IF NOT EXISTS index_scheduled_task_status " +
                "ON scheduled_task(status)",
            migration = "28_29",
        )
    }
}

internal val MIGRATION_29_30 = object : androidx.room.migration.Migration(29, 30) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        execSqlSafely(
            db = db,
            sql = "CREATE INDEX IF NOT EXISTS index_scheduled_task_status " +
                "ON scheduled_task(status)",
            migration = "29_30",
        )
    }
}

internal val MIGRATION_30_31 = object : androidx.room.migration.Migration(30, 31) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        execSqlSafely(
            db = db,
            sql = "ALTER TABLE Sender ADD COLUMN custom_template TEXT NOT NULL DEFAULT ''",
            migration = "30_31",
        )
    }
}

internal val MIGRATION_31_32 = object : androidx.room.migration.Migration(31, 32) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        createSmsBlacklistHitTable(db, "31_32")
    }
}

// v34 removes the legacy entity types; the physical tables are dropped by
// ForwardDatabaseBootstrapper after a successful import (raw SQL). An
// empty migration keeps the version bookkeeping correct; devices whose
// import has not run yet keep the tables as tolerated extras until the
// bootstrapper drains and drops them.
internal val MIGRATION_33_34 = object : androidx.room.migration.Migration(33, 34) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        XLog.w(message = "Relay schema v33->v34: legacy entity types removed")
    }
}

internal val MIGRATION_32_33 = object : androidx.room.migration.Migration(32, 33) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        recreateSmsMsgTable(db, "32_33")
        recreateSmsCodeRuleTable(db, "32_33")
    }
}

/**
 * Recreate [sms_msg] so its autoGenerate primary key becomes nullable
 * (`id INTEGER PRIMARY KEY AUTOINCREMENT`, without `NOT NULL`). This matches the
 * schema produced after the data layer was unified onto :smscode-core:db
 * (commit 9794f7ef changed SmsMsg.id / SmsCodeRule.id from Long to Long?).
 * Rows are preserved. SQLite cannot alter a column NOT NULL constraint in place,
 * so we rebuild the table and copy data.
 */
