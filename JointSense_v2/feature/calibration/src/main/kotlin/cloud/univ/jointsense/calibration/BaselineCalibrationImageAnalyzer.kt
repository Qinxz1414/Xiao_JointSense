package cloud.univ.jointsense.calibration

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import cloud.univ.jointsense.domain.model.InflammationFactor

internal data class BaselineWellReading(
    val row: Int,
    val col: Int,
    val index: Int,
    val signal: Float,
)

/** Existing regular-grid signal extraction isolated for Phase-2 replacement. */
internal object BaselineCalibrationImageAnalyzer {
    fun detectGridSignals(
        bitmap: Bitmap,
        crop: Rect,
        rows: Int,
        cols: Int,
        wellFraction: Float = 0.6f,
    ): List<BaselineWellReading> {
        return legacyCalibrationSampleWindows(
            crop = CalibrationIntBounds(crop.left, crop.top, crop.right, crop.bottom),
            rows = rows,
            cols = cols,
            wellFraction = wellFraction,
        ).map { window ->
            BaselineWellReading(
                row = window.row,
                col = window.col,
                index = window.index,
                signal = bitmap.tealness(
                    window.left,
                    window.top,
                    window.width,
                    window.height,
                ),
            )
        }
    }
}

private fun Bitmap.tealness(left: Int, top: Int, width: Int, height: Int): Float {
    val safeLeft = left.coerceIn(0, this.width - 1)
    val safeTop = top.coerceIn(0, this.height - 1)
    val safeWidth = width.coerceIn(1, this.width - safeLeft)
    val safeHeight = height.coerceIn(1, this.height - safeTop)
    val pixels = IntArray(safeWidth * safeHeight)
    getPixels(pixels, 0, safeWidth, safeLeft, safeTop, safeWidth, safeHeight)
    return pixels.sumOf { (Color.blue(it) - Color.red(it)).toDouble() }.div(pixels.size).toFloat()
}

internal val FACTORY_LADDER: Map<InflammationFactor, List<Float>> = mapOf(
    InflammationFactor.TNF_ALPHA to listOf(0f, 2f, 5f, 10f, 20f, 50f, 100f, 200f, 500f),
    InflammationFactor.IL6 to listOf(0f, 5f, 10f, 20f, 50f, 100f, 200f, 500f, 1_000f),
    InflammationFactor.IL1_BETA to listOf(0f, 2f, 5f, 10f, 20f, 50f, 100f, 200f, 500f),
)

internal val InflammationFactor.shortName: String
    get() = when (this) {
        InflammationFactor.IL6 -> "IL-6"
        InflammationFactor.TNF_ALPHA -> "TNF-α"
        InflammationFactor.IL1_BETA -> "IL-1β"
    }
