package com.github.magisk317.smscode.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetDatabase()
    }

    @After
    fun tearDown() {
        resetDatabase()
    }

    @Test
    fun migrate_3_to_18_success() {
        createVersion3Database()

        val db = openMigratedDatabase()
        assertFinalSchema(db)
    }

    @Test
    fun migrate_1_to_18_success() {
        createVersion1Database()

        val db = openMigratedDatabase()
        assertFinalSchema(db)
    }

    @Test
    fun migrate_6_to_18_success() {
        createVersion6Database()

        val db = openMigratedDatabase()
        assertFinalSchema(db)
    }

    @Test
    fun migrate_15_to_18_success() {
        createVersion15Database()

        val db = openMigratedDatabase()
        assertFinalSchema(db)
    }

    @Test
    fun migrate_17_to_18_success() {
        createVersion17Database()

        val db = openMigratedDatabase()
        assertFinalSchema(db)
    }

    @Test
    fun database_instance_builds_without_migration_conflict() {
        val db = openMigratedDatabase()
        assertEquals(18, queryUserVersion(db))
    }

    private fun openMigratedDatabase(): SupportSQLiteDatabase {
        val db = AppDatabase.getInstance(context)
        return db.openHelper.writableDatabase
    }

    private fun assertFinalSchema(db: SupportSQLiteDatabase) {
        assertEquals(18, queryUserVersion(db))

        assertColumnExists(db, table = "sms_msg", column = "msg_type")
        assertColumnExists(db, table = "sms_msg", column = "call_type")
        assertColumnExists(db, table = "sms_msg", column = "notify_channel_id")

        assertColumnExists(db, table = "Sender", column = "receive_non_code")
        assertColumnExists(db, table = "Sender", column = "receive_app_notify")
        assertColumnExists(db, table = "Sender", column = "receive_code")
        assertColumnExists(db, table = "Sender", column = "receive_call_notify")

        assertColumnExists(db, table = "app_info", column = "forwarding")
        assertColumnExists(db, table = "app_info", column = "notify_template")
        assertColumnExists(db, table = "notify_route_rule", column = "scope")
        assertColumnExists(db, table = "notify_route_rule", column = "package_name")
        assertColumnExists(db, table = "notify_route_rule", column = "sender_id")
        assertColumnExists(db, table = "notify_route_rule", column = "update_time")
        assertColumnExists(db, table = "forward_filter_rule", column = "msg_type")
        assertColumnExists(db, table = "forward_filter_rule", column = "scope_type")
        assertColumnExists(db, table = "forward_filter_rule", column = "scope_key")
        assertColumnExists(db, table = "forward_filter_rule", column = "sender_id")
        assertColumnExists(db, table = "forward_filter_rule", column = "policy")
        assertColumnExists(db, table = "forward_filter_rule", column = "match_mode")
        assertColumnExists(db, table = "forward_filter_rule", column = "pattern")
        assertColumnExists(db, table = "forward_filter_rule", column = "enabled")
        assertColumnExists(db, table = "forward_filter_rule", column = "update_time")

        assertIndexExists(db, table = "sms_msg", index = "index_sms_msg_sender_body_date_msg_type")
        assertIndexExists(db, table = "sms_msg", index = "index_sms_msg_pkg_type_channel_date")
        assertIndexExists(db, table = "notify_route_rule", index = "index_notify_route_rule_scope_package_sender")
        assertIndexExists(db, table = "notify_route_rule", index = "index_notify_route_rule_package_scope")
        assertIndexExists(db, table = "notify_route_rule", index = "index_notify_route_rule_sender_scope")
        assertIndexExists(db, table = "forward_filter_rule", index = "index_forward_filter_rule_unique")
        assertIndexExists(db, table = "forward_filter_rule", index = "index_forward_filter_rule_msg_scope_key")
        assertIndexExists(db, table = "forward_filter_rule", index = "index_forward_filter_rule_msg_scope_sender")
    }

    private fun queryUserVersion(db: SupportSQLiteDatabase): Int {
        db.query("PRAGMA user_version").use { cursor ->
            assertTrue("PRAGMA user_version should return one row", cursor.moveToFirst())
            return cursor.getInt(0)
        }
    }

    private fun assertColumnExists(
        db: SupportSQLiteDatabase,
        table: String,
        column: String,
    ) {
        val columns = mutableSetOf<String>()
        db.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
        }
        assertTrue("Column $table.$column should exist, actual=$columns", column in columns)
    }

    private fun assertIndexExists(
        db: SupportSQLiteDatabase,
        table: String,
        index: String,
    ) {
        val indexes = mutableSetOf<String>()
        db.query("PRAGMA index_list($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                indexes += cursor.getString(nameIndex)
            }
        }
        assertTrue("Index $index should exist on $table, actual=$indexes", index in indexes)
    }

    private fun createVersion1Database() {
        createLegacyDatabase(
            version = 1,
            createSenderAndRule = false,
            includePackageName = false,
            includeForwardColumns = false,
            includeReceiveNonCode = false,
        )
    }

    private fun createVersion3Database() {
        createLegacyDatabase(
            version = 3,
            createSenderAndRule = false,
            includePackageName = true,
            includeForwardColumns = false,
            includeReceiveNonCode = false,
        )
    }

    private fun createVersion6Database() {
        createLegacyDatabase(
            version = 6,
            createSenderAndRule = true,
            includePackageName = true,
            includeForwardColumns = true,
            includeReceiveNonCode = true,
        )
    }

    private fun createLegacyDatabase(
        version: Int,
        createSenderAndRule: Boolean,
        includePackageName: Boolean,
        includeForwardColumns: Boolean,
        includeReceiveNonCode: Boolean,
    ) {
        val dbPath = context.getDatabasePath(DATABASE_NAME)
        dbPath.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(dbPath, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS sms_code_rule (" +
                    "company TEXT, " +
                    "code_keyword TEXT NOT NULL, " +
                    "code_regex TEXT NOT NULL, " +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT" +
                    ")",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_sms_code_rule_company_code_keyword_code_regex " +
                    "ON sms_code_rule(company, code_keyword, code_regex)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS app_info (" +
                    "package_name TEXT NOT NULL, " +
                    "label TEXT, " +
                    "blocked INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(package_name)" +
                    ")",
            )

            val smsMsgColumns = mutableListOf(
                "id INTEGER PRIMARY KEY AUTOINCREMENT",
                "sender TEXT",
                "body TEXT",
                "date INTEGER NOT NULL DEFAULT 0",
                "company TEXT",
                "sms_code TEXT",
            ).apply {
                if (includePackageName) add("package_name TEXT")
                if (includeForwardColumns) {
                    add("forward_status INTEGER NOT NULL DEFAULT 0")
                    add("forward_target TEXT")
                    add("forward_message TEXT")
                    add("forward_time INTEGER NOT NULL DEFAULT 0")
                }
            }
            db.execSQL("CREATE TABLE IF NOT EXISTS sms_msg (${smsMsgColumns.joinToString(", ")})")

            if (version >= 3) {
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_sms_msg_sender_body_date " +
                        "ON sms_msg(sender, body, date)",
                )
            }

            if (createSenderAndRule) {
                val senderColumns = mutableListOf(
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL",
                    "type INTEGER NOT NULL DEFAULT 1",
                    "name TEXT NOT NULL DEFAULT ''",
                    "json_setting TEXT NOT NULL DEFAULT ''",
                    "status INTEGER NOT NULL DEFAULT 1",
                    "time INTEGER NOT NULL",
                ).apply {
                    if (includeReceiveNonCode) {
                        add("receive_non_code INTEGER NOT NULL DEFAULT 0")
                    }
                }
                db.execSQL("CREATE TABLE IF NOT EXISTS Sender (${senderColumns.joinToString(", ")})")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS Rule (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "type TEXT NOT NULL DEFAULT 'sms', " +
                        "filed TEXT NOT NULL DEFAULT 'transpond_all', " +
                        "check TEXT NOT NULL DEFAULT 'is', " +
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
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_Rule_id ON Rule(id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_Rule_sender_id ON Rule(sender_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_Rule_sender_list ON Rule(sender_list)")
            }

            db.version = version
        }
    }

    private fun createVersion15Database() {
        val dbPath = context.getDatabasePath(DATABASE_NAME)
        dbPath.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(dbPath, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS sms_code_rule (" +
                    "company TEXT, " +
                    "code_keyword TEXT NOT NULL, " +
                    "code_regex TEXT NOT NULL, " +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT" +
                    ")",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_sms_code_rule_company_code_keyword_code_regex " +
                    "ON sms_code_rule(company, code_keyword, code_regex)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS app_info (" +
                    "package_name TEXT NOT NULL, " +
                    "label TEXT, " +
                    "blocked INTEGER NOT NULL DEFAULT 0, " +
                    "forwarding INTEGER NOT NULL DEFAULT 0, " +
                    "notify_template TEXT NOT NULL DEFAULT '', " +
                    "PRIMARY KEY(package_name)" +
                    ")",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS sms_msg (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "sender TEXT, " +
                    "body TEXT, " +
                    "date INTEGER NOT NULL DEFAULT 0, " +
                    "company TEXT, " +
                    "sms_code TEXT, " +
                    "package_name TEXT, " +
                    "msg_type INTEGER NOT NULL DEFAULT 0, " +
                    "call_type INTEGER NOT NULL DEFAULT 0, " +
                    "forward_status INTEGER NOT NULL DEFAULT 0, " +
                    "forward_target TEXT, " +
                    "forward_message TEXT, " +
                    "forward_time INTEGER NOT NULL DEFAULT 0" +
                    ")",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_sms_msg_sender_body_date_msg_type " +
                    "ON sms_msg(sender, body, date, msg_type)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS Sender (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "type INTEGER NOT NULL DEFAULT 1, " +
                    "name TEXT NOT NULL DEFAULT '', " +
                    "json_setting TEXT NOT NULL DEFAULT '', " +
                    "status INTEGER NOT NULL DEFAULT 1, " +
                    "time INTEGER NOT NULL, " +
                    "receive_non_code INTEGER NOT NULL DEFAULT 0, " +
                    "receive_app_notify INTEGER NOT NULL DEFAULT 1, " +
                    "receive_code INTEGER NOT NULL DEFAULT 1, " +
                    "receive_call_notify INTEGER NOT NULL DEFAULT 0" +
                    ")",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS Rule (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "type TEXT NOT NULL DEFAULT 'sms', " +
                    "filed TEXT NOT NULL DEFAULT 'transpond_all', " +
                    "check TEXT NOT NULL DEFAULT 'is', " +
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
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_Rule_id ON Rule(id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_Rule_sender_id ON Rule(sender_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_Rule_sender_list ON Rule(sender_list)")

            db.version = 15
        }
    }

    private fun createVersion17Database() {
        createVersion15Database()
        val dbPath = context.getDatabasePath(DATABASE_NAME)

        SQLiteDatabase.openDatabase(
            dbPath.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS notify_route_rule (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "scope INTEGER NOT NULL, " +
                    "package_name TEXT NOT NULL, " +
                    "sender_id INTEGER NOT NULL, " +
                    "update_time INTEGER NOT NULL DEFAULT 0" +
                    ")",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_notify_route_rule_scope_package_sender " +
                    "ON notify_route_rule(scope, package_name, sender_id)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_notify_route_rule_package_scope " +
                    "ON notify_route_rule(package_name, scope)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_notify_route_rule_sender_scope " +
                    "ON notify_route_rule(sender_id, scope)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS forward_filter_rule (" +
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
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_forward_filter_rule_unique " +
                    "ON forward_filter_rule(msg_type, scope_type, scope_key, sender_id, policy, match_mode, pattern)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_forward_filter_rule_msg_scope_key " +
                    "ON forward_filter_rule(msg_type, scope_type, scope_key)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_forward_filter_rule_msg_scope_sender " +
                    "ON forward_filter_rule(msg_type, scope_type, sender_id)",
            )
            db.version = 17
        }
    }

    private fun resetDatabase() {
        AppDatabase.closeInstance()
        context.deleteDatabase(DATABASE_NAME)
        context.getDatabasePath("$DATABASE_NAME-wal").delete()
        context.getDatabasePath("$DATABASE_NAME-shm").delete()
    }

    companion object {
        private const val DATABASE_NAME = "xsmscode_room.db"
    }
}
