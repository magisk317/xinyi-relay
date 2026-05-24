package io.github.magisk317.relay.sender

import io.github.magisk317.relay.contract.json.RelayJson
import io.github.magisk317.relay.engine.sender.SenderType
import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SenderSettingSchemasTest {

    @Test
    fun allKnownSenderTypes_haveSchema() {
        val expectedTypes = listOf(
            SenderType.DINGTALK_GROUP_ROBOT,
            SenderType.EMAIL,
            SenderType.BARK,
            SenderType.WEBHOOK,
            SenderType.WEWORK_ROBOT,
            SenderType.WEWORK_AGENT,
            SenderType.SERVERCHAN,
            SenderType.TELEGRAM,
            SenderType.SMS,
            SenderType.FEISHU,
            SenderType.PUSHPLUS,
            SenderType.GOTIFY,
            SenderType.DINGTALK_INNER_ROBOT,
            SenderType.FEISHU_APP,
            SenderType.URL_SCHEME,
            SenderType.SOCKET,
            SenderType.NTFY,
        )

        assertEquals(expectedTypes, SenderSettingSchemas.all.map { it.senderType })
        expectedTypes.forEach { type ->
            val schema = SenderSettingSchemas.schemaFor(type)
            assertNotNull(schema, "Missing schema for sender type $type")
            assertFalse(SenderSettingSchemas.fieldsFor(type).isEmpty(), "Missing fields for sender type $type")
        }
    }

    @Test
    fun schemaFields_haveStableNamesAndUniqueAliasesPerType() {
        SenderSettingSchemas.all.forEach { schema ->
            val names = schema.fields.map { it.name }
            assertEquals(
                names.distinct(),
                names,
                "Duplicate field names for sender type ${schema.senderType}",
            )

            schema.fields.forEach { field ->
                assertTrue(field.name.isNotBlank(), "Blank field name for sender type ${schema.senderType}")
                assertEquals(
                    field.aliases.distinct(),
                    field.aliases,
                    "Duplicate aliases for ${schema.senderType}.${field.name}",
                )
                assertFalse(
                    field.name in field.aliases,
                    "Canonical field listed as alias for ${schema.senderType}.${field.name}",
                )
                field.aliases.forEach { alias ->
                    assertTrue(alias.isNotBlank(), "Blank alias for ${schema.senderType}.${field.name}")
                }
            }
        }
    }

    @Test
    fun requiredAndTypedFields_areExposedForCrossClientForms() {
        assertRequired(SenderType.WEBHOOK, "webServer")
        assertRequired(SenderType.TELEGRAM, "apiToken", "chatId")
        assertRequired(SenderType.EMAIL, "fromEmail", "pwd", "recipients", "toEmail")
        assertRequired(SenderType.SOCKET, "address", "port")

        assertFieldType(SenderType.WEBHOOK, "headers", SenderSettingFieldType.STRING_MAP)
        assertFieldType(SenderType.TELEGRAM, "apiToken", SenderSettingFieldType.SECRET)
        assertFieldType(SenderType.SOCKET, "port", SenderSettingFieldType.INTEGER)
        assertFieldType(SenderType.SMS, "onlyNoNetwork", SenderSettingFieldType.BOOLEAN)
    }

    @Test
    fun fieldDefaultsAndOptions_areExposedForSchemaDrivenForms() {
        assertFieldDefault(SenderType.TELEGRAM, "method", "POST")
        assertFieldDefault(SenderType.TELEGRAM, "parseMode", "HTML")
        assertFieldDefault(SenderType.TELEGRAM, "proxyType", "DIRECT")
        assertFieldDefault(SenderType.PUSHPLUS, "website", "www.pushplus.plus")
        assertFieldDefault(SenderType.PUSHPLUS, "template", "html")
        assertFieldDefault(SenderType.PUSHPLUS, "channel", "wechat")

        assertFieldOptions(SenderType.TELEGRAM, "method", "GET", "POST")
        assertFieldOptions(SenderType.TELEGRAM, "parseMode", "HTML", "MarkdownV2")
        assertFieldOptions(SenderType.TELEGRAM, "proxyType", "DIRECT", "HTTP", "SOCKS")
    }

    @Test
    fun sharedSenderSchemaContract_matchesKotlinSchema() {
        val contractFile = findWorkspaceFile("shared/contracts/senderSchemas.json")
        val sharedSchemas = RelayJson.decode(
            ListSerializer(SenderSettingSchema.serializer()),
            contractFile.readText(),
        )

        assertEquals(SenderSettingSchemas.all, sharedSchemas)
    }

    private fun assertRequired(type: Int, vararg names: String) {
        val requiredNames = SenderSettingSchemas.fieldsFor(type)
            .filter { it.requiredForEnable }
            .map { it.name }
        names.forEach { name ->
            assertTrue(name in requiredNames, "Expected $type.$name to be required")
        }
    }

    private fun assertFieldType(type: Int, name: String, fieldType: SenderSettingFieldType) {
        val field = SenderSettingSchemas.fieldsFor(type)
            .singleOrNull { it.name == name }
        assertNotNull(field, "Missing $type.$name")
        assertEquals(fieldType, field?.type, "Unexpected type for $type.$name")
    }

    private fun assertFieldDefault(type: Int, name: String, defaultValue: String) {
        val field = SenderSettingSchemas.fieldsFor(type)
            .singleOrNull { it.name == name }
        assertNotNull(field, "Missing $type.$name")
        assertEquals(defaultValue, field?.defaultValue, "Unexpected default for $type.$name")
    }

    private fun assertFieldOptions(type: Int, name: String, vararg values: String) {
        val field = SenderSettingSchemas.fieldsFor(type)
            .singleOrNull { it.name == name }
        assertNotNull(field, "Missing $type.$name")
        assertEquals(values.toList(), field?.options?.map { it.value }, "Unexpected options for $type.$name")
    }

    private fun findWorkspaceFile(relativePath: String): File {
        var dir = File("").absoluteFile
        while (true) {
            val candidate = File(dir, relativePath)
            if (candidate.isFile) return candidate
            dir = dir.parentFile ?: break
        }
        error("$relativePath not found from ${File("").absolutePath}")
    }
}
