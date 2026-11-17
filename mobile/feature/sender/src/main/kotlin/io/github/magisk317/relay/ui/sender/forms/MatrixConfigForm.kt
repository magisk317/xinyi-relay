package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.mobile.core.R
import io.github.magisk317.forward.engine.sender.SenderType
import io.github.magisk317.forward.sender.E2eeModuleStatus
import io.github.magisk317.forward.sender.MatrixE2eeAvailability
import io.github.magisk317.forward.sender.MatrixE2eeAvailabilityProvider
import io.github.magisk317.forward.sender.MatrixE2eeVerificationProvider
import io.github.magisk317.forward.sender.MatrixE2eeVerificationState
import io.github.magisk317.forward.sender.MatrixE2eeVerificationStatus
import io.github.magisk317.forward.sender.SenderSettingJson
import io.github.magisk317.forward.sender.SenderSettingDraft
import io.github.magisk317.forward.sender.config.MatrixSetting
import io.github.magisk317.relay.ui.sender.SenderViewModel
import io.github.magisk317.uikit.common.showLatestSnackbar
import io.github.magisk317.uikit.foundation.LocalSnackbarHostState
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppCard
import io.github.magisk317.uikit.surface.AppFilterChip
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.surface.AppLinearProgressIndicator
import io.github.magisk317.uikit.surface.AppPrimaryButton
import io.github.magisk317.uikit.surface.AppSecondaryButton
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor
import kotlinx.coroutines.launch

/** Stored value of [io.github.magisk317.forward.contract.model.ProxyType.DIRECT]. */
private const val MATRIX_PROXY_TYPE_DIRECT = "DIRECT"

internal val MatrixVisibleFields = listOf(
    SchemaSenderFormFieldSpec(
        name = "homeserver",
        labelRes = R.string.sender_form_label_matrix_homeserver_required,
        supportingTextRes = R.string.sender_form_label_matrix_homeserver_example,
    ),
    SchemaSenderFormFieldSpec(
        name = "username",
        labelRes = R.string.sender_form_label_matrix_username_required,
        supportingTextRes = R.string.sender_form_label_matrix_username_example,
    ),
    SchemaSenderFormFieldSpec(
        name = "password",
        labelRes = R.string.sender_form_label_matrix_password_required,
        isSecret = true,
    ),
    SchemaSenderFormFieldSpec(
        name = "roomId",
        labelRes = R.string.sender_form_label_matrix_room_id_required,
        placeholderRes = R.string.sender_form_label_matrix_room_id_share_link_hint,
        supportingTextRes = R.string.sender_form_label_matrix_room_id_share_link_hint,
    ),
    SchemaSenderFormFieldSpec(
        name = "messageType",
        labelRes = R.string.sender_form_label_message_type,
        optionLabelRes = MessageTypeOptionLabels,
    ),
    SchemaSenderFormFieldSpec(
        name = "titleTemplate",
        labelRes = R.string.sender_form_title_template_label,
        placeholderRes = R.string.sender_form_title_template_placeholder,
    ),
    SchemaSenderFormFieldSpec(
        name = "proxyType",
        labelRes = R.string.sender_form_label_proxy_type,
    ),
    SchemaSenderFormFieldSpec(
        name = "proxyHost",
        labelRes = R.string.sender_form_label_proxy_host,
        visible = { !it.isMatrixProxyDirect() },
    ),
    SchemaSenderFormFieldSpec(
        name = "proxyPort",
        labelRes = R.string.sender_form_label_proxy_port,
        visible = { !it.isMatrixProxyDirect() },
    ),
    SchemaSenderFormFieldSpec(
        name = "proxyAuthenticator",
        labelRes = R.string.sender_form_label_proxy_authenticator,
        visible = { !it.isMatrixProxyDirect() },
    ),
    SchemaSenderFormFieldSpec(
        name = "proxyUsername",
        labelRes = R.string.sender_form_label_proxy_username,
        visible = { !it.isMatrixProxyDirect() && it.boolean("proxyAuthenticator") },
    ),
    SchemaSenderFormFieldSpec(
        name = "proxyPassword",
        labelRes = R.string.sender_form_label_proxy_password,
        visible = { !it.isMatrixProxyDirect() && it.boolean("proxyAuthenticator") },
    ),
)

/**
 * Proxy settings only exist for HTTP/SOCKS; DIRECT sends straight to the homeserver.
 * Mirrors [io.github.magisk317.forward.sender.config.ProxyTypeSerializer], which also
 * falls back to DIRECT for blank or unknown values.
 */
private fun SenderSettingDraft.isMatrixProxyDirect(): Boolean {
    val value = string("proxyType").trim().uppercase()
    return value.isEmpty() || value == MATRIX_PROXY_TYPE_DIRECT
}

