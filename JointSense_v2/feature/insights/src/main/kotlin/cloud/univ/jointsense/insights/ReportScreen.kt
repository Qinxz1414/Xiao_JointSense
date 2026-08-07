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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.univ.jointsense.designsystem.chart.GaugeChart
import cloud.univ.jointsense.designsystem.component.NavyBarAction
import cloud.univ.jointsense.designsystem.component.NavyTopBar
import cloud.univ.jointsense.designsystem.theme.BgLight
import cloud.univ.jointsense.designsystem.theme.BioGreen
import cloud.univ.jointsense.designsystem.theme.GradeColors
import cloud.univ.jointsense.designsystem.theme.InkText
import cloud.univ.jointsense.designsystem.theme.PrimaryAccent
import cloud.univ.jointsense.designsystem.theme.TextSecondary
import cloud.univ.jointsense.designsystem.theme.TnfRed
import cloud.univ.jointsense.designsystem.theme.factorColor
import cloud.univ.jointsense.domain.model.InflammationFactor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI Report screen — cartilage inflammation assessment, 7-day change
 * analysis, 14-day risk gauge, rule-based AI suggestions and export.
 */
@Composable
fun ReportScreen(
    state: ReportUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ai = state.currentAi
    val grade = state.currentGrade

    val reportTitle = "JointSense AI Report"
    val reportLines = remember(state) {
        buildReportLines(state)
    }

    Scaffold(
        topBar = {
            NavyTopBar(
                title = "AI Report",
                actions = {
                    NavyBarAction(
                        icon = Icons.Default.Share,
                        contentDescription = "Share summary",
                        onClick = {
                            BaselineReportExporter.shareText(
                                context,
                                (listOf(reportTitle) + reportLines).joinToString("\n")
                            )
                        }
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BgLight)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (ai == null || grade == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Text(
                        text = "No data yet.\nRun a test to generate your AI report.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        textAlign = TextAlign.Center,
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                val gradeColor = GradeColors[grade]

                // ---- Assessment card ----
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
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
                                text = "Cartilage Inflammation Assessment",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = BaselineInsightsMetrics.activityLabel(grade),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = InkText
                            )
                            Text(
                                text = "AI %.2f - %s (grade %d)".format(
                                    ai,
                                    BaselineInsightsMetrics.gradeLabel(grade),
                                    grade,
                                ),
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ---- 7-day change card ----
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Change Trend (Last 7 Days)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = InkText
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        InflammationFactor.entries.forEach { factor ->
                            val delta = state.factorDeltaPct7d[factor]
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
                                    color = InkText,
                                    modifier = Modifier.weight(1f)
                                )
                                if (delta != null) {
                                    Icon(
                                        imageVector = if (delta >= 0) {
                                            Icons.Default.ArrowUpward
                                        } else {
                                            Icons.Default.ArrowDownward
                                        },
                                        contentDescription = null,
                                        tint = if (delta >= 0) TnfRed else BioGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "%.0f%%".format(kotlin.math.abs(delta)),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (delta >= 0) TnfRed else BioGreen
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "vs prev. week",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                } else {
                                    Text(
                                        text = "— no comparison",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Risk gauge card ----
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Risk Index",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = InkText,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "14-day OA progression forecast",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        GaugeChart(
                            value = ai,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        )
                        Text(
                            text = BaselineInsightsMetrics.riskLabel(grade),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = gradeColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ---- AI suggestions card ----
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "AI Suggestions",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = InkText
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        BaselineInsightsMetrics.suggestions(grade, state.aiWeekDeltaPct).forEach { line ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Text(
                                    text = "•",
                                    color = PrimaryAccent,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = line,
                                    fontSize = 13.sp,
                                    color = InkText,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ---- Export card ----
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Export Report",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = InkText
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    val file = BaselineReportExporter.buildPdf(
                                        context, reportTitle, reportLines
                                    )
                                    BaselineReportExporter.shareFile(context, file, "application/pdf")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryAccent
                                )
                            ) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("PDF Report", fontSize = 14.sp, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            OutlinedButton(
                                onClick = {
                                    BaselineReportExporter.shareText(
                                        context,
                                        (listOf(reportTitle) + reportLines)
                                            .joinToString("\n")
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = PrimaryAccent
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Share",
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    color = PrimaryAccent
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

/** Plain-text report body shared by PDF export and text sharing. */
private fun buildReportLines(state: ReportUiState): List<String> {
    val lines = mutableListOf<String>()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val ai = state.currentAi
    val grade = state.currentGrade ?: return lines

    lines += "Generated: ${dateFormat.format(Date())}"
    lines += ""
    lines += "OA Inflammation Index (AI): " +
        (ai?.let { "%.2f".format(it) } ?: "-") +
        "  -> ${BaselineInsightsMetrics.gradeLabel(grade)} (grade $grade)"
    lines += "14-day progression risk: ${BaselineInsightsMetrics.riskLabel(grade)}"
    lines += ""
    lines += "Latest quantitative values:"
    val latest = state.latestValues
    InflammationFactor.entries.forEach { factor ->
        val v = latest[factor]
        lines += "  - ${factor.shortName} (${factor.displayName}): " +
            (v?.let { "%.2f $FACTOR_UNIT".format(it) } ?: "not measured")
    }
    lines += ""
    lines += "Change vs previous week:"
    InflammationFactor.entries.forEach { factor ->
        val d = state.factorDeltaPct7d[factor]
        lines += "  - ${factor.shortName}: " +
            (d?.let { "%+.0f%%".format(it) } ?: "no comparison")
    }
    lines += ""
    lines += "AI suggestions:"
    BaselineInsightsMetrics.suggestions(grade, state.aiWeekDeltaPct).forEach { lines += "  * $it" }
    lines += ""
    lines += "Disclaimer: research prototype - not for medical diagnosis."
    return lines
}
