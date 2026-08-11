package cloud.univ.jointsense.calibration

import android.graphics.Bitmap
import android.graphics.Rect

internal data class GridWellReading(
    val row: Int,
    val col: Int,
    val index: Int,
    val signal: Float,
)

internal interface GridPixelSource {
    val width: Int
    val height: Int
    fun getPixels(left: Int, top: Int, width: Int, height: Int): IntArray
}

internal object GridSignalDetector {
    fun detectGridSignals(
        bitmap: Bitmap,
        crop: CalibrationIntBounds,
        rows: Int = 3,
        cols: Int = 3,
        wellFraction: Float = 0.6f,
    ): List<GridWellReading> = detectGridSignals(
        source = BitmapPixelSource(bitmap),
        crop = crop,
        rows = rows,
        cols = cols,
        wellFraction = wellFraction,
    )

    fun detectGridSignals(
        bitmap: Bitmap,
        crop: Rect,
        rows: Int = 3,
        cols: Int = 3,
        wellFraction: Float = 0.6f,
    ): List<GridWellReading> = detectGridSignals(
        bitmap = bitmap,
        crop = CalibrationIntBounds(crop.left, crop.top, crop.right, crop.bottom),
        rows = rows,
        cols = cols,
        wellFraction = wellFraction,
    )

    fun detectGridSignals(
        source: GridPixelSource,
        crop: CalibrationIntBounds,
        rows: Int,
        cols: Int,
        wellFraction: Float = 0.6f,
    ): List<GridWellReading> {
        require(rows > 0 && cols > 0) { "rows and cols must be positive" }
        require(wellFraction > 0f && wellFraction <= 1f) {
            "wellFraction must be in (0, 1]"
        }
        return legacyCalibrationSampleWindows(crop, rows, cols, wellFraction).map { window ->
            val left = window.left.coerceIn(0, source.width - 1)
            val top = window.top.coerceIn(0, source.height - 1)
            val width = window.width.coerceIn(1, source.width - left)
            val height = window.height.coerceIn(1, source.height - top)
            val pixels = source.getPixels(left, top, width, height)
            GridWellReading(
                row = window.row,
                col = window.col,
                index = window.index,
                signal = pixels.sumOf { pixel ->
                    ((pixel and 0xff) - ((pixel ushr 16) and 0xff)).toDouble()
                }.div(pixels.size).toFloat(),
            )
        }
    }
}

private class BitmapPixelSource(
    private val bitmap: Bitmap,
) : GridPixelSource {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height

    override fun getPixels(left: Int, top: Int, width: Int, height: Int): IntArray =
        IntArray(width * height).also { pixels ->
            bitmap.getPixels(pixels, 0, width, left, top, width, height)
        }
}
