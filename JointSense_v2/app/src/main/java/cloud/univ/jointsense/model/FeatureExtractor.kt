package cloud.univ.jointsense.model

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.sqrt

/**
 * Extracts image features from a selected region of a bitmap.
 *
 * Features extracted (6 total, after Lasso feature selection):
 * 1. Red channel mean
 * 2. Green channel mean
 * 3. Blue channel mean
 * 4. Red channel standard deviation
 * 5. Green channel standard deviation
 * 6. Blue channel standard deviation
 *
 * Additional features (edge density and entropy) were eliminated
 * by Lasso regression with zero coefficients.
 */
object FeatureExtractor {

    data class Features(
        val rMean: Float,
        val gMean: Float,
        val bMean: Float,
        val rStd: Float,
        val gStd: Float,
        val bStd: Float
    ) {
        fun toFloatArray() = floatArrayOf(rMean, gMean, bMean, rStd, gStd, bStd)

        override fun toString(): String {
            return "R(μ=%.1f, σ=%.1f) G(μ=%.1f, σ=%.1f) B(μ=%.1f, σ=%.1f)".format(
                rMean, rStd, gMean, gStd, bMean, bStd
            )
        }
    }

    /**
     * Extract RGB mean and standard deviation from a rectangular region of the bitmap.
     *
     * @param bitmap Source bitmap
     * @param left Left coordinate of the region
     * @param top Top coordinate of the region
     * @param width Width of the region
     * @param height Height of the region
     * @return Features containing R, G, B mean and standard deviation
     */
    fun extract(bitmap: Bitmap, left: Int, top: Int, width: Int, height: Int): Features {
        // Ensure coordinates are within bitmap bounds
        val safeLeft = left.coerceIn(0, bitmap.width - 1)
        val safeTop = top.coerceIn(0, bitmap.height - 1)
        val safeWidth = width.coerceIn(1, bitmap.width - safeLeft)
        val safeHeight = height.coerceIn(1, bitmap.height - safeTop)

        val pixels = IntArray(safeWidth * safeHeight)
        bitmap.getPixels(pixels, 0, safeWidth, safeLeft, safeTop, safeWidth, safeHeight)

        val n = pixels.size.toFloat()
        if (n == 0f) return Features(0f, 0f, 0f, 0f, 0f, 0f)

        // Calculate sums and sum of squares for each channel
        var rSum = 0.0
        var gSum = 0.0
        var bSum = 0.0
        var rSqSum = 0.0
        var gSqSum = 0.0
        var bSqSum = 0.0

        for (pixel in pixels) {
            val r = Color.red(pixel).toDouble()
            val g = Color.green(pixel).toDouble()
            val b = Color.blue(pixel).toDouble()
            rSum += r
            gSum += g
            bSum += b
            rSqSum += r * r
            gSqSum += g * g
            bSqSum += b * b
        }

        // Mean: μ = (1/N) * Σ x_i
        val rMean = (rSum / n).toFloat()
        val gMean = (gSum / n).toFloat()
        val bMean = (bSum / n).toFloat()

        // Standard deviation: σ = sqrt((1/N) * Σ x_i² - μ²)
        val rVariance = (rSqSum / n - rMean.toDouble() * rMean.toDouble()).coerceAtLeast(0.0)
        val gVariance = (gSqSum / n - gMean.toDouble() * gMean.toDouble()).coerceAtLeast(0.0)
        val bVariance = (bSqSum / n - bMean.toDouble() * bMean.toDouble()).coerceAtLeast(0.0)

        val rStd = sqrt(rVariance).toFloat()
        val gStd = sqrt(gVariance).toFloat()
        val bStd = sqrt(bVariance).toFloat()

        return Features(rMean, gMean, bMean, rStd, gStd, bStd)
    }

    /**
     * Extract features from the entire bitmap.
     */
    fun extract(bitmap: Bitmap): Features {
        return extract(bitmap, 0, 0, bitmap.width, bitmap.height)
    }
}
