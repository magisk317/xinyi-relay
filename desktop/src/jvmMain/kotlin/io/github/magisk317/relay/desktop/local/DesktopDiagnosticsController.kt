package io.github.magisk317.relay.desktop.local

import io.github.magisk317.relay.desktop.platform.DesktopFileDialog
import io.github.magisk317.relay.desktop.platform.writeAtomically
import io.github.magisk317.relay.desktop.session.DesktopDiagnostics
import io.github.magisk317.relay.desktop.session.diagnosticsFileName
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * Outcome of one diagnostics export round through the file dialog seam.
 *
 * Three states are all the export needs: the file landed, nothing happened,
 * or it failed. [Failed] carries the reason verbatim (write permission, disk
 * full) the same way the database transfer outcomes do.
 */
sealed interface DiagnosticsOutcome {

    /** Export committed at [file]; [report] is what was written. */
    data class Exported(val file: File, val report: DesktopDiagnostics) : DiagnosticsOutcome

    /** The user dismissed the file dialog; nothing happened. */
    data object Cancelled : DiagnosticsOutcome

    /** The round failed; [message] explains why. */
    data class Failed(val message: String) : DiagnosticsOutcome
}

/**
 * Drives the diagnostics export UI seam (parity §5, `诊断信息导出`).
 *
 * The report is built by the injected [report] lambda off the shell's live
 * state, so the controller itself stays platform-agnostic and testable; the
 * dialog does the OS work, [writeAtomically] lands the file. Exports are
 * always allowed — unlike the database snapshot, a diagnostics bundle needs
 * no open mirror, which keeps it usable exactly when the Local/Hybrid track
 * is the thing that broke.
 */
class DesktopDiagnosticsController(
    private val report: () -> DesktopDiagnostics,
    private val fileDialog: DesktopFileDialog,
    private val clock: () -> Instant = Instant::now,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * Writes a diagnostics snapshot into a user-chosen file. Returns
     * [DiagnosticsOutcome.Cancelled] without touching disk when the user
     * backs out of the dialog.
     */
    suspend fun export(): DiagnosticsOutcome {
        val target = withContext(ioDispatcher) {
            fileDialog.chooseSaveFile(diagnosticsFileName(clock))
        } ?: return DiagnosticsOutcome.Cancelled
        return withContext(ioDispatcher) {
            try {
                val diagnostics = report()
                writeAtomically(target, diagnostics.encode())
                DiagnosticsOutcome.Exported(target, diagnostics)
            } catch (failure: Exception) {
                DiagnosticsOutcome.Failed(failure.message ?: "cannot write the diagnostics file")
            }
        }
    }
}
