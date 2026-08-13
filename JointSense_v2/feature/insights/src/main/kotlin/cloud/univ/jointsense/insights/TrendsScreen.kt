package cloud.univ.jointsense.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.univ.jointsense.designsystem.chart.ChartDataPoint
import cloud.univ.jointsense.designsystem.chart.ChartSeries
import cloud.univ.jointsense.designsystem.chart.LineChart
import cloud.univ.jointsense.designsystem.chart.MultiLineChart
import cloud.univ.jointsense.designsystem.chart.TimePoint
import cloud.univ.jointsense.designsystem.component.ClinicalCard
import cloud.univ.jointsense.designsystem.component.JointSenseTopBar
import cloud.univ.jointsense.designsystem.theme.factorColor
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.feature.insights.R
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/**
 * Trends screen — long-term trend and fluctuation monitoring:
 * period filter, per-factor multi-line chart, AI trend and key events.
 */
@Composable
fun TrendsScreen(
    state: TrendsUiState,
    modifier: Modifier = Modifier
) {
    // 0 = All
    var periodDays by remember { mutableIntStateOf(7) }
    val periods = listOf(7, 30, 90, 0)
    val locale = LocalConfiguration.current.locales[0]
    val numberFormat = remember(locale) {
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 2
        }
    }
    val chartDateFormat = remember(locale) {
        DateFormat.getDateInstance(DateFormat.SHORT, locale)
    }

    Scaffold(
        topBar = { JointSenseTopBar(title = stringResource(R.string.insights_trends_title)) }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Period chips
            Row(modifier = Modifier.fillMaxWidth()) {
                periods.forEach { days ->
                    val selected = periodDays == days
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .clickable { periodDays = days }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (days == 0) {
                                stringResource(R.string.insights_period_all)
                            } else {
                                stringResource(R.string.insights_period_days, days)
                            },
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val since = if (periodDays == 0) {
                0L
            } else {
                System.currentTimeMillis() - DAY_MILLIS * periodDays
            }

            val factorSeries = InflammationFactor.entries.map { factor ->
                ChartSeries(
                    name = factor.shortName,
                    color = factorColor(factor),
                    points = state.factorSeries[factor].orEmpty()
                        .filter { it.time >= since }
                        .map { TimePoint(it.time, it.value) }
                )
            }
            val aiSeries = state.aiSeries.filter { it.time >= since }
            val events = state.keyEvents.filter { it.time >= since }
            val hasData = factorSeries.any { it.points.isNotEmpty() }

            if (!hasData) {
                ClinicalCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.insights_trends_empty),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            } else {
                // Factor trends
                ClinicalCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.insights_factor_trends),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // Legend
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            factorSeries.forEach { series ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(series.color)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = series.name,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            MultiLineChart(
                                series = factorSeries,
                                modifier = Modifier.fillMaxSize(),
                                yAxisLabel = stringResource(R.string.insights_unit),
                                formatValue = numberFormat::format,
                                formatTime = { chartDateFormat.format(Date(it)) },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // AI trend
                ClinicalCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.insights_ai_trend),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val dayFormat = remember(locale) {
                            DateFormat.getDateInstance(DateFormat.SHORT, locale)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        ) {
                            LineChart(
                                dataPoints = aiSeries.map {
                                    ChartDataPoint(
                                        label = dayFormat.format(Date(it.time)),
                                        value = it.value
                                    )
                                },
                                lineColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxSize(),
                                yAxisLabel = stringResource(R.string.insights_ai_axis),
                                formatValue = numberFormat::format,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Key events
                ClinicalCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.insights_key_events),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (events.isEmpty()) {
                            Text(
                                text = stringResource(R.string.insights_no_events),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        val eventFormat = remember(locale) {
                            DateFormat.getDateInstance(DateFormat.SHORT, locale)
                        }
                        events.forEach { event ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val (icon, tint, bg) = when (event.kind) {
                                    EventKind.TEST -> Triple(
                                        Icons.Default.Science,
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primaryContainer
                                    )
                                    EventKind.DOWN -> Triple(
                                        Icons.Default.ArrowDownward,
                                        MaterialTheme.colorScheme.tertiary,
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                    EventKind.UP -> Triple(
                                        Icons.Default.ArrowUpward,
                                        MaterialTheme.colorScheme.error,
                                        MaterialTheme.colorScheme.errorContainer
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(bg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = tint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = event.localizedText(),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = eventFormat.format(Date(event.time)),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun KeyEventItem.localizedText(): String = when (kind) {
    EventKind.TEST -> pluralStringResource(
        R.plurals.insights_event_test,
        requireNotNull(measurementCount),
        measurementCount,
        aiValue?.let { stringResource(R.string.insights_event_ai_value, it) }.orEmpty(),
    )
    EventKind.UP -> stringResource(
        R.string.insights_event_ai_up,
        requireNotNull(previousAi),
        requireNotNull(currentAi),
    )
    EventKind.DOWN -> stringResource(
        R.string.insights_event_ai_down,
        requireNotNull(previousAi),
        requireNotNull(currentAi),
    )
}
