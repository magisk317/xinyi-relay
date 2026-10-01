package io.github.magisk317.relay.contract.remote

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenApiRouteContractsTest {

    @Test
    fun routesDeclareResponses() {
        assertFalse(OpenApiRouteContracts.ALL_ROUTES.isEmpty())
        OpenApiRouteContracts.ALL_ROUTES.forEach { route ->
            assertFalse(route.responses.isEmpty(), "${route.method} ${route.path} should declare responses")
            route.responses.forEach { response ->
                assertTrue(response.status.isNotBlank(), "${route.method} ${route.path} has blank response status")
            }
        }
    }

    @Test
    fun routesHaveUniqueOperationIdsAndMethodPaths() {
        val duplicateOperationIds = OpenApiRouteContracts.ALL_ROUTES
            .groupBy { it.operationId }
            .filterValues { it.size > 1 }
            .keys
        assertTrue(duplicateOperationIds.isEmpty(), "duplicate operationIds: $duplicateOperationIds")

        val duplicateMethodPaths = OpenApiRouteContracts.ALL_ROUTES
            .groupBy { it.method.lowercase() to it.path }
            .filterValues { it.size > 1 }
            .keys
        assertTrue(duplicateMethodPaths.isEmpty(), "duplicate route method/path pairs: $duplicateMethodPaths")
    }

    @Test
    fun declaredPathParametersMatchPathTemplates() {
        OpenApiRouteContracts.ALL_ROUTES.forEach { route ->
            val placeholders = Regex("""\{([^}]+)}""")
                .findAll(route.path)
                .map { it.groupValues[1] }
                .toSet()
            val pathParameters = route.parameters
                .filter { it.location == "path" }
                .map { it.name }
                .toSet()
            assertEquals(placeholders, pathParameters, "${route.method} ${route.path} path parameters drifted")
        }
    }

    @Test
    fun pathIdRoutesDeclareRequiredPathParameter() {
        OpenApiRouteContracts.ALL_ROUTES
            .filter { it.path.contains("{id}") }
            .forEach { route ->
                val idParameter = route.parameters.singleOrNull { it.name == "id" && it.location == "path" }
                assertTrue(idParameter != null, "${route.method} ${route.path} should declare id path parameter")
                assertEquals(true, idParameter?.required, "${route.method} ${route.path} id parameter should be required")
                assertEquals("Long", idParameter?.schema, "${route.method} ${route.path} id parameter should be Long")
            }
    }

    @Test
    fun mutatingConfigRoutesDeclareRequestSchemas() {
        val schemasByOperation = OpenApiRouteContracts.ALL_ROUTES.associateBy { it.operationId }
        val expected = mapOf(
            "queueDeviceConfigCommand" to "DeviceConfigCommandRequest",
            "pushAgentConfigMirror" to "AgentConfigMirrorRequest",
            "pullAgentConfigCommands" to "AgentConfigCommandsPullRequest",
            "ackAgentConfigCommands" to "AgentConfigCommandsAckRequest",
            "ingestAgentRecords" to "RelayRecordsBatchRequest",
        )

        expected.forEach { (operationId, schema) ->
            val route = schemasByOperation.getValue(operationId)
            assertEquals(schema, route.requestSchema, "$operationId request schema drifted")
            assertEquals(true, route.requestRequired, "$operationId request body should be required")
        }
    }

    @Test
    fun desktopLogoutRequestBodyIsOptional() {
        val route = OpenApiRouteContracts.ALL_ROUTES.single { it.operationId == "logoutDesktopSession" }

        assertEquals("DesktopLogoutRequest", route.requestSchema)
        assertEquals(false, route.requestRequired)
    }
}
