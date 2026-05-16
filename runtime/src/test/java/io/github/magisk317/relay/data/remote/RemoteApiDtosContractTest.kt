package io.github.magisk317.relay.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.lang.reflect.Modifier

class RemoteApiDtosContractTest {

    @Test
    fun androidAgentWireDtos_matchOpenApiSchemaFields() {
        val schemas = loadOpenApiSchemas()
        val cases = listOf(
            "AgentRegisterRequest" to AgentRegisterRequest::class.java,
            "AgentRegisterResponse" to AgentRegisterResponse::class.java,
            "HeartbeatRequest" to HeartbeatRequest::class.java,
            "ConfigSnapshotRequest" to ConfigSnapshotRequest::class.java,
            "ConfigSnapshotResponse" to ConfigSnapshotResponse::class.java,
            "RelayRecordWire" to RelayRecordWire::class.java,
            "RelayRecordsBatchRequest" to RelayRecordsBatchRequest::class.java,
        )

        cases.forEach { (schemaName, dtoClass) ->
            val schemaFields = schemaFields(schemas, schemaName)
            val dtoFields = jsonFieldNames(dtoClass)
            assertEquals(schemaFields, dtoFields, "$schemaName fields drifted")
        }
    }

    private fun loadOpenApiSchemas(): JsonObject {
        val file = findOpenApiFile()
        val root = JsonParser.parseReader(file.reader()).asJsonObject
        return root
            .getAsJsonObject("components")
            .getAsJsonObject("schemas")
    }

    private fun findOpenApiFile(): File {
        var dir = File("").absoluteFile
        while (true) {
            val candidate = File(dir, "shared/contracts/openapi.json")
            if (candidate.isFile) {
                return candidate
            }
            dir = dir.parentFile ?: break
        }
        error("shared/contracts/openapi.json not found from ${File("").absolutePath}")
    }

    private fun schemaFields(schemas: JsonObject, schemaName: String): List<String> {
        assertTrue(schemas.has(schemaName), "schema $schemaName should exist")
        return schemas
            .getAsJsonObject(schemaName)
            .getAsJsonObject("properties")
            .keySet()
            .sorted()
    }

    private fun jsonFieldNames(clazz: Class<*>): List<String> {
        return clazz.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .map { field ->
                field.getAnnotation(SerializedName::class.java)?.value ?: field.name
            }
            .sorted()
    }
}
