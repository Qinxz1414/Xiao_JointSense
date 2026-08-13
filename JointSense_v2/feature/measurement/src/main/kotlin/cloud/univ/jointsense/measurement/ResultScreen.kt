package cloud.univ.jointsense.measurement

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.univ.jointsense.designsystem.chart.AiScaleBar
import cloud.univ.jointsense.designsystem.component.GradeScale
import cloud.univ.jointsense.designsystem.component.ClinicalCard
import cloud.univ.jointsense.designsystem.component.GradeBadge
import cloud.univ.jointsense.designsystem.component.JointSenseBarAction
import cloud.univ.jointsense.designsystem.component.JointSenseTopBar
import cloud.univ.jointsense.designsystem.theme.factorColor
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.feature.measurement.R
import java.text.NumberFormat

/**
 * Analysis result screen — quantitative values for the three factors,
 * the composite OA Inflammation Index with its 0-1 scale, the 0-4
 * grade bar and retest / save actions (design: 分析结果).
 */
@Composable
fun ResultScreen(
    session: TestSession?,
    lastResult: TestResult?,
    canAddMore: Boolean,
    cleanupWarning: String? = null,
    onContinueMeasurement: () -> Unit,
    onReturnToOrigin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    val aiScaleFormat = remember(locale) {
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }
    }
    val results = session?.results ?: emptyList()
    val latestPerFactor = BaselineMeasurementMetrics.latestPerFactor(results)
    val ai = BaselineMeasurementMetrics.aiFromResults(results)
    val grade = ai?.let { BaselineMeasurementMetrics.grade(it) }

    Scaffold(
        topBar = {
            JointSenseTopBar(
                title = stringResource(R.string.measurement_title_result),
                navigationIcon = {
                    JointSenseBarAction(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.measurement_action_back),
                        onClick = onReturnToOrigin,
                    )
                },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            cleanupWarning?.let {
                Text(
                    text = stringResource(R.string.measurement_cleanup_warning),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(MEASUREMENT_CLEANUP_WARNING_TAG),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            // ---- Quantitative analysis card ----
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
                        text = stringResource(R.string.measurement_quantitative_analysis),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Factor legend
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InflammationFactor.entries.forEach { factor ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(factorColor(factor))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = factor.shortName,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                    // Value rows
                    InflammationFactor.entries.forEach { factor ->
                        val value = latestPerFactor[factor]
                        val isJustMeasured = lastResult?.factor == factor
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .then(
                                    if (isJustMeasured) {
                                        Modifier.background(
                                            factorColor(factor).copy(alpha = 0.08f)
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = factor.shortName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = value?.let { stringResource(R.string.measurement_value, it) }
                                    ?: stringResource(R.string.value_unavailable),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (value != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                    text = stringResource(R.string.factor_unit),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            thickness = 0.5.dp
                        )
                    }

                    // Well signal swatch (ELISA palette, Rule/SKILL.md)
                    lastResult?.let { result ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(
                                        BaselineMeasurementMetrics.wellColor(
                                            BaselineMeasurementMetrics.normalize(
                                                result.factor,
                                                result.concentration,
                                            )
                                        )
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = stringResource(
                                        R.string.measurement_well_signal,
                                        result.factor.shortName,
                                        result.concentration,
                                    ),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(
                                        R.string.measurement_rgb_features,
                                        result.features.rMean, result.features.rStd,
                                        result.features.gMean, result.features.gStd,
                                        result.features.bMean, result.features.bStd,
                                    ),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---- AI index card ----
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
                        text = stringResource(R.string.measurement_oa_index),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = ai?.let { stringResource(R.string.measurement_value, it) }
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
                                contentDescription = stringResource(R.string.measurement_oa_grade),
                                stateDescription = stringResource(
                                    R.string.measurement_oa_grade_description,
                                    grade,
                                    gradeLabel,
                                ),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    AiScaleBar(
                        value = ai,
                        formatValue = { aiScaleFormat.format(it.toDouble()) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---- Grade card ----
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
                        text = stringResource(R.string.measurement_oa_grade),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val labels = (0..4).map { stringResource(gradeResource(it)) }
                    GradeScale(
                        currentGrade = grade,
                        labels = labels,
                        contentDescription = stringResource(R.string.measurement_oa_grade_scale_description),
                        stateDescription = grade?.let {
                            stringResource(R.string.measurement_oa_grade_description, it, labels[it])
                        } ?: stringResource(R.string.measurement_grade_unavailable),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Actions ----
            if (canAddMore) {
                OutlinedButton(
                    onClick = onContinueMeasurement,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag(CONTINUE_MEASUREMENT_TAG),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.measurement_action_continue),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Button(
                onClick = onReturnToOrigin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.measurement_action_done),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.measurement_result_saved),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

const val MEASUREMENT_CLEANUP_WARNING_TAG = "measurement_cleanup_warning"

private fun gradeResource(grade: Int): Int = when (grade) {
    0 -> R.string.grade_0
    1 -> R.string.grade_1
    2 -> R.string.grade_2
    3 -> R.string.grade_3
    4 -> R.string.grade_4
    else -> error("Grade must be between 0 and 4")
}
