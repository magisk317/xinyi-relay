package com.github.magisk317.smscode.common.utils

import android.content.Context
import java.io.File
import java.io.IOException

object ModuleActivationStore {
    private const val FILE_NAME = "module_activated"
    private const val MAX_ACTIVE_AGE_MS = 24 * 60 * 60 * 1000L

    fun markActivated(context: Context) {
        val file = getStoreFile(context)
        try {
            file.parentFile?.mkdirs()
            file.writeText(System.currentTimeMillis().toString())
        } catch (_: IOException) {
            // ignore
        }
    }

    fun isActivatedRecently(context: Context): Boolean {
        val last = getLastActivated(context)
        if (last <= 0L) return false
        return System.currentTimeMillis() - last <= MAX_ACTIVE_AGE_MS
    }

    private fun getLastActivated(context: Context): Long {
        val file = getStoreFile(context)
        return try {
            val text = file.readText().trim()
            text.toLongOrNull() ?: file.lastModified()
        } catch (_: IOException) {
            0L
        }
    }

    private fun getStoreFile(context: Context): File {
        val dir = StorageUtils.getExternalFilesDir(context)
        return File(dir, FILE_NAME)
    }
}
