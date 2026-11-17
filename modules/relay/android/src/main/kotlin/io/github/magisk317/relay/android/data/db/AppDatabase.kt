package io.github.magisk317.relay.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.magisk317.relay.android.data.db.dao.RuleDao
import io.github.magisk317.relay.android.data.db.dao.SenderDao
import io.github.magisk317.relay.android.data.db.legacy.entity.LegacyConvertersDate as ConvertersDate
import io.github.magisk317.relay.android.data.db.legacy.entity.LegacyConvertersSenderList as ConvertersSenderList
import io.github.magisk317.relay.android.data.db.dao.AppInfoDao
import io.github.magisk317.relay.android.data.db.dao.AutoInputEventDao
import io.github.magisk317.relay.android.data.db.dao.ForwardFilterRuleDao
import io.github.magisk317.relay.android.data.db.dao.NotifyRouteRuleDao
import io.github.magisk317.relay.android.data.db.dao.SmsBlacklistHitDao
import io.github.magisk317.relay.android.data.db.dao.SmsCodeRuleDao
import io.github.magisk317.relay.android.data.db.dao.SmsMsgDao
import io.github.magisk317.smscode.db.entity.AutoInputEvent
import io.github.magisk317.smscode.db.entity.SmsCodeRule
import io.github.magisk317.relay.android.data.db.entity.SenderDispatchLog
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.relay.android.data.db.entity.SmsBlacklistHit

// v34 dropped the legacy import-source tables (sms_msg, app_info, Sender,
// Rule, forward_filter_rule, notify_route_rule, sender_dispatch_log,
// scheduled_task): ForwardDatabaseBootstrapper drains them into forward.db
// through raw SQLite and drops them after a fully successful import.
@Database(entities = [
    SmsCodeRule::class,
    SmsBlacklistHit::class,
    AutoInputEvent::class
], version = 34, exportSchema = true)
@TypeConverters(ConvertersDate::class, ConvertersSenderList::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun smsCodeRuleDao(): SmsCodeRuleDao
    abstract fun smsBlacklistHitDao(): SmsBlacklistHitDao
    abstract fun autoInputEventDao(): AutoInputEventDao

    companion object {
        internal const val DATABASE_NAME = "relay_room.db"
        private val PREVIOUS_DATABASE_NAMES = listOf("xrelay_room.db", "xsmscode_room.db")

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            val dbContext = context.applicationContext ?: context
            migratePreviousDatabaseFiles(dbContext)
            recoverCorruptedDatabase(dbContext)
            instance ?: Room.databaseBuilder(
                dbContext,
                AppDatabase::class.java,
                DATABASE_NAME,
            )
                .fallbackToDestructiveMigration(dropAllTables = true)
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
                    MIGRATION_26_27,
                    MIGRATION_27_28,
                    MIGRATION_28_29,
                    MIGRATION_29_30,
                    MIGRATION_30_31,
                    MIGRATION_31_32,
                    MIGRATION_32_33,
                    MIGRATION_33_34,
                )
                .enableMultiInstanceInvalidation()
                .build().also { instance = it }
        }

        /**
         * Check if the database file is corrupted (e.g., from a crash during write).
         * If integrity check fails, delete the database so Room can recreate it fresh.
         */
        private fun recoverCorruptedDatabase(context: Context) {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (!dbFile.exists()) return
            runCatching {
                val conn = android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbFile.absolutePath, null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )
                val cursor = conn.rawQuery("PRAGMA integrity_check", null)
                val ok = cursor.moveToFirst() && cursor.getString(0) == "ok"
                cursor.close()
                conn.close()
                if (!ok) {
                    XLog.e("Database integrity check failed, deleting corrupted database")
                    context.deleteDatabase(DATABASE_NAME)
                }
            }.onFailure { e ->
                XLog.e(message = "Database integrity check threw, deleting corrupted database", e)
                context.deleteDatabase(DATABASE_NAME)
            }
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
                        message = "Failed to migrate previous db file %s -> %s (%s)",
                        source.absolutePath,
                        target.absolutePath,
                        it.message ?: "unknown",
                    )
                }
            } else {
                XLog.i(message = "Migrated previous db file: %s -> %s", previousName, targetName)
            }
        }

        @JvmStatic
        fun closeInstance() {
            synchronized(this) {
                runCatching { instance?.close() }.onFailure { error ->
                    XLog.w("closeInstance: close failed: ${error.message}", error)
                }
                instance = null
            }
        }
    }
}
