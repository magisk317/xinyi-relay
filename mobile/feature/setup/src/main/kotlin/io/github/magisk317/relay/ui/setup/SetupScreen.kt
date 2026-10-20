package io.github.magisk317.relay.ui.setup

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.mobile.core.R
import io.github.magisk317.relay.ui.common.PrivacyPolicyDialog
import io.github.magisk317.relay.ui.common.PrivacyPolicyPage
import io.github.magisk317.relay.ui.permission.StartupPermissionCollector
import io.github.magisk317.uikit.surface.AppCard
import io.github.magisk317.uikit.surface.AppCircularProgressIndicator
import io.github.magisk317.uikit.surface.AppPrimaryButton
import io.github.magisk317.uikit.surface.AppScaffold
import io.github.magisk317.uikit.surface.AppSecondaryButton
import io.github.magisk317.uikit.surface.AppSurface
import io.github.magisk317.uikit.surface.SectionHeading
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor
import org.koin.compose.viewmodel.koinViewModel

/**
 * Setup wizard: privacy agreement, optional root probe, one-tap root grant,
 * then whatever root cannot cover.
 *
 * The first screen is the original privacy dialog: accepting jumps straight to
 * the permission steps, declining finishes the activity, and viewing details
 * opens the full policy before any decision is made.
 */
@Composable
fun SetupScreen(
    onDecline: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetupViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPolicyPage by remember { mutableStateOf(false) }
    // The welcome step is the only exit: back there is a decline, which the
    // wizard refuses to guess, so the gesture is swallowed instead.
    BackHandler(enabled = state.step != SetupStep.WELCOME && !showPolicyPage) { viewModel.backStep() }

    Box(modifier = modifier) {
        AppScaffold(
            bottomBar = {
                // The privacy dialog owns the first screen, so the action bar
                // only appears once a decision has been made.
                if (state.step != SetupStep.WELCOME) {
                    SetupActions(
                        state = state,
                        onNext = viewModel::proceedToGrants,
                        onSkipRoot = viewModel::skipRoot,
                        onGrantRoot = viewModel::grantViaRoot,
                        onFinish = viewModel::finishManualGrants,
                        onEnterApp = onFinished,
                    )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (state.step) {
                    SetupStep.WELCOME -> Unit
                    SetupStep.ROOT -> RootStep(state)
                    SetupStep.GRANT_ROOT -> GrantRootStep(state)
                    SetupStep.GRANT_MANUAL -> GrantManualStep(
                        state = state,
                        onRefreshMissing = viewModel::refreshMissing,
                    )

                    SetupStep.DONE -> DoneStep(state)
                }
            }
        }
        if (state.step == SetupStep.WELCOME && !showPolicyPage) {
            PrivacyPolicyDialog(
                onDismiss = {},
                onConfirm = viewModel::acceptPrivacyPolicy,
                onCancel = { viewModel.declinePrivacyPolicy(onDeclined = onDecline) },
                onViewPolicy = { showPolicyPage = true },
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            )
        }
        if (showPolicyPage) {
            PrivacyPolicyPage(onDismiss = { showPolicyPage = false })
        }
    }
}

@Composable
private fun SetupActions(
    state: SetupUiState,
    onNext: () -> Unit,
    onSkipRoot: () -> Unit,
    onGrantRoot: () -> Unit,
    onFinish: () -> Unit,
    onEnterApp: () -> Unit,
) {
    AppSurface(color = appColor(AppColorRole.SurfaceContainer)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state.step) {
                SetupStep.WELCOME -> Unit

                SetupStep.ROOT -> {
                    AppSecondaryButton(
                        text = stringResource(R.string.setup_root_skip),
                        onClick = onSkipRoot,
                        enabled = !state.rootProbing,
                        modifier = Modifier.weight(1f),
                    )
                    AppPrimaryButton(
                        text = stringResource(R.string.setup_next),
                        onClick = onNext,
                        enabled = !state.rootProbing,
                        modifier = Modifier.weight(1f),
                    )
                }

                SetupStep.GRANT_ROOT -> AppPrimaryButton(
                    text = if (state.granting) {
                        stringResource(R.string.setup_one_tap_granting)
                    } else {
                        stringResource(R.string.setup_one_tap_grant)
                    },
                    onClick = onGrantRoot,
                    enabled = !state.granting,
                    modifier = Modifier.fillMaxWidth(),
                )

                SetupStep.GRANT_MANUAL -> AppPrimaryButton(
                    text = stringResource(R.string.setup_finish),
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth(),
                )

                SetupStep.DONE -> AppPrimaryButton(
                    text = stringResource(R.string.setup_enter_app),
                    onClick = onEnterApp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun RootStep(state: SetupUiState) {
    if (state.rootProbing) {
        AppCard {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppCircularProgressIndicator(modifier = Modifier.size(24.dp))
                AppText(
                    text = stringResource(R.string.setup_root_probing),
                    role = AppTextRole.Body,
                )
            }
        }
        return
    }

    AppCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppText(
                text = stringResource(
                    if (state.rootAvailable) {
                        R.string.setup_root_available_title
                    } else {
                        R.string.setup_root_unavailable_title
                    },
                ),
                role = AppTextRole.Subtitle,
            )
            AppText(
                text = stringResource(
                    if (state.rootAvailable) {
                        R.string.setup_root_available_summary
                    } else {
                        R.string.setup_root_unavailable_summary
                    },
                ),
                role = AppTextRole.BodySmall,
            )
        }
    }
}

@Composable
private fun GrantRootStep(state: SetupUiState) {
    AppText(text = stringResource(R.string.setup_grant_root_title), role = AppTextRole.Title)
    AppText(text = stringResource(R.string.setup_grant_root_summary), role = AppTextRole.BodySmall)

    if (state.granting) {
        AppCard {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppCircularProgressIndicator(modifier = Modifier.size(24.dp))
                AppText(
                    text = stringResource(R.string.setup_one_tap_granting),
                    role = AppTextRole.Body,
                )
            }
        }
    }

    if (state.grantLog.isNotEmpty()) {
        AppCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.grantLog.forEach { line ->
                    AppText(text = line, role = AppTextRole.Footnote)
                }
            }
        }
    }
}

