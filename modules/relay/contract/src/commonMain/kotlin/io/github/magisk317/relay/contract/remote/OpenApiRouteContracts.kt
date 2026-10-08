package io.github.magisk317.relay.contract.remote

data class OpenApiRouteContract(
    val path: String,
    val method: String,
    val operationId: String,
    val requestSchema: String? = null,
    val requestContentType: String = "application/json",
    val requestRequired: Boolean = true,
    val parameters: List<OpenApiRouteParameter> = emptyList(),
    val responses: List<OpenApiRouteResponse> = listOf(
        OpenApiRouteResponse(status = "200"),
    ),
)

data class OpenApiRouteParameter(
    val name: String,
    val location: String,
    val required: Boolean = true,
    val schema: String = "String",
)

data class OpenApiRouteResponse(
    val status: String,
    val schema: String? = null,
    val description: String = "",
    val contentType: String = "application/json",
)

object OpenApiRouteContracts {
    val ALL_ROUTES: List<OpenApiRouteContract> = listOf(
        OpenApiRouteContract(
            path = "/healthz",
            method = "get",
            operationId = "getHealth",
            responses = listOf(OpenApiRouteResponse(status = "200", schema = "HealthResponse")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/system/info",
            method = "get",
            operationId = "getSystemInfo",
            responses = listOf(OpenApiRouteResponse(status = "200", schema = "SystemInfoResponse")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/bootstrap/admin",
            method = "post",
            operationId = "bootstrapAdmin",
            requestSchema = "BootstrapAdminRequest",
            responses = listOf(OpenApiRouteResponse(status = "201", schema = "BootstrapAdminResponse")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/auth/login",
            method = "post",
            operationId = "login",
            requestSchema = "LoginRequest",
            responses = listOf(OpenApiRouteResponse(status = "200", schema = "LoginResponse")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/auth/logout",
            method = "post",
            operationId = "logout",
            responses = listOf(OpenApiRouteResponse(status = "200", schema = "SimpleOKResponse")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/auth/password",
            method = "post",
            operationId = "changePassword",
            requestSchema = "ChangePasswordRequest",
            responses = listOf(OpenApiRouteResponse(status = "200", schema = "SimpleOKResponse")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/auth/me",
            method = "get",
            operationId = "getMe",
            responses = listOf(OpenApiRouteResponse(status = "200", schema = "MeResponse")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/auth/desktop/start",
            method = "get",
            operationId = "getDesktopAuthStartPage",
            parameters = listOf(
                OpenApiRouteParameter(name = "redirect_uri", location = "query"),
                OpenApiRouteParameter(name = "state", location = "query"),
                OpenApiRouteParameter(name = "client_name", location = "query", required = false),
            ),
            responses = listOf(
                OpenApiRouteResponse(status = "200", schema = "String", contentType = "text/html"),
            ),
        ),
        OpenApiRouteContract(
            path = "/api/v1/auth/desktop/start",
            method = "post",
            operationId = "approveDesktopAuthStart",
            requestSchema = "DesktopLoginPageRequest",
            requestContentType = "application/x-www-form-urlencoded",
            responses = listOf(OpenApiRouteResponse(status = "303", description = "Desktop auth callback redirect")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/auth/desktop/exchange",
            method = "post",
            operationId = "exchangeDesktopAuthCode",
            requestSchema = "DesktopExchangeRequest",
            responses = listOf(OpenApiRouteResponse(status = "200", schema = "DesktopSessionResponse")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/auth/desktop/refresh",
            method = "post",
            operationId = "refreshDesktopSession",
            requestSchema = "DesktopRefreshRequest",
            responses = listOf(OpenApiRouteResponse(status = "200", schema = "DesktopSessionResponse")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/auth/desktop/logout",
            method = "post",
            operationId = "logoutDesktopSession",
            requestSchema = "DesktopLogoutRequest",
            requestRequired = false,
            responses = listOf(OpenApiRouteResponse(status = "200", schema = "SimpleOKResponse")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/devices/bind-codes",
            method = "post",
            operationId = "createBindCode",
            responses = listOf(OpenApiRouteResponse(status = "201", schema = "BindCodeResponse")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/devices",
            method = "get",
            operationId = "listDevices",
            responses = listOf(OpenApiRouteResponse(status = "200", schema = "DevicesResponse")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/devices/{id}",
            method = "patch",
            operationId = "patchDevice",
            requestSchema = "PatchDeviceRequest",
            parameters = listOf(OpenApiRouteParameter(name = "id", location = "path", schema = "Long")),
            responses = listOf(OpenApiRouteResponse(status = "200", schema = "DeviceItem")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/devices/{id}/revoke",
            method = "post",
            operationId = "revokeDevice",
            parameters = listOf(OpenApiRouteParameter(name = "id", location = "path", schema = "Long")),
            responses = listOf(OpenApiRouteResponse(status = "200", schema = "SimpleOKResponse")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/devices/{id}/config",
            method = "get",
            operationId = "getDeviceConfig",
            parameters = listOf(OpenApiRouteParameter(name = "id", location = "path", schema = "Long")),
            responses = listOf(OpenApiRouteResponse(status = "200", schema = "DeviceConfigStateResponse")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/devices/{id}/config/commands",
            method = "post",
            operationId = "queueDeviceConfigCommand",
            requestSchema = "DeviceConfigCommandRequest",
            parameters = listOf(OpenApiRouteParameter(name = "id", location = "path", schema = "Long")),
            responses = listOf(OpenApiRouteResponse(status = "201", schema = "DeviceConfigCommandItem")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/devices/{id}/config/audit",
            method = "get",
            operationId = "listDeviceConfigAuditLogs",
            parameters = listOf(
                OpenApiRouteParameter(name = "id", location = "path", schema = "Long"),
                OpenApiRouteParameter(name = "limit", location = "query", required = false, schema = "Int"),
                OpenApiRouteParameter(name = "offset", location = "query", required = false, schema = "Int"),
            ),
            responses = listOf(OpenApiRouteResponse(status = "200", schema = "DeviceConfigAuditLogsResponse")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/agent/register",
            method = "post",
            operationId = "registerAgent",
            requestSchema = "AgentRegisterRequest",
            responses = listOf(OpenApiRouteResponse(status = "201", schema = "AgentRegisterResponse")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/agent/heartbeat",
            method = "post",
            operationId = "sendAgentHeartbeat",
            requestSchema = "HeartbeatRequest",
            responses = listOf(OpenApiRouteResponse(status = "200", schema = "SimpleOKResponse")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/agent/config/mirror",
            method = "post",
            operationId = "pushAgentConfigMirror",
            requestSchema = "AgentConfigMirrorRequest",
            responses = listOf(OpenApiRouteResponse(status = "200", schema = "DeviceConfigStateResponse")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/agent/config/commands:pull",
            method = "post",
            operationId = "pullAgentConfigCommands",
            requestSchema = "AgentConfigCommandsPullRequest",
            responses = listOf(OpenApiRouteResponse(status = "200", schema = "AgentConfigCommandsPullResponse")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/agent/config/commands:ack",
            method = "post",
            operationId = "ackAgentConfigCommands",
            requestSchema = "AgentConfigCommandsAckRequest",
            responses = listOf(OpenApiRouteResponse(status = "200", schema = "DeviceConfigCommandItem")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/agent/records:batch",
            method = "post",
            operationId = "ingestAgentRecords",
            requestSchema = "RelayRecordsBatchRequest",
            responses = listOf(OpenApiRouteResponse(status = "200", schema = "RelayRecordsBatchResponse")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/records",
            method = "get",
            operationId = "listRecords",
            parameters = listOf(
                OpenApiRouteParameter(name = "limit", location = "query", required = false, schema = "Int"),
                OpenApiRouteParameter(name = "offset", location = "query", required = false, schema = "Int"),
                OpenApiRouteParameter(name = "device_id", location = "query", required = false, schema = "Long"),
            ),
            responses = listOf(OpenApiRouteResponse(status = "200", schema = "RecordsResponse")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/records/{id}",
            method = "get",
            operationId = "getRecord",
            parameters = listOf(OpenApiRouteParameter(name = "id", location = "path", schema = "Long")),
            responses = listOf(OpenApiRouteResponse(status = "200", schema = "RelayRecord")),
        ),
        OpenApiRouteContract(
            path = "/api/v1/realtime/ws",
            method = "get",
            operationId = "connectRealtime",
            responses = listOf(OpenApiRouteResponse(status = "101", description = "WebSocket upgrade")),
        ),
    )
}
