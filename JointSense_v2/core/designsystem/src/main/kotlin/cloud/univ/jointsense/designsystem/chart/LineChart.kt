package cloud.univ.jointsense.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A data point for the line chart.
 */
data class ChartDataPoint(
    val label: String,   // X-axis label (e.g., "Test 1", time string)
    val value: Float     // Y-axis value (concentration)
)

/**
 * A simple line chart composable that displays data points connected by lines.
 * Used to show inflammation factor concentration trends over time.
 *
 * @param dataPoints List of data points to display
 * @param lineColor Color of the line and data points
 * @param modifier Modifier for the composable
 * @param yAxisLabel Label for the Y-axis (e.g., "pg/mL")
 */
@Composable
fun LineChart(
    dataPoints: List<ChartDataPoint>,
    yAxisLabel: String,
    formatValue: (Float) -> String,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    if (dataPoints.isEmpty()) return
    val axisColor = MaterialTheme.colorScheme.outline
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val pointCenterColor = MaterialTheme.colorScheme.surface
    val localDensity = LocalDensity.current
    val tickTextSize = with(localDensity) { 10.sp.toPx() }
    val axisTitleTextSize = with(localDensity) { 10.sp.toPx() }
    val valueTextSize = with(localDensity) { 10.sp.toPx() }
    val labelBaselineOffset = with(localDensity) { 14.sp.toPx() }
    val valueLabelOffset = with(localDensity) { 7.sp.toPx() }
    val baseLeftPadding = with(localDensity) { 60.dp.toPx() }
    val baseRightPadding = with(localDensity) { 20.dp.toPx() }
    val baseTopPadding = with(localDensity) { 20.dp.toPx() }
    val baseBottomPadding = with(localDensity) { 50.dp.toPx() }
    val seriesStyle = chartSeriesStyle(0)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        val paddingLeft = maxOf(baseLeftPadding, tickTextSize * 4.5f)
        val paddingRight = baseRightPadding
        val paddingTop = baseTopPadding
        val paddingBottom = maxOf(baseBottomPadding, tickTextSize * 2.5f)

        val chartWidth = size.width - paddingLeft - paddingRight
        val chartHeight = size.height - paddingTop - paddingBottom

        if (chartWidth <= 0 || chartHeight <= 0) return@Canvas

        // Calculate Y-axis range
        val minValue = dataPoints.minOf { it.value }
        val maxValue = dataPoints.maxOf { it.value }
        val valueRange = if (maxValue == minValue) 10f else (maxValue - minValue)
        val yMin = (minValue - valueRange * 0.1f).coerceAtLeast(0f)
        val yMax = maxValue + valueRange * 0.1f

        // Draw axes
        val axisStroke = 2f

        // Y-axis
        drawLine(
            color = axisColor,
            start = Offset(paddingLeft, paddingTop),
            end = Offset(paddingLeft, paddingTop + chartHeight),
            strokeWidth = axisStroke
        )
        // X-axis
        drawLine(
            color = axisColor,
            start = Offset(paddingLeft, paddingTop + chartHeight),
            end = Offset(paddingLeft + chartWidth, paddingTop + chartHeight),
            strokeWidth = axisStroke
        )

        // Draw Y-axis labels and grid lines
        val ySteps = 4
        for (i in 0..ySteps) {
            val y = paddingTop + chartHeight - (chartHeight * i / ySteps)
            val value = yMin + (yMax - yMin) * i / ySteps

            // Grid line
            if (i > 0) {
                drawLine(
                    color = gridColor,
                    start = Offset(paddingLeft, y),
                    end = Offset(paddingLeft + chartWidth, y),
                    strokeWidth = 1f
                )
            }

            // Y-axis label
            drawContext.canvas.nativeCanvas.drawText(
                formatValue(value),
                paddingLeft - 8f,
                y + tickTextSize * 0.4f,
                android.graphics.Paint().apply {
                    color = labelColor
                    textSize = tickTextSize
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
            )
        }

        // Draw Y-axis label
        drawContext.canvas.nativeCanvas.apply {
            save()
            rotate(-90f, 16f, paddingTop + chartHeight / 2)
            drawText(
                yAxisLabel,
                16f,
                paddingTop + chartHeight / 2,
                android.graphics.Paint().apply {
                    color = labelColor
                    textSize = axisTitleTextSize
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
            restore()
        }

        if (dataPoints.size == 1) {
            // Single point - just draw a dot
            val x = paddingLeft + chartWidth / 2
            val y = paddingTop + chartHeight - ((dataPoints[0].value - yMin) / (yMax - yMin) * chartHeight)

            drawChartMarker(
                shape = seriesStyle.markerShape,
                color = lineColor,
                centerColor = pointCenterColor,
                center = Offset(x, y),
                radius = 7f,
            )

            // Label
            drawContext.canvas.nativeCanvas.drawText(
                dataPoints[0].label,
                x,
                paddingTop + chartHeight + labelBaselineOffset,
                android.graphics.Paint().apply {
                    color = labelColor
                    textSize = tickTextSize
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )

            // Value label
            drawContext.canvas.nativeCanvas.drawText(
                formatValue(dataPoints[0].value),
                x,
                y - valueLabelOffset,
                android.graphics.Paint().apply {
                    color = lineColor.toArgb()
                    textSize = valueTextSize
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
        } else {
            // Multiple points - draw line chart
            val xStep = chartWidth / (dataPoints.size - 1).coerceAtLeast(1)
            val points = dataPoints.mapIndexed { index, point ->
                val x = paddingLeft + xStep * index
                val y = paddingTop + chartHeight - ((point.value - yMin) / (yMax - yMin) * chartHeight)
                Offset(x, y)
            }

            // Draw line
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(
                    width = 3f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = seriesStyle.linePattern.pathEffect(),
                )
            )

            // Draw area fill
            val fillPath = Path().apply {
                moveTo(points[0].x, paddingTop + chartHeight)
                lineTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
                lineTo(points.last().x, paddingTop + chartHeight)
                close()
            }
            drawPath(
                path = fillPath,
                color = lineColor.copy(alpha = 0.1f)
            )

            // Draw data points and labels
            for (i in points.indices) {
                // Data point
                drawChartMarker(
                    shape = seriesStyle.markerShape,
                    color = lineColor,
                    centerColor = pointCenterColor,
                    center = points[i],
                    radius = 7f,
                )

                // X-axis label
                drawContext.canvas.nativeCanvas.drawText(
                    dataPoints[i].label,
                    points[i].x,
                    paddingTop + chartHeight + labelBaselineOffset,
                    android.graphics.Paint().apply {
                        color = labelColor
                        textSize = tickTextSize
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )

                // Value label above point
                drawContext.canvas.nativeCanvas.drawText(
                    formatValue(dataPoints[i].value),
                    points[i].x,
                    points[i].y - valueLabelOffset,
                    android.graphics.Paint().apply {
                        color = lineColor.toArgb()
                        textSize = valueTextSize
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                    }
                )
            }
        }
    }
}
