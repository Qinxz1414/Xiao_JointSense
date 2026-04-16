package cloud.univ.jointsense.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
    lineColor: Color = Color(0xFF0077B6),
    modifier: Modifier = Modifier,
    yAxisLabel: String = "pg/mL"
) {
    if (dataPoints.isEmpty()) return

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        val paddingLeft = 60f
        val paddingRight = 20f
        val paddingTop = 20f
        val paddingBottom = 50f

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
        val axisColor = Color(0xFF6C757D)
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
        val gridColor = Color(0xFFE0E0E0)
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
                "%.1f".format(value),
                paddingLeft - 8f,
                y + 4f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#6C757D")
                    textSize = 24f
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
                    color = android.graphics.Color.parseColor("#6C757D")
                    textSize = 22f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
            restore()
        }

        if (dataPoints.size == 1) {
            // Single point - just draw a dot
            val x = paddingLeft + chartWidth / 2
            val y = paddingTop + chartHeight - ((dataPoints[0].value - yMin) / (yMax - yMin) * chartHeight)

            drawCircle(
                color = lineColor,
                radius = 8f,
                center = Offset(x, y)
            )

            // Label
            drawContext.canvas.nativeCanvas.drawText(
                dataPoints[0].label,
                x,
                paddingTop + chartHeight + 35f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#6C757D")
                    textSize = 22f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )

            // Value label
            drawContext.canvas.nativeCanvas.drawText(
                "%.2f".format(dataPoints[0].value),
                x,
                y - 14f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#0077B6")
                    textSize = 22f
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
                    join = StrokeJoin.Round
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
                drawCircle(
                    color = Color.White,
                    radius = 7f,
                    center = points[i]
                )
                drawCircle(
                    color = lineColor,
                    radius = 5f,
                    center = points[i]
                )

                // X-axis label
                drawContext.canvas.nativeCanvas.drawText(
                    dataPoints[i].label,
                    points[i].x,
                    paddingTop + chartHeight + 35f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#6C757D")
                        textSize = 22f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )

                // Value label above point
                drawContext.canvas.nativeCanvas.drawText(
                    "%.2f".format(dataPoints[i].value),
                    points[i].x,
                    points[i].y - 14f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#0077B6")
                        textSize = 20f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                    }
                )
            }
        }
    }
}
