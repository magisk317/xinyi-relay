package io.github.magisk317.relay.contract.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String = "",
)

@Serializable
data class HealthResponse(
    val ok: Boolean = false,
    val service: String = "",
)

@Serializable
data class SystemInfoResponse(
    val service: String = "",
    val appEnv: String = "",
    val localBaseUrl: String = "",
    val publicBaseUrl: String = "",
    val databaseReady: Boolean = false,
    val userCount: Long = 0L,
    val time: String = "",
)

@Serializable
data class BootstrapAdminRequest(
    val username: String,
    val password: String,
)

@Serializable
data class BootstrapAdminResponse(
    val ok: Boolean = false,
    val userId: Long = 0L,
    val username: String = "",
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val authenticated: Boolean = false,
    val username: String = "",
    val csrfToken: String = "",
    val languageTag: String? = null,
)

@Serializable
data class MeResponse(
    val authenticated: Boolean = false,
    val username: String? = null,
    val csrfToken: String? = null,
    val languageTag: String? = null,
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

@Serializable
data class DesktopLoginPageRequest(
    val username: String,
    val password: String,
    @SerialName("redirect_uri")
    val redirectUri: String,
    val state: String,
    @SerialName("client_name")
    val clientName: String,
)

@Serializable
data class DesktopExchangeRequest(
    val code: String,
)

@Serializable
data class DesktopRefreshRequest(
    val refreshToken: String,
)

@Serializable
data class DesktopLogoutRequest(
    val refreshToken: String,
)

@Serializable
data class DesktopSessionResponse(
    val authenticated: Boolean = false,
    val username: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAt: String = "",
    val refreshExpiresAt: String = "",
)

@Serializable
data class SimpleOKResponse(
    val ok: Boolean = false,
)

@Serializable
data class BindCodeResponse(
    val code: String = "",
    val expiresAt: String = "",
)

@Serializable
data class PatchDeviceRequest(
    val displayName: String? = null,
    val enabled: Boolean? = null,
)
