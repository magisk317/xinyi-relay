package io.github.magisk317.relay.desktop.data

import androidx.room.RoomDatabase

/**
 * Public entry point for opening the desktop database.
 *
 * Only the builder is platform specific; the schema, the DAOs and all store
 * semantics live in the common source set. The platform-specific wiring (driver,
 * in-memory vs on-disk, callback, transaction) is provided by the top-level
 * `expect` functions below.
 */
object DesktopDatabaseFactory {

    /**
     * Opens (or creates) the on-disk database inside [directory].
     */
    fun open(
        directory: String,
        name: String = DesktopDatabase.DATABASE_NAME,
    ): DesktopDatabase = openAt(absolutePath(directory, name))

    /**
     * Opens a private, in-memory database. Used by tests and by dry-run imports.
     */
    fun inMemory(name: String = ":memory:"): DesktopDatabase = inMemoryAt(name)

    /**
     * Default on-disk location of the desktop store (platform specific).
     */
    fun defaultDirectory(): String = defaultDatabaseDirectory()
}

internal expect fun openAt(path: String): DesktopDatabase

internal expect fun inMemoryAt(name: String): DesktopDatabase

internal expect fun defaultDatabaseDirectory(): String

internal expect fun absolutePath(directory: String, name: String): String

/**
 * Runs [block] inside a single database transaction.
 *
 * `withTransaction` is only resolvable on the JVM target, so the actual is
 * provided per platform.
 */
internal expect suspend fun <R> DesktopDatabase.inTransaction(block: suspend () -> R): R
