package cloud.univ.jointsense.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.univ.jointsense.designsystem.chart.ChartDataPoint
import cloud.univ.jointsense.designsystem.chart.ChartSeries
import cloud.univ.jointsense.designsystem.chart.LineChart
import cloud.univ.jointsense.designsystem.chart.MultiLineChart
import cloud.univ.jointsense.designsystem.chart.SeriesLegendSymbol
import cloud.univ.jointsense.designsystem.chart.TimePoint
import cloud.univ.jointsense.designsystem.chart.ChartLinePattern
import cloud.univ.jointsense.designsystem.chart.ChartMarkerShape
import cloud.univ.jointsense.designsystem.chart.chartSeriesStyle
import cloud.univ.jointsense.designsystem.component.ClinicalCard
import cloud.univ.jointsense.designsystem.component.JointSenseTopBar
import cloud.univ.jointsense.designsystem.theme.factorColor
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.inflammationFactorPresentationOrder
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
    modifier: Modifier = Modifier,
    nowMillis: () -> Long = System::currentTimeMillis,
) {
    // 0 = All
    var periodDays by rememberSaveable { mutableIntStateOf(7) }
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
                .testTag(TRENDS_SCREEN_TAG)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Period chips
            Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
                periods.chunked(2).forEach { rowPeriods ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        rowPeriods.forEach { days ->
                            val selected = periodDays == days
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                    .testTag(periodTag(days))
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { periodDays = days },
                                    )
                                    .heightIn(min = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (days == 0) {
                                        stringResource(R.string.insights_period_all)
                                    } else {
                                        stringResource(R.string.insights_period_days, days)
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val now = nowMillis()
            val since = if (periodDays == 0) {
                0L
            } else {
                now - DAY_MILLIS * periodDays
            }

            val factorSeries = inflammationFactorPresentationOrder.map { factor ->
                ChartSeries(
                    name = factor.shortName,
                    color = factorColor(factor),
                    points = state.factorSeries[factor].orEmpty()
                        .filter { it.time in since..now && it.value.isFinite() && it.value >= 0f }
                        .map { TimePoint(it.time, it.value) }
                )
            }
            val aiSeries = state.aiSeries.filter {
                it.time in since..now && it.value.isFinite() && it.value in 0f..1f
            }
            val events = state.keyEvents.filter { it.time in since..now && it.isPresentable() }
            val hasData = factorSeries.any { it.points.isNotEmpty() }
            val factorPoints = factorSeries.flatMap(ChartSeries::points)
            val latestAi = aiSeries.maxByOrNull(InsightPoint::time)?.value
            val currentGrade = latestAi?.let(BaselineInsightsMetrics::grade)
            val currentAiText = latestAi?.let(numberFormat::format)
                ?: stringResource(R.string.value_unavailable)
            val currentGradeText = currentGrade?.toString()
                ?: stringResource(R.string.value_unavailable)
            val currentGradeLabel = currentGrade?.let { stringResource(gradeResource(it)) }
                ?: stringResource(R.string.insights_grade_unavailable)
            val factorSummary = if (factorPoints.isEmpty()) {
                stringResource(R.string.insights_trends_empty)
            } else {
                val latest = factorSeries.mapNotNull { series ->
                    series.points.maxByOrNull(TimePoint::time)?.let { point ->
                        stringResource(
                            R.string.insights_factor_chart_latest,
                            series.name,
                            numberFormat.format(point.value),
                            chartTrendDirection(
                                series.points.sortedBy(TimePoint::time).map(TimePoint::value),
                            ).localizedLabel(),
                        )
                    }
                }.joinToString(", ")
                stringResource(
                    R.string.insights_factor_chart_summary,
                    factorSeries.filter { it.points.isNotEmpty() }.joinToString(", ", transform = ChartSeries::name),
                    chartDateFormat.format(Date(factorPoints.minOf(TimePoint::time))),
                    chartDateFormat.format(Date(factorPoints.maxOf(TimePoint::time))),
                    latest,
                    currentAiText,
                    currentGradeText,
                    currentGradeLabel,
                )
            }
            val aiSummary = if (aiSeries.isEmpty()) {
                stringResource(R.string.insights_trends_empty)
            } else {
                stringResource(
                    R.string.insights_ai_chart_summary,
                    chartDateFormat.format(Date(aiSeries.minOf(InsightPoint::time))),
                    chartDateFormat.format(Date(aiSeries.maxOf(InsightPoint::time))),
                    numberFormat.format(aiSeries.maxBy(InsightPoint::time).value),
                    chartTrendDirection(
                        aiSeries.sortedBy(InsightPoint::time).map(InsightPoint::value),
                    ).localizedLabel(),
                    currentGradeText,
                    currentGradeLabel,
                )
            }

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
                        Text(
                            text = stringResource(R.string.insights_series_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(
                            modifier = Modifier.testTag(TREND_SERIES_LABELS_TAG),
                        ) {
                            factorSeries.forEachIndexed { index, series ->
                                val styleDescription = chartStyleDescription(index, series.name)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag(legendTag(index))
                                        .clearAndSetSemantics { contentDescription = styleDescription }
                                        .padding(vertical = 4.dp),
                                ) {
                                    SeriesLegendSymbol(
                                        seriesIndex = index,
                                        color = series.color,
                                        modifier = Modifier
                                            .width(32.dp)
                                            .height(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = series.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.insights_concentration_axis),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag(TREND_UNIT_AXIS_LABEL_TAG),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .semantics { contentDescription = factorSummary }
                                .testTag(FACTOR_TREND_CHART_TAG)
                        ) {
                            MultiLineChart(
                                series = factorSeries,
                                modifier = Modifier.fillMaxSize(),
                                yAxisLabel = stringResource(R.string.insights_unit),
                                formatValue = numberFormat::format,
                                formatTime = { chartDateFormat.format(Date(it)) },
                            )
                        }
                        Text(
                            text = stringResource(R.string.insights_date_axis),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .testTag(TREND_DATE_AXIS_LABEL_TAG),
                        )
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
                                .semantics { contentDescription = aiSummary }
                                .testTag(OA_TREND_CHART_TAG)
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

const val FACTOR_TREND_CHART_TAG = "factor_trend_chart"
const val OA_TREND_CHART_TAG = "oa_trend_chart"
const val TREND_DATE_AXIS_LABEL_TAG = "trend_date_axis_label"
const val TREND_UNIT_AXIS_LABEL_TAG = "trend_unit_axis_label"
const val TREND_SERIES_LABELS_TAG = "trend_series_labels"
const val TRENDS_PERIOD_7_TAG = "trends_period_7"
const val TRENDS_PERIOD_30_TAG = "trends_period_30"
const val TRENDS_PERIOD_90_TAG = "trends_period_90"
const val TRENDS_PERIOD_ALL_TAG = "trends_period_all"
const val TRENDS_SCREEN_TAG = "screen_trends"
const val TREND_SERIES_TNF_ALPHA_LEGEND_TAG = "trend_legend_tnf_alpha"
const val TREND_SERIES_IL6_LEGEND_TAG = "trend_legend_il6"
const val TREND_SERIES_IL1_BETA_LEGEND_TAG = "trend_legend_il1_beta"

private fun periodTag(days: Int): String = when (days) {
    7 -> TRENDS_PERIOD_7_TAG
    30 -> TRENDS_PERIOD_30_TAG
    90 -> TRENDS_PERIOD_90_TAG
    else -> TRENDS_PERIOD_ALL_TAG
}

private fun legendTag(seriesIndex: Int): String = when (seriesIndex) {
    0 -> TREND_SERIES_TNF_ALPHA_LEGEND_TAG
    1 -> TREND_SERIES_IL6_LEGEND_TAG
    else -> TREND_SERIES_IL1_BETA_LEGEND_TAG
}

internal enum class ChartTrendDirection { RISING, FALLING, STABLE, INSUFFICIENT }

internal fun chartTrendDirection(values: List<Float>): ChartTrendDirection = when {
    values.size < 2 -> ChartTrendDirection.INSUFFICIENT
    values.last() > values.first() -> ChartTrendDirection.RISING
    values.last() < values.first() -> ChartTrendDirection.FALLING
    else -> ChartTrendDirection.STABLE
}

@Composable
internal fun ChartTrendDirection.localizedLabel(): String = stringResource(
    when (this) {
        ChartTrendDirection.RISING -> R.string.insights_chart_trend_rising
        ChartTrendDirection.FALLING -> R.string.insights_chart_trend_falling
        ChartTrendDirection.STABLE -> R.string.insights_chart_trend_stable
        ChartTrendDirection.INSUFFICIENT -> R.string.insights_chart_trend_insufficient
    },
)

@Composable
internal fun chartStyleDescription(seriesIndex: Int, seriesName: String): String {
    val style = chartSeriesStyle(seriesIndex)
    val line = chartLineStyleLabel(style.linePattern)
    val marker = chartMarkerStyleLabel(style.markerShape)
    return stringResource(R.string.insights_chart_legend_summary, seriesName, line, marker)
}

@Composable
internal fun chartLineStyleLabel(pattern: ChartLinePattern): String = stringResource(
    when (pattern) {
        ChartLinePattern.SOLID -> R.string.insights_chart_style_solid
        ChartLinePattern.DASHED -> R.string.insights_chart_style_dashed
        ChartLinePattern.DOTTED -> R.string.insights_chart_style_dotted
    },
)

@Composable
internal fun chartMarkerStyleLabel(shape: ChartMarkerShape): String = stringResource(
    when (shape) {
        ChartMarkerShape.CIRCLE -> R.string.insights_chart_marker_circle
        ChartMarkerShape.SQUARE -> R.string.insights_chart_marker_square
        ChartMarkerShape.TRIANGLE -> R.string.insights_chart_marker_triangle
    },
)

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

private fun KeyEventItem.isPresentable(): Boolean = when (kind) {
    EventKind.TEST -> measurementCount != null && aiValue?.let { it.isFinite() && it in 0f..1f } != false
    EventKind.UP,
    EventKind.DOWN,
    -> previousAi?.let { it.isFinite() && it in 0f..1f } == true &&
        currentAi?.let { it.isFinite() && it in 0f..1f } == true
}
