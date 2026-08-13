package cloud.univ.jointsense.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.univ.jointsense.designsystem.theme.GradeColors
import kotlin.math.cos
import kotlin.math.sin

/** A point on a time axis. */
data class TimePoint(
    val time: Long,
    val value: Float
)

/** A named, colored series for the multi-line chart. */
data class ChartSeries(
    val name: String,
    val color: Color,
    val points: List<TimePoint>
)

/**
 * Tiny inline trend line used inside the home factor cards.
 */
@Composable
fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val pad = 6f
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        val min = values.min()
        val max = values.max()
        val range = if (max == min) 1f else max - min

        fun point(i: Int): Offset {
            val x = pad + if (values.size == 1) w / 2 else w * i / (values.size - 1)
            val y = pad + h - (values[i] - min) / range * h
            return Offset(x, y)
        }

        if (values.size == 1) {
            drawCircle(color, radius = 5f, center = point(0))
            return@Canvas
        }

        val path = Path().apply {
            moveTo(point(0).x, point(0).y)
            for (i in 1 until values.size) lineTo(point(i).x, point(i).y)
        }
        drawPath(
            path,
            color,
            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawCircle(color, radius = 5f, center = point(values.size - 1))
    }
}

/**
 * Multi-series line chart over a shared time axis, with date ticks,
 * grid lines and per-series dots. Used on the Trends screen.
 */
