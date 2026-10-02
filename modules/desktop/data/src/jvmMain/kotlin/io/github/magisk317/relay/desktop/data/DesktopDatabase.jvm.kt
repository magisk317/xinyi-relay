package io.github.magisk317.relay.desktop.data

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.deferredTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import java.io.File

internal actual fun openAt(path: String): DesktopDatabase =
    Room.databaseBuilder<DesktopDatabase>(path) { DesktopDatabaseConstructor.initialize() }
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addCallback(PartialIndexCallback)
        .build()

internal actual fun inMemoryAt(name: String): DesktopDatabase =
    Room.inMemoryDatabaseBuilder<DesktopDatabase> { DesktopDatabaseConstructor.initialize() }
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addCallback(PartialIndexCallback)
        .build()

internal actual fun defaultDatabaseDirectory(): String =
    File(System.getProperty("user.home"), ".local/share/xinyi-relay").absolutePath

internal actual fun absolutePath(directory: String, name: String): String =
    File(directory, name).absolutePath

internal actual fun execSql(connection: SQLiteConnection, sql: String) {
    connection.execSQL(sql)
}

/**
 * Runs [block] inside an atomic SQLite transaction.
 *
 * `withTransaction` (room-ktx) is Android-only and not published for the JVM target, so we use
 * Room KMP's native [useWriterConnection] + [deferredTransaction] (BEGIN DEFERRED), which is the
 * JVM/Android equivalent of Room's `withTransaction`.
 */
internal actual suspend fun <R> DesktopDatabase.inTransaction(block: suspend () -> R): R =
    useWriterConnection<R> { transactor -> transactor.deferredTransaction<R> { block() } }

/**
 * Installs the partial indexes Room cannot express with annotations.
 *
 * Executed from the open callback rather than `onCreate` so databases created by
 * an earlier build also pick the indexes up.
 */
private object PartialIndexCallback : RoomDatabase.Callback() {

    override fun onCreate(connection: SQLiteConnection) {
        PartialIndexes.install(connection)
    }

    override fun onOpen(connection: SQLiteConnection) {
        PartialIndexes.install(connection)
    }
}
