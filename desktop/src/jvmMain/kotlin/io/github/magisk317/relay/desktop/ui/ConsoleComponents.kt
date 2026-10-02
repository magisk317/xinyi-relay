package io.github.magisk317.relay.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
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


/** WebUI hero card palette (frontend/webui/src/template.tsx) mirrored for the desktop port. */
private val HeroTop = Color(0xFFF8FCE8)
private val HeroBottom = Color(0xFFE8F4BC)
private val CardTop = Color(0xFFFFFFFF)
private val CardBottom = Color(0xFFF7FBE9)
private val HeroOutline = Color(0x297E992D)
private val CardOutline = Color(0x247E992D)
private val MetricInfo = Color(0xFFF2F8D5)
private val MetricSuccess = Color(0xFFEBF9C9)
private val MetricWarning = Color(0xFFFFF7DE)
private val MetricDefault = Color(0xFFF4F8E8)
private val MetricOnInfo = Color(0xFF6A861F)
private val MetricOnSuccess = Color(0xFF58711E)
private val MetricOnWarning = Color(0xFF9A6412)
private val MetricOnDefault = Color(0xFF4F5D31)
private val BadgeAccent = Color(0xFFD8F0A5)
private val BadgeSuccess = Color(0xFFDFF4B8)
private val BadgeWarning = Color(0xFFFFF0D9)
private val BadgeDanger = Color(0xFFFDE5D9)
private val BadgeMuted = Color(0xFFEEF4DF)
private val TextInk = Color(0xFF243115)
private val TextInkSoft = Color(0xFF596743)
private val TextMuted = Color(0xFF6C785D)
private val TextHint = Color(0xFF70805D)
private val ErrorInk = Color(0xFFB24A24)
private val ErrorSurface = Color(0xFFFFF8F3)
private val ErrorOutline = Color(0xFFF7C9BF)
private val DashedOutline = Color(0xFFD7E6A5)
private val RowOutline = Color(0xFFD9E6B1)

internal val ConsoleInk = TextInk
internal val ConsoleMuted = TextMuted
internal val ConsoleHint = TextHint

enum class RelayTone { ACCENT, MUTED, SUCCESS, WARNING, DANGER }

enum class ActionTone { PRIMARY, NEUTRAL, WARNING, DANGER }

enum class MetricTone { DEFAULT, SUCCESS, INFO, WARNING }

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
            shape = MaterialTheme.shapes.extraLarge,
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(1.dp, HeroOutline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .background(Brush.linearGradient(listOf(HeroTop, HeroBottom)))
                    .padding(horizontal = 28.dp, vertical = 24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.fillMaxWidth(0.72f)) {
                        if (!badge.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFFF4FBE0),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD7E6A6)),
                            ) {
                                Text(
                                    text = badge.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF58711E),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineLarge,
                            color = TextInk,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextInkSoft,
                            modifier = Modifier.padding(top = 10.dp),
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
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
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardOutline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(CardTop.copy(alpha = 0.94f), CardBottom)))
                .padding(horizontal = 22.dp, vertical = 22.dp),
        ) {
            if (!title.isNullOrBlank() || !subtitle.isNullOrBlank()) {
                Column(modifier = Modifier.padding(bottom = 18.dp)) {
                    if (!title.isNullOrBlank()) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextInk,
                        )
                    }
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            modifier = Modifier.padding(top = 8.dp),
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
        MetricTone.SUCCESS -> MetricSuccess to MetricOnSuccess
        MetricTone.INFO -> MetricInfo to MetricOnInfo
        MetricTone.WARNING -> MetricWarning to MetricOnWarning
        MetricTone.DEFAULT -> MetricDefault to MetricOnDefault
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = CardTop.copy(alpha = 0.94f),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardOutline),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(shape = RoundedCornerShape(50), color = pillBackground) {
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
                color = TextInk,
            )
            if (!helper.isNullOrBlank()) {
                Text(
                    text = helper,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
            }
        }
    }
}

