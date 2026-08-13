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
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.univ.jointsense.core.designsystem.R as DesignSystemR
import cloud.univ.jointsense.designsystem.chart.ChartDataPoint
import cloud.univ.jointsense.designsystem.component.GradeScale
import cloud.univ.jointsense.designsystem.chart.LineChart
import cloud.univ.jointsense.designsystem.chart.Sparkline
import cloud.univ.jointsense.designsystem.component.ClinicalCard
import cloud.univ.jointsense.designsystem.component.GradeBadge
import cloud.univ.jointsense.designsystem.component.JointSenseTopBar
import cloud.univ.jointsense.designsystem.theme.factorColor
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.feature.insights.R
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/**
 * Home dashboard — OA inflammation overview per the design mockup:
 * factor cards with sparklines, composite AI, grade bar, 7-day trend
 * and a last-test / test-now footer.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onTestNow: () -> Unit,
    onOpenReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = { JointSenseTopBar(title = stringResource(R.string.insights_home_title)) }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            val allResults = state.allResults

            if (allResults.isEmpty()) {
                EmptyHome(onTestNow = onTestNow)
            } else {
                DashboardContent(
                    state = state,
                    onTestNow = onTestNow,
                    onOpenReport = onOpenReport
                )
            }
        }
    }
}

@Composable
private fun EmptyHome(onTestNow: () -> Unit) {
    ClinicalCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = DesignSystemR.drawable.jointsense_logo),
                    contentDescription = stringResource(R.string.insights_logo_description),
                    modifier = Modifier.size(72.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.insights_empty_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.insights_empty_message),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onTestNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.insights_test_now), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DashboardContent(
    state: HomeUiState,
    onTestNow: () -> Unit,
    onOpenReport: () -> Unit
) {
    val latest = state.latestValues
    val ai = state.currentAi
    val grade = state.currentGrade
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
    val lastTest = state.allResults.maxOf { it.timestamp }

    // Factor cards
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InflammationFactor.entries.forEach { factor ->
            val value = latest[factor]
            val spark = state.factorSeries[factor].orEmpty().takeLast(7).map { it.value }
            ClinicalCard(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                    Text(
                        text = factor.shortName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = value?.let { stringResource(R.string.insights_value, it) }
                                ?: stringResource(R.string.value_unavailable),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = stringResource(R.string.insights_unit),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Sparkline(
                        values = spark,
                        color = factorColor(factor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // AI composite card
    ClinicalCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.insights_oa_index),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ai?.let { stringResource(R.string.insights_value, it) }
                            ?: stringResource(R.string.value_unavailable),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (grade != null) {
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
                        )
                    }
                }
            }
            IconButton(onClick = onOpenReport) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = stringResource(R.string.insights_open_report),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Grade card
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
                text = stringResource(R.string.insights_oa_grade),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            val labels = (0..4).map { stringResource(gradeResource(it)) }
            val gradeStateDescription = grade?.let {
                stringResource(R.string.insights_grade_description, it, labels[it])
            } ?: stringResource(R.string.insights_grade_unavailable)
            GradeScale(
                currentGrade = grade,
                labels = labels,
                contentDescription = stringResource(R.string.insights_grade_scale_description),
                stateDescription = gradeStateDescription,
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Recent 7-day AI trend
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
                text = stringResource(R.string.insights_recent_trend),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            val weekSeries = state.aiSeries.filter {
                it.time >= System.currentTimeMillis() - DAY_MILLIS * 7
            }.ifEmpty { state.aiSeries.takeLast(7) }
            val dayFormat = remember(locale) { DateFormat.getDateInstance(DateFormat.SHORT, locale) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                LineChart(
                    dataPoints = weekSeries.map {
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

    // Last test + test now footer
    ClinicalCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.insights_last_test),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dateFormat.format(Date(lastTest)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Button(
                onClick = onTestNow,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.insights_test_now), fontWeight = FontWeight.SemiBold)
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
}