@Composable
fun MultiLineChart(
    series: List<ChartSeries>,
    yAxisLabel: String,
    formatValue: (Float) -> String,
    formatTime: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    val allPoints = series.flatMap { it.points }
    if (allPoints.isEmpty()) return
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val pointCenterColor = MaterialTheme.colorScheme.surface

    Canvas(modifier = modifier) {
        val density = this.density
        fun textSize(sp: Float) = sp * density

        val paddingLeft = 34f * density
        val paddingRight = 12f * density
        val paddingTop = 10f * density
        val paddingBottom = 22f * density

        val chartWidth = size.width - paddingLeft - paddingRight
        val chartHeight = size.height - paddingTop - paddingBottom
        if (chartWidth <= 0 || chartHeight <= 0) return@Canvas

        val tMin = allPoints.minOf { it.time }
        val tMaxRaw = allPoints.maxOf { it.time }
        val tMax = if (tMaxRaw == tMin) tMin + 86_400_000L else tMaxRaw
        val vMinRaw = allPoints.minOf { it.value }
        val vMaxRaw = allPoints.maxOf { it.value }
        val vRange = if (vMaxRaw == vMinRaw) 1f else (vMaxRaw - vMinRaw)
        val yMin = (vMinRaw - vRange * 0.15f).coerceAtLeast(0f)
        val yMax = vMaxRaw + vRange * 0.15f

        fun x(time: Long) = paddingLeft + chartWidth * (time - tMin) / (tMax - tMin)
        fun y(value: Float) =
            paddingTop + chartHeight - (value - yMin) / (yMax - yMin) * chartHeight

        // Horizontal grid + y labels
        val ySteps = 4
        for (i in 0..ySteps) {
            val gy = paddingTop + chartHeight - chartHeight * i / ySteps
            val gv = yMin + (yMax - yMin) * i / ySteps
            if (i > 0) {
                drawLine(gridColor, Offset(paddingLeft, gy), Offset(paddingLeft + chartWidth, gy), 1f)
            }
            drawContext.canvas.nativeCanvas.drawText(
                formatValue(gv),
                paddingLeft - 8f,
                gy + textSize(4f),
                android.graphics.Paint().apply {
                    color = labelColor
                    textSize = textSize(10f)
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
            )
        }

        // Baseline
        drawLine(
            axisColor,
            Offset(paddingLeft, paddingTop + chartHeight),
            Offset(paddingLeft + chartWidth, paddingTop + chartHeight),
            2f
        )

        // X date ticks
        val xTicks = 4
        for (i in 0..xTicks) {
            val t = tMin + (tMax - tMin) * i / xTicks
            val tx = x(t)
            drawContext.canvas.nativeCanvas.drawText(
                formatTime(t),
                tx,
                paddingTop + chartHeight + textSize(14f),
                android.graphics.Paint().apply {
                    color = labelColor
                    textSize = textSize(10f)
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
        }

        // Series
        for (s in series) {
            if (s.points.isEmpty()) continue
            val pts = s.points.sortedBy { it.time }
            if (pts.size == 1) {
                drawCircle(s.color, radius = 6f, center = Offset(x(pts[0].time), y(pts[0].value)))
                continue
            }
            val path = Path().apply {
                moveTo(x(pts[0].time), y(pts[0].value))
                for (i in 1 until pts.size) lineTo(x(pts[i].time), y(pts[i].value))
            }
            drawPath(
                path,
                s.color,
                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            for (p in pts) {
                drawCircle(pointCenterColor, radius = 6f, center = Offset(x(p.time), y(p.value)))
                drawCircle(s.color, radius = 4f, center = Offset(x(p.time), y(p.value)))
            }
        }
    }
}

/**
 * Semi-circular risk gauge with five grade-colored segments and a
 * needle at the given AI value (0..1).
 */
@Composable
fun GaugeChart(
    value: Float,
    modifier: Modifier = Modifier
) {
    val needleColor = MaterialTheme.colorScheme.onSurface
    val needleCenterColor = MaterialTheme.colorScheme.surface
    Canvas(modifier = modifier) {
        val strokeWidth = size.height * 0.28f
        // Radius must respect BOTH dimensions: a stroked semicircle of
        // radius R needs R + strokeWidth of vertical room, otherwise the
        // arc paints outside the canvas onto neighbouring content.
        val radius = minOf(size.width / 2f, size.height) - strokeWidth
        val center = Offset(size.width / 2f, size.height - strokeWidth / 2f)

        // Segment spans follow the grade boundaries: 0.25/0.25/0.25/0.15/0.10
        val fractions = listOf(0.25f, 0.25f, 0.25f, 0.15f, 0.10f)
        var start = 180f
        for (i in GradeColors.indices) {
            val sweep = fractions[i] * 180f
            drawArc(
                color = GradeColors[i],
                startAngle = start,
                sweepAngle = sweep - 1f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            start += sweep
        }

        // Needle
        val v = value.coerceIn(0f, 1f)
        val angle = Math.toRadians((180f + v * 180f).toDouble())
        val needleLen = radius - strokeWidth
        val tip = Offset(
            center.x + (needleLen * cos(angle)).toFloat(),
            center.y + (needleLen * sin(angle)).toFloat()
        )
        drawLine(needleColor, center, tip, strokeWidth = 5f, cap = StrokeCap.Round)
        drawCircle(needleColor, radius = 10f, center = center)
        drawCircle(needleCenterColor, radius = 4f, center = center)
    }
}

/**
 * 0..1 AI scale with the grade gradient and a marker at the value.
 */
@Composable
fun AiScaleBar(
    value: Float?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val width = maxWidth
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(
                        Brush.horizontalGradient(GradeColors),
                        RoundedCornerShape(5.dp)
                    )
            )
            if (value != null) {
                val fraction = value.coerceIn(0f, 1f)
                Text(
                    text = "▼",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .offset(x = width * fraction - 5.dp)
                        .align(Alignment.TopStart)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
        ) {
            listOf("0", "0.25", "0.50", "0.75", "1.00").forEachIndexed { i, label ->
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = when (i) {
                        0 -> androidx.compose.ui.text.style.TextAlign.Start
                        4 -> androidx.compose.ui.text.style.TextAlign.End
                        else -> androidx.compose.ui.text.style.TextAlign.Center
                    }
                )
            }
        }
    }
}
