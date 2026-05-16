package io.github.magisk317.relay.data.remote

import io.github.magisk317.relay.contract.json.RelayJson
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class RemoteApiDtosContractTest {

    @Test
    fun androidAgentWireDtos_matchOpenApiSchemaFields() {
        val schemas = loadOpenApiSchemas()
        val cases = listOf(
            "AgentRegisterRequest" to AgentRegisterRequest.serializer().descriptor,
            "AgentRegisterResponse" to AgentRegisterResponse.serializer().descriptor,
            "HeartbeatRequest" to HeartbeatRequest.serializer().descriptor,
            "ConfigSnapshotRequest" to ConfigSnapshotRequest.serializer().descriptor,
            "ConfigSnapshotResponse" to ConfigSnapshotResponse.serializer().descriptor,
            "RelayRecordWire" to RelayRecordWire.serializer().descriptor,
            "RelayRecordsBatchRequest" to RelayRecordsBatchRequest.serializer().descriptor,
        )

        cases.forEach { (schemaName, descriptor) ->
            val schemaFields = schemaFields(schemas, schemaName)
            val dtoFields = jsonFieldNames(descriptor)
            assertEquals(schemaFields, dtoFields, "$schemaName fields drifted")
        }
    }

    private fun loadOpenApiSchemas(): JsonObject {
        val file = findOpenApiFile()
        val root = RelayJson.parseElement(file.readText()).jsonObject
        return root
            .getValue("components").jsonObject
            .getValue("schemas").jsonObject
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
        assertTrue(schemas.containsKey(schemaName), "schema $schemaName should exist")
        return schemas
            .getValue(schemaName).jsonObject
            .getValue("properties").jsonObject
            .keys
            .sorted()
    }

    private fun jsonFieldNames(descriptor: SerialDescriptor): List<String> {
        return List(descriptor.elementsCount) { index -> descriptor.getElementName(index) }
            .sorted()
    }
}
