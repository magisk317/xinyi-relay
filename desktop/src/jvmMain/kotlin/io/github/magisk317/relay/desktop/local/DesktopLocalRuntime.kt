package io.github.magisk317.relay.desktop.local

import io.github.magisk317.relay.desktop.core.store.Clock
import io.github.magisk317.relay.desktop.core.store.DesktopClock
import io.github.magisk317.relay.desktop.core.store.DesktopLocalStore
import io.github.magisk317.relay.desktop.core.store.RemoteStore
import io.github.magisk317.relay.desktop.core.sync.SyncEngine
import io.github.magisk317.relay.desktop.core.sync.asRemoteView
import io.github.magisk317.relay.desktop.data.DesktopDatabase
import io.github.magisk317.relay.desktop.data.DesktopDatabaseFactory

/**
 * Composition root of the local half of the console.
 *
 * The console pages talk to the backend through `remote.ConsoleClient`; this
 * runtime owns the SQLite mirror the Local and Hybrid run modes read from and
 * the engine that keeps it in step with the backend. It is the one place in the
 * app that knows how the pieces of `:desktop:core` and `:desktop:data` fit
 * together, so no page ever opens a database itself.
 */
class DesktopLocalRuntime(
    val store: DesktopLocalStore,
    val sync: SyncEngine,
    private val database: DesktopDatabase,
) : AutoCloseable {

    override fun close() {
        database.close()
    }

    companion object {

        /**
         * Opens the persistent store under [directory], which defaults to the
         * platform's user data directory.
         */
        fun create(
            remote: RemoteStore,
            clock: Clock = DesktopClock(),
            directory: String = DesktopDatabaseFactory.defaultDirectory(),
        ): DesktopLocalRuntime = assemble(DesktopDatabaseFactory.open(directory), remote, clock)

        /**
         * Opens a private in-memory store, so dry-run imports and tests never
         * touch the on-disk database.
         */
        fun createInMemory(
            remote: RemoteStore,
            clock: Clock = DesktopClock(),
            name: String = "desktop-dry-run",
        ): DesktopLocalRuntime = assemble(DesktopDatabaseFactory.inMemory(name), remote, clock)

        private fun assemble(
            database: DesktopDatabase,
            remote: RemoteStore,
            clock: Clock,
        ): DesktopLocalRuntime {
            val store = DesktopLocalStore(database, clock)
            return DesktopLocalRuntime(
                store = store,
                sync = SyncEngine(local = store, remote = remote.asRemoteView()),
                database = database,
            )
        }
    }
}