@Composable
fun MatrixConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    val availability = remember { MatrixE2eeAvailabilityProvider.get() }
    // The E2EE management UI only appears once the user opts into e2ee, so the form
    // starts on the default HTTPS mode. The opt-in is forced while the module is
    // installed: the runtime encrypts every encrypted room as soon as the plugin is
    // loaded, so HTTPS stays unavailable until the module is uninstalled. The
    // loaders expose plain getters over non-observable state, so the flag is kept as
    // composable state and moves with the install/uninstall transitions.
    var e2eeInstalled by remember { mutableStateOf(availability.isAvailable) }
    var e2eeModeSelected by remember { mutableStateOf(e2eeInstalled) }
    val snackbarHostState = LocalSnackbarHostState.current
    val coroutineScope = rememberCoroutineScope()
    val httpsLockedHint = stringResource(R.string.matrix_e2ee_https_locked)

    SchemaSenderConfigForm(
        senderId = senderId,
        senderType = SenderType.MATRIX,
        channel = "Matrix",
        fields = MatrixVisibleFields,
        onBack = onBack,
        viewModel = viewModel,
        normalizeDraft = ::matrixVisibleDraft,
        extraContent = { draft, _ ->
            MatrixEncryptionModeSelector(
                e2eeSelected = e2eeModeSelected,
                httpsLocked = e2eeInstalled,
                onSelectE2ee = { selected ->
                    if (selected) {
                        e2eeModeSelected = true
                    } else if (e2eeInstalled) {
                        coroutineScope.launch {
                            snackbarHostState.showLatestSnackbar(httpsLockedHint)
                        }
                    } else {
                        e2eeModeSelected = false
                    }
                },
            )
            if (e2eeModeSelected) {
                MatrixE2eeStatusSection(
                    availability = availability,
                    onInstalled = {
                        e2eeInstalled = true
                        e2eeModeSelected = true
                    },
                    onUninstalled = {
                        e2eeInstalled = false
                        e2eeModeSelected = false
                    },
                )
                MatrixE2eeVerificationSection(
                    availability = availability,
                    draft = draft,
                )
            }
        },
    )
}

/**
 * Encryption mode picker for the Matrix form: plain HTTPS delivery or the
 * downloadable E2EE module. While the module is installed the HTTPS option is
 * dimmed and reports why it cannot be chosen instead of switching the mode.
 */
@Composable
private fun MatrixEncryptionModeSelector(
    e2eeSelected: Boolean,
    httpsLocked: Boolean,
    onSelectE2ee: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AppText(
            text = stringResource(R.string.matrix_e2ee_mode_label),
            role = AppTextRole.Body,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppFilterChip(
                label = stringResource(R.string.matrix_e2ee_mode_https),
                selected = !e2eeSelected,
                onClick = { onSelectE2ee(false) },
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (httpsLocked) LOCKED_OPTION_ALPHA else 1f),
            )
            AppFilterChip(
                label = stringResource(R.string.matrix_e2ee_mode_e2ee),
                selected = e2eeSelected,
                onClick = { onSelectE2ee(true) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Matches the disabled-opacity Material uses for controls that cannot be used. */
private const val LOCKED_OPTION_ALPHA = 0.38f

/**
 * Displays the appropriate E2EE status indicator based on module availability:
 * - AVAILABLE -> "E2EE Enabled" card with an uninstall action
 * - NOT_APPLICABLE -> info banner suggesting E2EE variant
 * - NOT_INSTALLED (Play DFM / GitHub plugin not downloaded yet) -> install button
 * - DOWNLOADING -> progress bar with percentage
 * - INSTALL_FAILED -> error message + retry button
 * - LOAD_FAILED -> error message
 * - UNINSTALLING -> indeterminate progress while the module files are removed
 *   (Play: the removal is deferred until the app is backgrounded)
 */
private fun matrixVisibleDraft(draft: SenderSettingDraft): SenderSettingDraft {
    var result = draft.keepOnlyFields(MatrixVisibleFields.map { it.name } + "accessToken")
    // Normalize room ID: extract from matrix.to share links
    val rawRoomId = result.string("roomId")
    val normalizedRoomId = extractMatrixRoomId(rawRoomId)
    if (normalizedRoomId != rawRoomId) {
        result = result.withString("roomId", normalizedRoomId)
    }
    return result
}

private fun extractMatrixRoomId(input: String): String {
    if (input.isBlank()) return input
    // Handle https://matrix.to/#/!roomId:server?via=server
    val matrixToRegex = Regex("""https://matrix\.to/#/(![^?\s]+)""")
    matrixToRegex.find(input)?.let { return it.groupValues[1] }
    // Already a valid room ID
    return input.trim()
}
