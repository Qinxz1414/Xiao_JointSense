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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.univ.jointsense.designsystem.chart.AiScaleBar
import cloud.univ.jointsense.designsystem.chart.GradeBar
import cloud.univ.jointsense.designsystem.component.NavyTopBar
import cloud.univ.jointsense.designsystem.theme.BgLight
import cloud.univ.jointsense.designsystem.theme.GradeColors
import cloud.univ.jointsense.designsystem.theme.InkText
import cloud.univ.jointsense.designsystem.theme.PrimaryAccent
import cloud.univ.jointsense.designsystem.theme.StructureGray
import cloud.univ.jointsense.designsystem.theme.TextSecondary
import cloud.univ.jointsense.designsystem.theme.factorColor
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession

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
    onNewTest: () -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val results = session?.results ?: emptyList()
    val latestPerFactor = BaselineMeasurementMetrics.latestPerFactor(results)
    val ai = BaselineMeasurementMetrics.aiFromResults(results)
    val grade = ai?.let { BaselineMeasurementMetrics.grade(it) }

    Scaffold(
        topBar = { NavyTopBar(title = "Analysis Result") }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BgLight)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ---- Quantitative analysis card ----
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
                        text = "Quantitative Analysis (pg/mL)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = InkText
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
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = StructureGray, thickness = 1.dp)

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
                                color = InkText,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = value?.let { "%.2f".format(it) } ?: "—",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (value != null) InkText else TextSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                    text = FACTOR_UNIT,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        HorizontalDivider(
                            color = StructureGray.copy(alpha = 0.6f),
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
                                    text = "Well signal - ${result.factor.shortName} " +
                                        "(ELISA palette)",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "R(μ=%.1f, σ=%.1f) G(μ=%.1f, σ=%.1f) B(μ=%.1f, σ=%.1f)"
                                        .format(
                                            result.features.rMean, result.features.rStd,
                                            result.features.gMean, result.features.gStd,
                                            result.features.bMean, result.features.bStd
                                        ),
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---- AI index card ----
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
                        text = "OA Inflammation Index (AI)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = InkText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = ai?.let { "%.2f".format(it) } ?: "—",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkText
                        )
                        if (grade != null) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(GradeColors[grade])
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = BaselineMeasurementMetrics.gradeLabel(grade),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    AiScaleBar(value = ai)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---- Grade card ----
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
                        text = "OA Inflammation Grade",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = InkText
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    GradeBar(currentGrade = grade)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Actions ----
            if (canAddMore) {
                OutlinedButton(
                    onClick = onNewTest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = PrimaryAccent
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Retest",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryAccent
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Button(
                onClick = onGoHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Save Result",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Results are stored automatically on this device.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
