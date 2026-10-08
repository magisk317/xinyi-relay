package io.github.magisk317.relay.desktop.platform

import java.awt.FileDialog
import java.io.File
import java.io.FilenameFilter

/**
 * OS file chooser for the database transfer seam (parity §5).
 *
 * The webUI downloads the database snapshot through the browser's download
 * manager and picks an import file through a file input; the desktop shell has
 * no equivalent, so both directions go through here. The seam exists for the
 * headless CI case: constructing a [FileDialog] on a machine without a display
 * throws [java.awt.HeadlessException], which must surface as "no file chosen"
 * rather than crash the click handler.
 */
interface DesktopFileDialog {

    /**
     * Asks the user where to write the export, [suggestedName] pre-filled in
     * the name field.
     *
     * @return the chosen file, or null when the user cancelled (or the
     *   platform has no file dialog).
     */
    fun chooseSaveFile(suggestedName: String): File?

    /**
     * Asks the user which file to import.
     *
     * @return the chosen file, or null when the user cancelled (or the
     *   platform has no file dialog).
     */
    fun chooseOpenFile(): File?
}

/**
 * [DesktopFileDialog] over AWT's modal [FileDialog].
 *
 * The OS call (making the modal visible) sits behind an injectable lambda the
 * same way [AwtLinkOpener] hides `Desktop.browse`, so tests can stub the dialog
 * result without a display. A cancelled dialog reports null directory/file on
 * Linux and Windows; macOS returns the last used values on cancel, so a null
 * check alone is not sufficient — the dialog is re-created per call, which
 * resets that carry-over.
 *
 * The `.json` filter passed to [FileDialog.setFilenameFilter] is honoured on
 * Linux and Windows; macOS ignores it by AWT design. The filter also lets
 * directories through so double-click navigation keeps working.
 */
class AwtFileDialog(
    owner: java.awt.Frame? = null,
    private val showSave: (FileDialog) -> Unit = { dialog -> dialog.isVisible = true },
    private val showOpen: (FileDialog) -> Unit = { dialog -> dialog.isVisible = true },
) : DesktopFileDialog {

    private val owner: java.awt.Frame? = owner

    override fun chooseSaveFile(suggestedName: String): File? = pick(FileDialog.SAVE) {
        it.file = suggestedName
        showSave(it)
    }.let { dialog -> resolve(dialog) }

    override fun chooseOpenFile(): File? = pick(FileDialog.LOAD) { dialog ->
        showOpen(dialog)
    }.let { dialog -> resolve(dialog) }

    /** Builds a fresh dialog per call, tolerating a headless platform. */
    private inline fun pick(mode: Int, configure: (FileDialog) -> Unit): FileDialog? =
        runCatching {
            FileDialog(owner, TITLE, mode).apply {
                filenameFilter = FilenameFilter { dir, name ->
                    File(dir, name).isDirectory || name.endsWith(".json", ignoreCase = true)
                }
                configure(this)
            }
        }.getOrNull()

    private fun resolve(dialog: FileDialog?): File? {
        val directory = dialog?.directory ?: return null
        val name = dialog.file ?: return null
        return File(directory, name)
    }

    private companion object {
        const val TITLE = "Xinyi Relay"
    }
}
