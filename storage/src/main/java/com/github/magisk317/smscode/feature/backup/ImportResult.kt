package com.github.magisk317.smscode.feature.backup

enum class ImportResult {
    /**
     * Success
     */
    SUCCESS,

    /**
     * Backup version missed
     */
    VERSION_MISSED,

    /**
     * Backup version unknown
     */
    VERSION_UNKNOWN,

    /**
     * Backup version too new
     */
    VERSION_TOO_NEW,

    /**
     * Backup version too old
     */
    VERSION_TOO_OLD,

    /**
     * Backup invalid
     */
    BACKUP_INVALID,

    /**
     * Read error
     */
    READ_FAILED,
}
