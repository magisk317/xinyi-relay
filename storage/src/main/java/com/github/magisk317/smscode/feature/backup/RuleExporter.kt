package com.github.magisk317.smscode.feature.backup

import com.github.magisk317.smscode.common.utils.JsonUtils
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * SmsCode rules exporter
 */
class RuleExporter(out: OutputStream?) : Closeable {
    private val writer = OutputStreamWriter(out, StandardCharsets.UTF_8)

    constructor(file: File?) : this(FileOutputStream(file))

    @Throws(IOException::class)
    fun doExport(
        ruleList: List<BackupRule>,
        appVersion: String,
        preferences: Map<String, String?>? = null,
        records: List<BackupSmsRecord>? = null,
    ) {
        val payload = BackupPayload(
            version = BackupConst.BACKUP_VERSION,
            schemaVersion = BackupConst.BACKUP_VERSION,
            appVersion = appVersion,
            rules = ruleList,
            preferences = preferences,
            records = records,
        )
        writer.write(JsonUtils.json.encodeToString(BackupPayload.serializer(), payload))
        writer.flush()
    }

    override fun close() {
        try {
            writer.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