@Composable
private fun GrantManualStep(
    state: SetupUiState,
    onRefreshMissing: () -> Unit,
) {
    val context = LocalContext.current
    val runtimeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // The dialog result says what was granted, but a re-read is the only way
        // to know the real state (one "don't ask again" changes everything).
        onRefreshMissing()
    }
    val specialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        onRefreshMissing()
    }

    AppText(text = stringResource(R.string.setup_manual_grant_title), role = AppTextRole.Title)

    if (!state.rootAvailable) {
        AppCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppText(
                    text = stringResource(R.string.setup_root_unavailable_title),
                    role = AppTextRole.Subtitle,
                )
                AppText(
                    text = stringResource(R.string.setup_root_unavailable_summary),
                    role = AppTextRole.BodySmall,
                )
            }
        }
    }

    SectionHeading(
        title = stringResource(R.string.setup_missing_title, state.missing.size),
    )

    val runtimeMissing = state.missingRuntime
    if (runtimeMissing.isNotEmpty()) {
        AppPrimaryButton(
            text = stringResource(R.string.setup_manual_grant),
            onClick = { runtimeLauncher.launch(runtimeMissing.map { it.key }.toTypedArray()) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    state.missingSpecial.forEach { requirement ->
        AppCard(
            onClick = {
                val intent = StartupPermissionCollector.buildSystemIntent(context, requirement)
                if (intent != null) {
                    runCatching { specialLauncher.launch(intent) }
                }
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(text = requirement.label, role = AppTextRole.Body)
                AppText(
                    text = stringResource(R.string.setup_grant_item_open),
                    role = AppTextRole.Footnote,
                )
            }
        }
    }
}

@Composable
private fun DoneStep(state: SetupUiState) {
    AppText(text = stringResource(R.string.setup_done_title), role = AppTextRole.Title)
    if (state.permissionStatuses.isNotEmpty() || state.missing.isNotEmpty()) {
        AppText(
            text = stringResource(R.string.setup_done_granted, state.grantedCount),
            role = AppTextRole.Body,
        )
    } else {
        AppText(
            text = stringResource(R.string.setup_done_all_ready),
            role = AppTextRole.Body,
        )
    }
    if (state.missing.isNotEmpty()) {
        AppText(
            text = stringResource(R.string.setup_done_missing, state.missing.size),
            role = AppTextRole.BodySmall,
        )
    }
    if (state.permissionStatuses.isNotEmpty()) {
        SectionHeading(
            title = stringResource(R.string.setup_done_list_title),
        )
        state.permissionStatuses.forEach { status ->
            AppCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        text = status.requirement.label,
                        role = AppTextRole.Body,
                        modifier = Modifier.weight(1f),
                    )
                    AppText(
                        text = stringResource(
                            if (status.granted) {
                                R.string.setup_done_item_granted
                            } else {
                                R.string.setup_done_item_missing
                            },
                        ),
                        role = AppTextRole.Footnote,
                        color = appColor(
                            if (status.granted) AppColorRole.Primary else AppColorRole.Error,
                        ),
                    )
                }
            }
        }
    }
}
