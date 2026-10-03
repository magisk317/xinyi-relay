package io.github.magisk317.relay.desktop.local

import io.github.magisk317.relay.desktop.data.DatabaseSnapshot
import io.github.magisk317.relay.desktop.data.DatabaseTransfer
import io.github.magisk317.relay.desktop.data.DesktopDatabase
import io.github.magisk317.relay.desktop.platform.DesktopFileDialog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Result of one export or import round through the file dialog seam.
 *
 * Every branch is non-exceptional: the UI turns them into hints, banners or a
 * dialog close. [Failed] carries the transfer core's reason verbatim so the
 * operator sees what was wrong with the file (foreign format, newer version,
 * schema mismatch) instead of a generic failure.
 */
sealed interface TransferOutcome {

    /** Export committed at [file]; [snapshot] is what was written. */
    data class Exported(val file: File, val snapshot: DatabaseSnapshot) : TransferOutcome

    /** Import committed; [snapshot] is what was restored. */
    data class Imported(val snapshot: DatabaseSnapshot) : TransferOutcome

    /** The user dismissed the file dialog; nothing happened. */
    data object Cancelled : TransferOutcome

    /** The round failed; [message] explains why. */
    data class Failed(val message: String) : TransferOutcome
}

/**
 * Drives the database transfer UI seam (parity §5): file dialog in, snapshot
 * text to `:desktop:data`, outcome out.
 *
 * The database arrives through [databaseProvider] because the mirror's
 * connection belongs to [DesktopLocalSyncController] — re-opening the store
 * here would fork the SQLite file and fight the sync engine. When the mirror
 * is closed (Remote run mode, pre-login) the provider yields null and the
 * page shows its unavailable note; [available] mirrors that for the UI.
 *
 * All blocking work (dialog, file IO) runs on [ioDispatcher]; callers must be
 * in a coroutine scope. The export writes a temporary file and renames it, so
 * an interrupted write can never leave a truncated snapshot behind for the
 * import path to pick up.
 */
class DatabaseTransferController(
    private val databaseProvider: () -> DesktopDatabase?,
    private val fileDialog: DesktopFileDialog,
    private val clock: () -> Instant = Instant::now,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** True while the local mirror is open, i.e. a transfer can run. */
    val available: Boolean get() = databaseProvider() != null

    /**
     * Snapshots the whole local database into a user-chosen file. Returns
     * [TransferOutcome.Cancelled] without touching disk when the user backs
     * out of the dialog.
     */
    suspend fun export(): TransferOutcome {
        val database = databaseProvider() ?: return failed("the local mirror is closed")
        return withContext(ioDispatcher) {
            val target = fileDialog.chooseSaveFile(defaultFileName()) ?: return@withContext TransferOutcome.Cancelled
            try {
                // Instant.toString renders ISO-8601 ("...T12:34:56Z"), the shape
                // the snapshot stamps carry everywhere.
                val snapshot = DatabaseTransfer.export(database, clock().toString())
                writeAtomically(target, DatabaseTransfer.encode(snapshot))
                TransferOutcome.Exported(target, snapshot)
            } catch (failure: Exception) {
                failed(failure.message ?: "cannot write the snapshot")
            }
        }
    }

    /**
     * Restores a snapshot from a user-chosen file, replacing the current
     * content atomically. A refused file (foreign format, newer version,
     * missing table, schema mismatch) leaves the previous content untouched
     * and comes back as [TransferOutcome.Failed].
     */
    suspend fun import(): TransferOutcome {
        val database = databaseProvider() ?: return failed("the local mirror is closed")
        return withContext(ioDispatcher) {
            val source = fileDialog.chooseOpenFile() ?: return@withContext TransferOutcome.Cancelled
            val text = try {
                source.readText()
            } catch (failure: Exception) {
                return@withContext failed("cannot read ${source.name}: ${failure.message}")
            }
            try {
                TransferOutcome.Imported(DatabaseTransfer.importText(database, text))
            } catch (failure: Exception) {
                failed(failure.message ?: "cannot apply the snapshot")
            }
        }
    }

    /**
     * Writes [text] beside [target] as a temporary file and renames it into
     * place, so a half-written file never appears under the chosen name.
     * Falls back to copy-and-delete where rename across devices is refused.
     */
    private fun writeAtomically(target: File, text: String) {
        val parent = target.parentFile ?: File(".")
        parent.mkdirs()
        val temporary = File.createTempFile("xinyi-snapshot-", ".json.tmp", parent)
        try {
            temporary.writeText(text)
            if (target.exists() && !target.delete()) {
                throw IllegalStateException("cannot replace ${target.name}")
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    /** `xinyi-relay-desktop-<UTC yyyyMMdd-HHmmss>.json`. */
    private fun defaultFileName(): String {
        val stamp = FILE_STAMP.format(clock().atOffset(ZoneOffset.UTC))
        return "xinyi-relay-desktop-$stamp.json"
    }

    private fun failed(message: String): TransferOutcome = TransferOutcome.Failed(message)

    private companion object {
        val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
