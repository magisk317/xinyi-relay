package io.github.magisk317.relay.contract.remote

import io.github.magisk317.relay.contract.json.RelayJson
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class AgentApiContractsOpenApiTest {

    @Test
    fun androidAgentWireDtos_matchOpenApiSchemaFields() {
        val schemas = loadOpenApiSchemas()
        val cases = listOf(
            "ErrorResponse" to ErrorResponse.serializer().descriptor,
            "HealthResponse" to HealthResponse.serializer().descriptor,
            "SystemInfoResponse" to SystemInfoResponse.serializer().descriptor,
            "BootstrapAdminRequest" to BootstrapAdminRequest.serializer().descriptor,
            "BootstrapAdminResponse" to BootstrapAdminResponse.serializer().descriptor,
            "LoginRequest" to LoginRequest.serializer().descriptor,
            "LoginResponse" to LoginResponse.serializer().descriptor,
            "MeResponse" to MeResponse.serializer().descriptor,
            "ChangePasswordRequest" to ChangePasswordRequest.serializer().descriptor,
            "DesktopLoginPageRequest" to DesktopLoginPageRequest.serializer().descriptor,
            "DesktopExchangeRequest" to DesktopExchangeRequest.serializer().descriptor,
            "DesktopRefreshRequest" to DesktopRefreshRequest.serializer().descriptor,
            "DesktopLogoutRequest" to DesktopLogoutRequest.serializer().descriptor,
            "DesktopSessionResponse" to DesktopSessionResponse.serializer().descriptor,
            "SimpleOKResponse" to SimpleOKResponse.serializer().descriptor,
            "BindCodeResponse" to BindCodeResponse.serializer().descriptor,
            "PatchDeviceRequest" to PatchDeviceRequest.serializer().descriptor,
            "DeviceItem" to DeviceItem.serializer().descriptor,
            "DevicesResponse" to DevicesResponse.serializer().descriptor,
            "RealtimeEvent" to RealtimeEvent.serializer().descriptor,
            "AgentRegisterRequest" to AgentRegisterRequest.serializer().descriptor,
            "AgentRegisterResponse" to AgentRegisterResponse.serializer().descriptor,
            "HeartbeatRequest" to HeartbeatRequest.serializer().descriptor,
            "AgentConfigMirrorRequest" to AgentConfigMirrorRequest.serializer().descriptor,
            "AgentConfigCommandsPullRequest" to AgentConfigCommandsPullRequest.serializer().descriptor,
            "AgentConfigCommandsPullResponse" to AgentConfigCommandsPullResponse.serializer().descriptor,
            "AgentConfigCommandsAckRequest" to AgentConfigCommandsAckRequest.serializer().descriptor,
            "DeviceConfigCommandRequest" to DeviceConfigCommandRequest.serializer().descriptor,
            "DeviceConfigCommandItem" to DeviceConfigCommandResponse.serializer().descriptor,
            "DeviceConfigAuditLogItem" to DeviceConfigAuditLogItem.serializer().descriptor,
            "DeviceConfigAuditLogsResponse" to DeviceConfigAuditLogsResponse.serializer().descriptor,
            "RelayRecord" to RelayRecord.serializer().descriptor,
            "RecordsResponse" to RecordsResponse.serializer().descriptor,
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
            val candidates = listOf(
                File(dir, "frontend/shared/contracts/openapi.json"),
                File(dir, "shared/contracts/openapi.json"),
            )
            candidates.firstOrNull { it.isFile }?.let { return it }
            dir = dir.parentFile ?: break
        }
        error(
            buildString {
                append("openapi.json not found from ")
                append(File("").absolutePath)
                append(" (looked for frontend/shared/contracts/openapi.json and shared/contracts/openapi.json)")
            }
        )
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
