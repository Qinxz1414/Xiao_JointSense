package cloud.univ.jointsense.insights

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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.univ.jointsense.designsystem.component.ClinicalCard
import cloud.univ.jointsense.designsystem.component.JointSenseBarAction
import cloud.univ.jointsense.designsystem.component.JointSenseTopBar
import cloud.univ.jointsense.designsystem.theme.GradeColors
import cloud.univ.jointsense.designsystem.theme.factorColor
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.feature.insights.R
import cloud.univ.jointsense.insights.report.LocalizedReportFormatter
import cloud.univ.jointsense.insights.report.PdfExportResult
import cloud.univ.jointsense.insights.report.PdfReportExporter
import cloud.univ.jointsense.insights.report.ReportActionModelFactory
import cloud.univ.jointsense.insights.report.ReportError
import cloud.univ.jointsense.insights.report.ReportShareResult
import cloud.univ.jointsense.insights.report.ReportSharing
import java.io.File
import java.text.NumberFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Research report preview with measured values, evidence-based weekly trend,
 * observations, and export actions. The fixed disclaimer is export-only.
 */
@Composable
fun ReportScreen(
    state: ReportUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val presentation = state.toReportPresentation()
    val ai = presentation.oaIndex
    val grade = presentation.grade
    val locale = context.resources.configuration.locales[0]
    val formatter = remember(context.resources, locale) {
        LocalizedReportFormatter.from(context.resources, locale)
    }
    val numberFormat = remember(locale) {
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 2
        }
    }
    val reportModelFactory = remember(state) {
        ReportActionModelFactory(stateProvider = { state }, clock = System::currentTimeMillis)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }

    fun showError(error: ReportError) {
        scope.launch {
            snackbarHostState.showSnackbar(context.getString(error.messageResource()))
        }
    }

    fun shareTextReport() {
        val exportReport = formatter.formatExport(reportModelFactory.create())
        when (
            val result = ReportSharing.shareText(
                context,
                exportReport.plainText,
                context.getString(R.string.report_share_text),
            )
        ) {
            is ReportShareResult.Failure -> showError(result.error)
            ReportShareResult.Started -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            JointSenseTopBar(
                title = stringResource(R.string.insights_report_screen_title),
                actions = {
                    JointSenseBarAction(
                        icon = Icons.Default.Share,
                        contentDescription = stringResource(R.string.insights_share_summary),
                        onClick = ::shareTextReport,
                    )
                }
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
            if (ai == null || grade == null) {
                ClinicalCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.insights_report_empty),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            } else {
                val gradeColor = GradeColors[grade]

                // ---- Assessment card ----
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
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(gradeColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = gradeColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.insights_cartilage_assessment),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(activityResource(grade)),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(
                                    R.string.insights_ai_grade_summary,
                                    ai,
                                    stringResource(gradeResource(grade)),
                                    grade,
                                ),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Absolute factor summary ----
                ClinicalCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(REPORT_FACTOR_SUMMARY_TAG),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.insights_factor_summary),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        InflammationFactor.entries.forEach { factor ->
                            val delta = state.factorDeltaPct7d[factor]
                            val absolute = presentation.factorValues
                                .first { it.factor == factor }
                                .value
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(factorColor(factor))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = factor.shortName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = absolute?.let {
                                        stringResource(
                                            R.string.insights_concentration_value,
                                            numberFormat.format(it),
                                        )
                                    } ?: stringResource(R.string.value_unavailable),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                if (delta != null) {
                                    Icon(
                                        imageVector = if (delta >= 0) {
                                            Icons.Default.ArrowUpward
                                        } else {
                                            Icons.Default.ArrowDownward
                                        },
                                        contentDescription = null,
                                        tint = if (delta >= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(
                                            if (delta >= 0) R.string.insights_change_up else R.string.insights_change_down,
                                            kotlin.math.abs(delta),
                                        ),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (delta >= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.insights_no_comparison),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Evidence-based trend interpretation ----
                ClinicalCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(REPORT_TREND_INTERPRETATION_TAG),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = stringResource(R.string.insights_trend_interpretation),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (presentation.trend == TrendInterpretation.INSUFFICIENT_DATA) {
                                stringResource(trendInterpretationResource(presentation.trend))
                            } else {
                                stringResource(
                                    trendInterpretationResource(presentation.trend),
                                    STABLE_TREND_THRESHOLD_PERCENT,
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        presentation.weekChangePercent?.let { change ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.insights_week_change_value, change),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ---- AI suggestions card ----
                ClinicalCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(REPORT_SUGGESTIONS_TAG),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.insights_suggestions),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        BaselineInsightsMetrics.suggestions(grade, state.aiWeekDeltaPct).forEach { line ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.bullet),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(suggestionResource(line)),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Export card ----
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
                            text = stringResource(R.string.insights_export_report),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    if (isExporting) return@Button
                                    isExporting = true
                                    val exportReport = formatter.formatExport(reportModelFactory.create())
                                    scope.launch {
                                        val exportResult = withContext(Dispatchers.IO) {
                                            PdfReportExporter.export(
                                                File(context.cacheDir, "reports"),
                                                exportReport,
                                            )
                                        }
                                        when (exportResult) {
                                            is PdfExportResult.Failure -> showError(exportResult.error)
                                            is PdfExportResult.Success -> {
                                                when (
                                                    val shareResult = ReportSharing.sharePdf(
                                                        context,
                                                        exportResult.file,
                                                        context.getString(R.string.report_share_pdf),
                                                    )
                                                ) {
                                                    is ReportShareResult.Failure -> showError(shareResult.error)
                                                    ReportShareResult.Started -> Unit
                                                }
                                            }
                                        }
                                        isExporting = false
                                    }
                                },
                                enabled = !isExporting,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag(REPORT_EXPORT_PDF_TAG),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.insights_pdf_report), fontSize = 14.sp, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            OutlinedButton(
                                onClick = ::shareTextReport,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag(REPORT_EXPORT_SHARE_TAG),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.insights_share_chooser),
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun ReportError.messageResource(): Int = when (this) {
    ReportError.CREATE_FILE -> R.string.report_error_create_file
    ReportError.EMPTY_FILE -> R.string.report_error_empty_file
    ReportError.NO_SHARE_APP -> R.string.report_error_no_share_app
    ReportError.OPEN_FILE -> R.string.report_error_open_file
}

private fun trendInterpretationResource(trend: TrendInterpretation): Int = when (trend) {
    TrendInterpretation.RISING -> R.string.insights_trend_rising
    TrendInterpretation.STABLE -> R.string.insights_trend_stable
    TrendInterpretation.FALLING -> R.string.insights_trend_falling
    TrendInterpretation.INSUFFICIENT_DATA -> R.string.insights_trend_insufficient
}

const val REPORT_FACTOR_SUMMARY_TAG = "report_factor_summary"
const val REPORT_TREND_INTERPRETATION_TAG = "report_trend_interpretation"
const val REPORT_SUGGESTIONS_TAG = "report_suggestions"
const val REPORT_EXPORT_PDF_TAG = "report_export_pdf"
const val REPORT_EXPORT_SHARE_TAG = "report_export_share"
private const val STABLE_TREND_THRESHOLD_PERCENT = 10
