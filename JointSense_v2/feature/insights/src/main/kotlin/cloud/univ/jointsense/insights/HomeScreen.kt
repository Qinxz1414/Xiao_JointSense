package cloud.univ.jointsense.insights

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.univ.jointsense.core.designsystem.R as DesignSystemR
import cloud.univ.jointsense.designsystem.chart.ChartDataPoint
import cloud.univ.jointsense.designsystem.chart.LineChart
import cloud.univ.jointsense.designsystem.chart.Sparkline
import cloud.univ.jointsense.designsystem.component.ClinicalCard
import cloud.univ.jointsense.designsystem.component.GradeBadge
import cloud.univ.jointsense.designsystem.component.GradeScale
import cloud.univ.jointsense.designsystem.component.JointSenseTopBar
import cloud.univ.jointsense.designsystem.theme.factorColor
import cloud.univ.jointsense.feature.insights.R
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

@Composable
fun HomeScreen(
    state: HomeUiState,
    onTestNow: () -> Unit,
    onRestoreSamples: () -> Unit,
    onOpenReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = state.toHomePresentation()
    Scaffold(
        topBar = { JointSenseTopBar(title = stringResource(R.string.insights_home_title)) },
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (presentation.isEmpty) {
                EmptyHome(onTestNow = onTestNow, onRestoreSamples = onRestoreSamples)
            } else {
                DashboardContent(
                    state = state,
                    presentation = presentation,
                    onTestNow = onTestNow,
                    onOpenReport = onOpenReport,
                )
            }
        }
    }
}

@Composable
private fun EmptyHome(
    onTestNow: () -> Unit,
    onRestoreSamples: () -> Unit,
) {
    ClinicalCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = DesignSystemR.drawable.jointsense_logo),
                    contentDescription = stringResource(R.string.insights_logo_description),
                    modifier = Modifier.size(72.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.insights_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.insights_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            StartMeasurementButton(onTestNow)
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onRestoreSamples,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag(RESTORE_SAMPLES_TAG),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.insights_restore_samples))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.insights_restore_samples_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DashboardContent(
    state: HomeUiState,
    presentation: HomePresentation,
    onTestNow: () -> Unit,
    onOpenReport: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
    }
    val numberFormat = remember(locale) {
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 2
        }
    }

    ClinicalCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.insights_oa_index),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = presentation.oaIndex?.let(numberFormat::format)
                            ?: stringResource(R.string.value_unavailable),
                        modifier = Modifier.testTag(OA_INDEX_VALUE_TAG),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    presentation.grade?.let { grade ->
                        Spacer(modifier = Modifier.width(12.dp))
                        val gradeLabel = stringResource(gradeResource(grade))
                        GradeBadge(
                            grade = grade,
                            label = gradeLabel,
                            contentDescription = stringResource(R.string.insights_oa_grade),
                            stateDescription = stringResource(
                                R.string.insights_grade_description,
                                grade,
                                gradeLabel,
                            ),
                            modifier = Modifier.testTag(OA_GRADE_TAG),
                        )
                    }
                }
                presentation.latestTimestamp?.let { timestamp ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            R.string.insights_latest_observation_time,
                            dateFormat.format(Date(timestamp)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onOpenReport) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = stringResource(R.string.insights_open_report),
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    ClinicalCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.insights_oa_grade),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            val labels = (0..4).map { stringResource(gradeResource(it)) }
            GradeScale(
                currentGrade = presentation.grade,
                labels = labels,
                contentDescription = stringResource(R.string.insights_grade_scale_description),
                stateDescription = presentation.grade?.let {
                    stringResource(R.string.insights_grade_description, it, labels[it])
                } ?: stringResource(R.string.insights_grade_unavailable),
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presentation.factorValues.forEach { factorValue ->
            val factor = factorValue.factor
            val spark = state.factorSeries[factor].orEmpty()
                .sortedBy(InsightPoint::time)
                .takeLast(7)
                .map(InsightPoint::value)
            ClinicalCard(
                modifier = Modifier
                    .weight(1f)
                    .testTag(factorValueTag(factor)),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = factor.shortName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = factorValue.value?.let(numberFormat::format)
                            ?: stringResource(R.string.value_unavailable),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.insights_unit),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Sparkline(
                        values = spark,
                        color = factorColor(factor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp),
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    ClinicalCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RECENT_TREND_TAG),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.insights_recent_trend),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            val dayFormat = remember(locale) { DateFormat.getDateInstance(DateFormat.SHORT, locale) }
            LineChart(
                dataPoints = presentation.recentObservations.map {
                    ChartDataPoint(dayFormat.format(Date(it.time)), it.value)
                },
                lineColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                yAxisLabel = stringResource(R.string.insights_ai_axis),
                formatValue = numberFormat::format,
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    StartMeasurementButton(onTestNow)
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun StartMeasurementButton(onTestNow: () -> Unit) {
    Button(
        onClick = onTestNow,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag(START_MEASUREMENT_TAG),
        shape = RoundedCornerShape(16.dp),
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.insights_start_new_measurement),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun factorValueTag(factor: cloud.univ.jointsense.domain.model.InflammationFactor): String = when (factor) {
    cloud.univ.jointsense.domain.model.InflammationFactor.TNF_ALPHA -> FACTOR_VALUE_TNF_ALPHA_TAG
    cloud.univ.jointsense.domain.model.InflammationFactor.IL6 -> FACTOR_VALUE_IL6_TAG
    cloud.univ.jointsense.domain.model.InflammationFactor.IL1_BETA -> FACTOR_VALUE_IL1_BETA_TAG
}

const val OA_INDEX_VALUE_TAG = "oa_index_value"
const val OA_GRADE_TAG = "oa_grade"
const val FACTOR_VALUE_TNF_ALPHA_TAG = "factor_value_tnf_alpha"
const val FACTOR_VALUE_IL6_TAG = "factor_value_il6"
const val FACTOR_VALUE_IL1_BETA_TAG = "factor_value_il1_beta"
const val RECENT_TREND_TAG = "recent_trend"
const val START_MEASUREMENT_TAG = "start_measurement"
const val RESTORE_SAMPLES_TAG = "restore_samples"
