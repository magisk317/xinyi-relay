package io.github.magisk317.relay.ui.home.overview

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.magisk317.relay.feature.mode.ListenerRowState
import io.github.magisk317.relay.feature.mode.OverviewRows
import io.github.magisk317.relay.feature.mode.ShizukuRowState
import io.github.magisk317.relay.feature.mode.XposedRowState
import io.github.magisk317.relay.mobile.core.R
import io.github.magisk317.uikit.surface.OverviewInfoItem
import io.github.magisk317.uikit.surface.SummarySectionCard

@Composable
internal fun OverviewRuntimeRowsCard(rows: OverviewRows) {
    SummarySectionCard(modifier = Modifier.fillMaxWidth()) {
        OverviewInfoItem(
            icon = Icons.Default.Extension,
            label = stringResource(R.string.overview_row_xposed),
            value = stringResource(rows.xposed.labelRes()),
        )
        rows.listener?.let { state ->
            OverviewInfoItem(
                icon = Icons.Default.Link,
                label = stringResource(R.string.overview_row_listener),
                value = stringResource(state.labelRes()),
            )
        }
        rows.shizuku?.let { state ->
            OverviewInfoItem(
                icon = Icons.Default.Security,
                label = stringResource(R.string.overview_shizuku),
                value = stringResource(state.labelRes()),
            )
        }
    }
}

internal fun XposedRowState.labelRes(): Int = when (this) {
    XposedRowState.ACTIVE -> R.string.overview_runtime_active
    XposedRowState.INACTIVE -> R.string.status_not_active
}

internal fun ListenerRowState.labelRes(): Int = when (this) {
    ListenerRowState.CONNECTED -> R.string.overview_runtime_connected
    ListenerRowState.DISCONNECTED -> R.string.overview_runtime_disconnected
}

internal fun ShizukuRowState.labelRes(): Int = when (this) {
    ShizukuRowState.AUTHORIZED -> R.string.overview_runtime_authorized
    ShizukuRowState.UNAUTHORIZED -> R.string.overview_runtime_unauthorized
    ShizukuRowState.UNREACHABLE -> R.string.overview_runtime_disconnected
}
