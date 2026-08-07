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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.univ.jointsense.core.designsystem.R
import cloud.univ.jointsense.designsystem.chart.ChartDataPoint
import cloud.univ.jointsense.designsystem.chart.GradeBar
import cloud.univ.jointsense.designsystem.chart.LineChart
import cloud.univ.jointsense.designsystem.chart.Sparkline
import cloud.univ.jointsense.designsystem.component.NavyTopBar
import cloud.univ.jointsense.designsystem.theme.AiLine
import cloud.univ.jointsense.designsystem.theme.BgLight
import cloud.univ.jointsense.designsystem.theme.GradeColors
import cloud.univ.jointsense.designsystem.theme.InkText
import cloud.univ.jointsense.designsystem.theme.PrimaryAccent
import cloud.univ.jointsense.designsystem.theme.TextSecondary
import cloud.univ.jointsense.designsystem.theme.factorColor
import cloud.univ.jointsense.domain.model.InflammationFactor
import java.text.SimpleDateFormat
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
        topBar = { NavyTopBar(title = "OA Inflammation Monitor") }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BgLight)
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                    .background(PrimaryAccent.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "JointSense Logo",
                    modifier = Modifier.size(72.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "JointSense",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = InkText
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "No tests yet.\nRun your first detection to build your\ninflammation dashboard.",
                fontSize = 13.sp,
                color = TextSecondary,
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
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test Now", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val lastTest = state.allResults.maxOf { it.timestamp }

    // Factor cards
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InflammationFactor.entries.forEach { factor ->
            val value = latest[factor]
            val spark = state.factorSeries[factor].orEmpty().takeLast(7).map { it.value }
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
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
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = value?.let { "%.2f".format(it) } ?: "—",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkText
                        )
                    }
                    Text(
                        text = "pg/mL",
                        fontSize = 9.sp,
                        color = TextSecondary
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "OA Inflammation Index (AI)",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
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
                                text = BaselineInsightsMetrics.gradeLabel(grade),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onOpenReport) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open AI report",
                    tint = TextSecondary
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Grade card
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
                fontSize = 13.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            GradeBar(currentGrade = grade)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Recent 7-day AI trend
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
                text = "Recent Trend (7 days)",
                fontSize = 13.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            val weekSeries = state.aiSeries.filter {
                it.time >= System.currentTimeMillis() - DAY_MILLIS * 7
            }.ifEmpty { state.aiSeries.takeLast(7) }
            val dayFormat = remember { SimpleDateFormat("MM/dd", Locale.getDefault()) }
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
                    lineColor = AiLine,
                    modifier = Modifier.fillMaxSize(),
                    yAxisLabel = "AI"
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Last test + test now footer
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Last test",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dateFormat.format(Date(lastTest)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = InkText
                )
            }
            Button(
                onClick = onTestNow,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
            ) {
                Text("Test Now", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
}
