package io.github.magisk317.relay.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.model.SenderDispatchStat
import io.github.magisk317.relay.ui.common.SegmentedOption
import io.github.magisk317.relay.ui.common.SingleChoiceSegmentedSelector
import io.github.magisk317.relay.ui.sender.getSenderTypeName

internal data class HomeAnalyticsSnapshot(
    val totalMessages: Long,
    val codeDetected: Long,
    val autoInputAttempt: Long,
    val autoInputSuccess: Long,
    val autoInputFailed: Long,
    val forwardTotal: Long,
    val forwardSuccess: Long,
    val forwardFailed: Long,
    val senderStats: List<SenderDispatchStat>,
)

internal enum class HomeChartType(val id: String) {
    FORWARD("forward"),
    EVENTS("events"),
    SENDER("sender"),
    ;

    fun labelRes(): Int = when (this) {
        FORWARD -> R.string.home_chart_type_forward
        EVENTS -> R.string.home_chart_type_events
        SENDER -> R.string.home_chart_type_sender
    }

    companion object {
        fun fromId(id: String?): HomeChartType? = values().firstOrNull { it.id == id }
    }
}

internal enum class HomeChartWindow(val id: String, val days: Int?) {
    ALL("all", null),
    LAST_7_DAYS("7d", 7),
    LAST_30_DAYS("30d", 30),
    ;

    fun labelRes(): Int = when (this) {
        ALL -> R.string.home_chart_window_all
        LAST_7_DAYS -> R.string.home_chart_window_7d
        LAST_30_DAYS -> R.string.home_chart_window_30d
    }

    fun fromMs(nowMs: Long): Long {
        val limit = days ?: return 0L
        return nowMs - limit * 24L * 60L * 60L * 1000L
    }

    companion object {
        fun fromId(id: String?): HomeChartWindow? = values().firstOrNull { it.id == id }
    }
}

private data class PieSlice(
    val label: String,
    val value: Long,
    val color: Color,
)

@Composable
internal fun HomeChartCard(
    chartType: HomeChartType,
    chartWindow: HomeChartWindow,
    data: HomeAnalyticsSnapshot?,
    onChartTypeChange: (HomeChartType) -> Unit,
    onChartWindowChange: (HomeChartWindow) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        HomeChartBody(
            chartType = chartType,
            chartWindow = chartWindow,
            data = data,
            onChartTypeChange = onChartTypeChange,
            onChartWindowChange = onChartWindowChange,
            showTitle = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun HomeChartBody(
    chartType: HomeChartType,
    chartWindow: HomeChartWindow,
    data: HomeAnalyticsSnapshot?,
    onChartTypeChange: (HomeChartType) -> Unit,
    onChartWindowChange: (HomeChartWindow) -> Unit,
    showTitle: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showTitle) {
            Text(
                text = stringResource(id = R.string.home_card_chart_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        data?.let {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatLine(label = stringResource(id = R.string.home_event_total_messages), value = data.totalMessages)
                StatLine(label = stringResource(id = R.string.home_event_code_detected), value = data.codeDetected)
                StatLine(label = stringResource(id = R.string.home_event_auto_input_attempt), value = data.autoInputAttempt)
                StatLine(label = stringResource(id = R.string.home_event_auto_input_success), value = data.autoInputSuccess)
                StatLine(label = stringResource(id = R.string.home_event_auto_input_failed), value = data.autoInputFailed)
                StatLine(label = stringResource(id = R.string.home_event_forward_success), value = data.forwardSuccess)
                StatLine(label = stringResource(id = R.string.home_event_forward_failed), value = data.forwardFailed)
            }
        }

        SingleChoiceSegmentedSelector(
            options = HomeChartType.values().map { type ->
                SegmentedOption(type, stringResource(id = type.labelRes()))
            },
            selected = chartType,
            onSelect = onChartTypeChange,
        )

        SingleChoiceSegmentedSelector(
            options = HomeChartWindow.values().map { window ->
                SegmentedOption(window, stringResource(id = window.labelRes()))
            },
            selected = chartWindow,
            onSelect = onChartWindowChange,
        )

        PieChart(
            slices = when (chartType) {
                HomeChartType.FORWARD -> listOf(
                    PieSlice(
                        label = stringResource(id = R.string.home_event_forward_success),
                        value = data?.forwardSuccess ?: 0L,
                        color = colors[0],
                    ),
                    PieSlice(
                        label = stringResource(id = R.string.home_event_forward_failed),
                        value = data?.forwardFailed ?: 0L,
                        color = colors[3],
                    ),
                )
                HomeChartType.EVENTS -> listOf(
                    PieSlice(
                        label = stringResource(id = R.string.home_event_code_detected),
                        value = data?.codeDetected ?: 0L,
                        color = colors[0],
                    ),
                    PieSlice(
                        label = stringResource(id = R.string.home_event_auto_input_success),
                        value = data?.autoInputSuccess ?: 0L,
                        color = colors[1],
                    ),
                    PieSlice(
                        label = stringResource(id = R.string.home_event_auto_input_failed),
                        value = data?.autoInputFailed ?: 0L,
                        color = colors[3],
                    ),
                    PieSlice(
                        label = stringResource(id = R.string.home_event_forward_success),
                        value = data?.forwardSuccess ?: 0L,
                        color = colors[2],
                    ),
                    PieSlice(
                        label = stringResource(id = R.string.home_event_forward_failed),
                        value = data?.forwardFailed ?: 0L,
                        color = colors[4],
                    ),
                )
                HomeChartType.SENDER -> {
                    if (data == null) {
                        emptyList()
                    } else {
                        data.senderStats.mapIndexed { index, row ->
                            PieSlice(
                                label = getSenderTypeName(context, row.senderType),
                                value = row.sent,
                                color = colors[index % colors.size],
                            )
                        }
                    }
                }
            },
            emptyText = stringResource(id = R.string.home_chart_no_data),
        )
    }
}

@Composable
private fun PieChart(
    slices: List<PieSlice>,
    emptyText: String,
) {
    val total = slices.sumOf { it.value }
    if (total <= 0) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = emptyText, color = MaterialTheme.colorScheme.outline)
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(140.dp)) {
            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = (slice.value.toFloat() / total.toFloat()) * 360f
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                )
                startAngle += sweep
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEach { slice ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(slice.color, CircleShape),
                    )
                    Text(text = slice.label, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = slice.value.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value.toString(), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}
