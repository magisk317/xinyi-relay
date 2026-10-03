package io.github.magisk317.relay.desktop.platform

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock

/**
 * Cross-process single-instance guard.
 *
 * The Tauri track gets this from a plugin; on the JVM the portable answer is an
 * advisory lock on a file in the user data directory - `FileLock` is released
 * by the OS when the holder dies, so a crashed instance never wedges the next
 * launch the way a stale PID file does.
 *
 * The lock file lives under a private directory (mode 700) because the lock
 * must be creatable by the first launch even on a fresh machine, and a
 * world-writable lock file would let any local user hold the app hostage.
 */
class FileLockInstanceGuard(private val lockFile: File) : AutoCloseable {

    private var channel: RandomAccessFile? = null
    private var lock: FileLock? = null

    /** True when this process holds the lock. */
    var acquired: Boolean = false
        private set

    /**
     * Attempts to take the lock.
     *
     * @return true when acquired, false when another live process already
     *   holds it (or the lock could not be created at all - treated as "not
     *   acquired" so the caller stays conservative and refuses to start a
     *   second copy, which is the safe direction to fail in).
     */
    fun acquire(): Boolean = runCatching {
        lockFile.parentFile?.let { parent ->
            if (!parent.exists()) check(parent.mkdirs()) { "cannot create ${parent.path}" }
        }
        val raf = RandomAccessFile(lockFile, "rw")
        channel = raf
        val taken = raf.channel.tryLock() ?: return false
        lock = taken
        acquired = true
        true
    }.getOrDefault(false)

    override fun close() {
        runCatching { lock?.release() }
        runCatching { channel?.close() }
        lock = null
        channel = null
        acquired = false
    }

    companion object {

        /** Default lock location: `<user home>/.xinyi-relay/instance.lock`. */
        fun defaultLockFile(userHome: String = System.getProperty("user.home")): File =
            File(userHome, ".xinyi-relay/instance.lock")
    }
}