@Composable
fun RelayBadge(text: String, tone: RelayTone = RelayTone.MUTED, modifier: Modifier = Modifier) {
    val background = when (tone) {
        RelayTone.ACCENT -> BadgeAccent
        RelayTone.SUCCESS -> BadgeSuccess
        RelayTone.WARNING -> BadgeWarning
        RelayTone.DANGER -> BadgeDanger
        RelayTone.MUTED -> BadgeMuted
    }
    val content = when (tone) {
        RelayTone.ACCENT -> Color(0xFF476018)
        RelayTone.SUCCESS -> Color(0xFF4B6517)
        RelayTone.WARNING -> Color(0xFF8A5518)
        RelayTone.DANGER -> Color(0xFF9F4E22)
        RelayTone.MUTED -> Color(0xFF566347)
    }
    Surface(shape = RoundedCornerShape(12.dp), color = background, modifier = modifier) {
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
        ActionTone.PRIMARY -> Brush.linearGradient(listOf(Color(0xFFBCE620), Color(0xFF99BF1F)))
        ActionTone.WARNING -> Brush.linearGradient(listOf(Color(0xFFFFF8EA), Color(0xFFFFF8EA)))
        ActionTone.DANGER -> Brush.linearGradient(listOf(Color(0xFFFFFAF0), Color(0xFFFFFAF0)))
        ActionTone.NEUTRAL -> Brush.linearGradient(listOf(Color(0xFFF8FBE9), Color(0xFFF8FBE9)))
    }
    val contentColor = when (tone) {
        ActionTone.PRIMARY -> Color(0xFF263215)
        ActionTone.WARNING -> Color(0xFF8F5D12)
        ActionTone.DANGER -> Color(0xFF8A5318)
        ActionTone.NEUTRAL -> Color(0xFF34461B)
    }
    val borderColor = when (tone) {
        ActionTone.PRIMARY -> Color.Transparent
        ActionTone.WARNING -> Color(0xFFE8C873)
        ActionTone.DANGER -> Color(0xFFE6C36F)
        ActionTone.NEUTRAL -> Color(0xFFD2E09D)
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        border = if (borderColor == Color.Transparent) null else androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(
            modifier = Modifier.background(background),
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
                    .size(48.dp)
                    .background(Color(0xFFEEF8C8), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF6A861F),
                )
            }
            Column {
                Text(
                    text = title ?: DesktopMessages.t(locale, "common.loading"),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextInk,
                )
                Text(
                    text = message ?: DesktopMessages.t(locale, "common.loadingMessage"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
fun EmptyCard(title: String, message: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = CardTop.copy(alpha = 0.84f),
        border = androidx.compose.foundation.BorderStroke(1.dp, DashedOutline),
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
                color = Color(0xFF34461B),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
        }
    }
}

@Composable
fun ErrorBanner(message: String, locale: DesktopLocale) {
    if (message.isBlank()) return
    Surface(
        shape = MaterialTheme.shapes.large,
        color = ErrorSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, ErrorOutline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = DesktopMessages.t(locale, "common.requestFailed"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = ErrorInk,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = ErrorInk,
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
            checkedThumbColor = Color.White,
            checkedTrackColor = Color(0xFF8CB01D),
            checkedBorderColor = Color(0xFF91B725),
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFFDFE8C4),
            uncheckedBorderColor = Color(0xFFCBD8A4),
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
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, RowOutline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(CardTop, CardBottom)))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.fillMaxWidth(0.8f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextInk,
                )
                if (!hint.isNullOrBlank()) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextHint,
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
            shape = RoundedCornerShape(24.dp),
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x5788A640)),
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
        ) {
            Row(
                modifier = Modifier
                    .background(Brush.linearGradient(listOf(CardTop, Color(0xFFF7FBE8))))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = selected?.label.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextInk,
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF6F8E18),
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
                                color = if (active) Color(0xFF1F2A10) else Color(0xFF42512A),
                            )
                            if (!option.description.isNullOrBlank()) {
                                Text(
                                    text = option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (active) Color(0xFF304016) else TextHint,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onValueChange(option.value)
                    },
                    modifier = if (active) {
                        Modifier.background(
                            Brush.linearGradient(listOf(Color(0xFF87AD1E), Color(0xFF6F8E18))),
                        )
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

/** Responsive metric grid: lays the cards out in rows of [minColumnWidth] columns. */
@Composable
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
