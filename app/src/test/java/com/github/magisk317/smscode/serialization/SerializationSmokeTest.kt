package com.github.magisk317.smscode.serialization

import com.github.magisk317.smscode.common.utils.JsonUtils
import com.github.magisk317.smscode.feature.backup.BackupConst
import com.github.magisk317.smscode.feature.backup.BackupPayload
import com.github.magisk317.smscode.feature.backup.BackupRule
import com.github.magisk317.smscode.feature.backup.RuleExporter
import com.github.magisk317.smscode.feature.backup.RuleImporter
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SerializationSmokeTest {

    @Test
    fun backupExportRoundTrip() {
        val rules = listOf(BackupRule(company = "ACME", codeKeyword = "code", codeRegex = "\\d{6}"))
        val output = ByteArrayOutputStream()

        RuleExporter(output).use { exporter ->
            exporter.doExport(rules, "3.0.0")
        }

        val jsonString = output.toString(Charsets.UTF_8.name())
        val payload = JsonUtils.json.decodeFromString<BackupPayload>(jsonString)
        assertEquals(BackupConst.BACKUP_VERSION, payload.version)
        assertEquals(BackupConst.BACKUP_VERSION, payload.schemaVersion)
        assertEquals(1, payload.rules.size)
        assertEquals("ACME", payload.rules.first().company)
    }

    @Test
    fun backupImportParsesRules() {
        val payload = BackupPayload(
            rules = listOf(BackupRule(company = "ACME", codeKeyword = "code", codeRegex = "\\d{6}")),
        )
        val json = JsonUtils.json.encodeToString(payload)
        val input = ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))

        RuleImporter(input).use { importer ->
            val importedPayload = importer.parsePayload()
            assertEquals(BackupConst.BACKUP_VERSION, importedPayload.schemaVersion)
            assertEquals(1, importedPayload.rules.size)
            assertEquals("ACME", importedPayload.rules.first().company)
        }
    }
}
