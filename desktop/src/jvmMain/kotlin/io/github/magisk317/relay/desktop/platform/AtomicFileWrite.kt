package io.github.magisk317.relay.desktop.platform

import java.io.File

/**
 * Writes [text] beside [target] as a temporary file and renames it into
 * place, so a half-written file never appears under the chosen name. Falls
 * back to copy-and-delete where a rename across devices is refused, and
 * always cleans the temporary up.
 *
 * Shared by the database snapshot export and the diagnostics export: both
 * write user-chosen files whose interrupted state must not be mistakable
 * for a finished artifact.
 */
internal fun writeAtomically(target: File, text: String) {
    val parent = target.parentFile ?: File(".")
    parent.mkdirs()
    val temporary = File.createTempFile("xinyi-export-", ".json.tmp", parent)
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
