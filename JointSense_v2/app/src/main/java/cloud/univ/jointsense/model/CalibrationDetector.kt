package cloud.univ.jointsense.model

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Detects a regular rows×cols well grid inside a cropped plate region and
 * measures the tealness signal (B − R) of the liquid in each well.
 *
 * Detection is deliberately simple and robust: the crop is subdivided into
 * an even grid (the ELISA standard plates are regular 3×3 layouts) and the
 * central sub-region of each cell — the liquid well, away from the plastic
 * rim — is sampled. This avoids fragile circle/peak detection that proved
 * unreliable on phone photos.
 */
object CalibrationDetector {

    data class WellReading(
        val row: Int,
        val col: Int,
        val index: Int,
        val signal: Float
    )

    /**
     * @param bitmap source image
     * @param crop   plate region in image coordinates
     * @param rows   well rows
     * @param cols   well columns
     * @param wellFraction fraction of each cell (centered) used to sample the
     *                     liquid, so the plastic rim is excluded
     * @return one [WellReading] per well, in row-major order (index 0..n-1)
     */
    fun detectGridSignals(
        bitmap: Bitmap,
        crop: Rect,
        rows: Int,
        cols: Int,
        wellFraction: Float = 0.6f
    ): List<WellReading> {
        require(rows > 0 && cols > 0) { "rows and cols must be positive" }
        val cw = crop.width().toFloat() / cols
        val ch = crop.height().toFloat() / rows
        val margin = (1f - wellFraction) / 2f
        val readings = mutableListOf<WellReading>()
        var index = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cellLeft = crop.left + (cw * c).toInt()
                val cellTop = crop.top + (ch * r).toInt()
                val subLeft = (cellLeft + cw * margin).toInt()
                val subTop = (cellTop + ch * margin).toInt()
                val subW = (cw * wellFraction).toInt().coerceAtLeast(1)
                val subH = (ch * wellFraction).toInt().coerceAtLeast(1)
                val f = FeatureExtractor.extract(bitmap, subLeft, subTop, subW, subH)
                val signal = f.bMean - f.rMean
                readings.add(WellReading(r, c, index++, signal))
            }
        }
        return readings
    }
}
