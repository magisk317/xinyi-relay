package io.github.magisk317.relay.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.desktop.i18n.DesktopMessages
import io.github.magisk317.relay.desktop.i18n.DesktopLocale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size


/** Shared surfaces use Material color roles so the appearance choice applies everywhere. */
internal val ConsoleInk: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface
internal val ConsoleMuted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
internal val ConsoleHint: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

enum class RelayTone { ACCENT, MUTED, SUCCESS, WARNING, DANGER }

enum class ActionTone { PRIMARY, NEUTRAL, WARNING, DANGER }

enum class MetricTone { DEFAULT, SUCCESS, INFO, WARNING }

/** Tones of the clickable facet pills the records page uses as filters. */
enum class FacetTone { MUTED, ACCENT, WARNING, SUCCESS }

/** Toggleable filter pill; selected pills get the ring the webUI draws for them. */
@Composable
fun FacetBadge(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    tone: FacetTone = FacetTone.MUTED,
    enabled: Boolean = true,
) {
    val background = when (tone) {
        FacetTone.ACCENT -> MaterialTheme.colorScheme.primaryContainer
        FacetTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
        FacetTone.SUCCESS -> MaterialTheme.colorScheme.secondaryContainer
        FacetTone.MUTED -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (tone) {
        FacetTone.ACCENT -> MaterialTheme.colorScheme.onPrimaryContainer
        FacetTone.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
        FacetTone.SUCCESS -> MaterialTheme.colorScheme.onSecondaryContainer
        FacetTone.MUTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = background,
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun PageShell(
    title: String,
    description: String,
    badge: String? = null,
    locale: DesktopLocale,
    actions: @Composable (() -> Unit)? = null,
    content: ColumnScopeContent,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (!badge.isNullOrBlank()) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = if (badge.isNullOrBlank()) 0.dp else 5.dp),
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                    if (actions != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp, top = 6.dp),
                        ) {
                            actions()
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            content()
        }
    }
}

/** Content slot for [PageShell]; kept separate so pages can emit any number of blocks. */
typealias ColumnScopeContent = @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            if (!title.isNullOrBlank() || !subtitle.isNullOrBlank()) {
                Column(modifier = Modifier.padding(bottom = 18.dp)) {
                    if (!title.isNullOrBlank()) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    tone: MetricTone = MetricTone.DEFAULT,
    helper: String? = null,
    modifier: Modifier = Modifier,
) {
    val (pillBackground, pillContent) = when (tone) {
        MetricTone.SUCCESS -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        MetricTone.INFO -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        MetricTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        MetricTone.DEFAULT -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(shape = RoundedCornerShape(4.dp), color = pillBackground) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = pillContent,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!helper.isNullOrBlank()) {
                Text(
                    text = helper,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun RelayBadge(text: String, tone: RelayTone = RelayTone.MUTED, modifier: Modifier = Modifier) {
    val background = when (tone) {
        RelayTone.ACCENT -> MaterialTheme.colorScheme.primaryContainer
        RelayTone.SUCCESS -> MaterialTheme.colorScheme.secondaryContainer
        RelayTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
        RelayTone.DANGER -> MaterialTheme.colorScheme.errorContainer
        RelayTone.MUTED -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (tone) {
        RelayTone.ACCENT -> MaterialTheme.colorScheme.onPrimaryContainer
        RelayTone.SUCCESS -> MaterialTheme.colorScheme.onSecondaryContainer
        RelayTone.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
        RelayTone.DANGER -> MaterialTheme.colorScheme.onErrorContainer
        RelayTone.MUTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(4.dp), color = background, modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ActionTone = ActionTone.NEUTRAL,
    enabled: Boolean = true,
) {
    val background = when (tone) {
        ActionTone.PRIMARY -> MaterialTheme.colorScheme.primary
        ActionTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
        ActionTone.DANGER -> MaterialTheme.colorScheme.errorContainer
        ActionTone.NEUTRAL -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when (tone) {
        ActionTone.PRIMARY -> MaterialTheme.colorScheme.onPrimary
        ActionTone.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
        ActionTone.DANGER -> MaterialTheme.colorScheme.onErrorContainer
        ActionTone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor = when (tone) {
        ActionTone.PRIMARY -> MaterialTheme.colorScheme.primary
        ActionTone.WARNING -> MaterialTheme.colorScheme.tertiary
        ActionTone.DANGER -> MaterialTheme.colorScheme.error
        ActionTone.NEUTRAL -> MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = background,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(
            modifier = Modifier,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) contentColor else contentColor.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
fun LoadingCard(title: String? = null, message: String? = null, locale: DesktopLocale) {
    SurfaceCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column {
                Text(
                    text = title ?: DesktopMessages.t(locale, "common.loading"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = message ?: DesktopMessages.t(locale, "common.loadingMessage"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
fun EmptyCard(title: String, message: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ErrorBanner(message: String, locale: DesktopLocale) {
    if (message.isBlank()) return
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = DesktopMessages.t(locale, "common.requestFailed"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
fun RelaySwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.surface,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    hint: String? = null,
    enabled: Boolean = true,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.fillMaxWidth(0.8f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!hint.isNullOrBlank()) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            RelaySwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

/** Minimal dropdown mirroring RelaySelect: label + description per option. */
data class RelayOption(val value: String, val label: String, val description: String? = null)

@Composable
fun RelaySelect(
    value: String,
    options: List<RelayOption>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.value == value } ?: options.firstOrNull()
    Box(modifier = modifier) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = selected?.label.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                val active = option.value == value
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            )
                            if (!option.description.isNullOrBlank()) {
                                Text(
                                    text = option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onValueChange(option.value)
                    },
                    modifier = if (active) {
                        Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

/** Shared "live"/reconnect pill shown by the overview and analytics pages. */
@Composable
fun LiveBadge(connected: Boolean, locale: DesktopLocale) {
    RelayBadge(
        text = DesktopMessages.t(locale, if (connected) "common.liveConnected" else "common.liveReconnecting"),
        tone = if (connected) RelayTone.SUCCESS else RelayTone.WARNING,
    )
}

/** Inline informational notice with the webUI's light-green surface and ink. */
@Composable
fun NoticeCard(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}

/**
 * Scrollable outlet for a ported page; the webUI scrolls the whole content
 * column, so the desktop shell mirrors that instead of scrolling per card.
 */
@Composable
fun PageOutlet(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(end = 4.dp, bottom = 12.dp),
            content = content,
        )
    }
}

/** Confirm helper mirroring the webUI's window.confirm before a destructive action. */
@Composable
fun ConfirmDialog(
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = message) },
        text = null,
        confirmButton = {
            ActionButton(text = confirmLabel, onClick = onConfirm, tone = ActionTone.DANGER)
        },
        dismissButton = {
            ActionButton(text = dismissLabel, onClick = onDismiss, tone = ActionTone.NEUTRAL)
        },
    )
}

/** Responsive metric grid: lays the cards out in rows of [minColumnWidth] columns. */@Composable
fun MetricRow(
    cards: List<@Composable () -> Unit>,
    minColumnWidth: androidx.compose.ui.unit.Dp = 220.dp,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = (maxWidth / minColumnWidth).toInt().coerceIn(1, maxOf(1, cards.size))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            cards.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    row.forEach { card ->
                        Box(modifier = Modifier.weight(1f)) { card() }
                    }
                }
            }
        }
    }
}
