package cloud.univ.jointsense.measurement

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.domain.model.inflammationFactorPresentationOrder
import cloud.univ.jointsense.feature.measurement.R
import java.text.NumberFormat

/**
 * Analysis result screen — quantitative values for the three factors,
 * the composite OA Inflammation Index with its 0-1 scale, the 0-4
 * grade bar and retest / save actions (design: 分析结果).
 */
@Composable
fun ResultScreen(
    resolution: ResultResolution,
    cleanupWarning: String? = null,
    onContinueMeasurement: () -> Unit,
    onReturnToOrigin: () -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (resolution) {
        ResultResolution.Loading -> ResultLoadingScreen(
            onBack = onReturnToOrigin,
            modifier = modifier,
        )
        ResultResolution.NotFound -> ResultNotFoundScreen(
            onBack = onReturnToOrigin,
            onGoHome = onGoHome,
            modifier = modifier,
        )
        is ResultResolution.Found -> ResultScreen(
            session = resolution.session,
            lastResult = resolution.result,
            canAddMore = resolution.canContinue,
            cleanupWarning = cleanupWarning,
            onContinueMeasurement = onContinueMeasurement,
            onReturnToOrigin = onReturnToOrigin,
            onGoHome = onGoHome,
            modifier = modifier,
        )
    }
}

@Composable
fun ResultScreen(
    session: TestSession?,
    lastResult: TestResult?,
    canAddMore: Boolean,
    cleanupWarning: String? = null,
    onContinueMeasurement: () -> Unit,
    onReturnToOrigin: () -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (lastResult == null) {
        ResultNotFoundScreen(
            onBack = onReturnToOrigin,
            onGoHome = onGoHome,
            modifier = modifier,
        )
        return
    }
    val locale = LocalConfiguration.current.locales[0]
    val aiScaleFormat = remember(locale) {
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }
    }
    val presentation = createResultUiModel(session, lastResult)
    val ai = presentation.oaIndex
    val grade = presentation.grade

    Scaffold(
        modifier = modifier.testTag(SCREEN_RESULT_TAG),
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
            modifier = Modifier
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
            ClinicalCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.measurement_latest_measurement),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = presentation.measuredFactor?.shortName
                            ?: stringResource(R.string.value_unavailable),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = presentation.concentration?.let {
                                aiScaleFormat.format(it.toDouble())
                            } ?: stringResource(R.string.value_unavailable),
                            modifier = Modifier.testTag(RESULT_CONCENTRATION_TAG),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.factor_unit),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.measurement_range_status,
                            presentation.rangeStatus?.let { stringResource(it.labelResource()) }
                                ?: stringResource(R.string.measurement_range_unknown),
                        ),
                        modifier = Modifier.testTag(RESULT_RANGE_STATUS_TAG),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    presentation.features?.let { features ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.measurement_rgb_features,
                                features.rMean,
                                features.rStd,
                                features.gMean,
                                features.gStd,
                                features.bMean,
                                features.bStd,
                            ),
                            modifier = Modifier.testTag(RESULT_FEATURES_SUMMARY_TAG),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(
                                R.string.measurement_tealness,
                                features.tealness,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                        inflammationFactorPresentationOrder.forEach { factor ->
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
                    inflammationFactorPresentationOrder.forEach { factor ->
                        val value = presentation.factorValues.first { it.factor == factor }.value
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
                    val measuredConcentration = presentation.concentration
                    val measuredFeatures = presentation.features
                    if (measuredConcentration != null && measuredFeatures != null) {
                        val result = lastResult
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
                                                measuredConcentration,
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
                                        measuredConcentration,
                                    ),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(
                                        R.string.measurement_rgb_features,
                                        measuredFeatures.rMean, measuredFeatures.rStd,
                                        measuredFeatures.gMean, measuredFeatures.gStd,
                                        measuredFeatures.bMean, measuredFeatures.bStd,
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
                        .heightIn(min = 50.dp)
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
                onClick = onGoHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag(RESULT_HOME_ACTION_TAG),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Home, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.measurement_action_home),
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

@Composable
private fun ResultLoadingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.testTag(SCREEN_RESULT_TAG),
        topBar = {
            JointSenseTopBar(
                title = stringResource(R.string.measurement_title_result),
                navigationIcon = {
                    JointSenseBarAction(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.measurement_action_back),
                        onClick = onBack,
                    )
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp)
                .testTag(RESULT_LOADING_TAG),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.measurement_result_loading_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.measurement_result_loading_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ResultNotFoundScreen(
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.testTag(SCREEN_RESULT_TAG),
        topBar = {
            JointSenseTopBar(
                title = stringResource(R.string.measurement_title_result),
                navigationIcon = {
                    JointSenseBarAction(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.measurement_action_back),
                        onClick = onBack,
                    )
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp)
                .testTag(RESULT_NOT_FOUND_TAG),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.measurement_result_not_found_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.measurement_result_not_found_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(RESULT_NOT_FOUND_BACK_TAG),
                ) {
                    Text(stringResource(R.string.measurement_action_back))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onGoHome,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(RESULT_NOT_FOUND_HOME_TAG),
                ) {
                    Icon(Icons.Default.Home, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.measurement_action_home))
                }
            }
        }
    }
}

const val MEASUREMENT_CLEANUP_WARNING_TAG = "measurement_cleanup_warning"
const val RESULT_CONCENTRATION_TAG = "result_concentration"
const val RESULT_RANGE_STATUS_TAG = "result_range_status"
const val RESULT_FEATURES_SUMMARY_TAG = "result_features_summary"
const val RESULT_HOME_ACTION_TAG = "result_home_action"
const val RESULT_NOT_FOUND_TAG = "result_not_found"
const val RESULT_NOT_FOUND_BACK_TAG = "result_not_found_back"
const val RESULT_NOT_FOUND_HOME_TAG = "result_not_found_home"
const val RESULT_LOADING_TAG = "result_loading"
const val SCREEN_RESULT_TAG = "screen_result"

private fun RangeStatus.labelResource(): Int = when (this) {
    RangeStatus.UNKNOWN -> R.string.measurement_range_unknown
    RangeStatus.BELOW_RANGE -> R.string.measurement_range_below
    RangeStatus.IN_RANGE -> R.string.measurement_range_within
    RangeStatus.ABOVE_RANGE -> R.string.measurement_range_above
}

private fun gradeResource(grade: Int): Int = when (grade) {
    0 -> R.string.grade_0
    1 -> R.string.grade_1
    2 -> R.string.grade_2
    3 -> R.string.grade_3
    4 -> R.string.grade_4
    else -> error("Grade must be between 0 and 4")
}
