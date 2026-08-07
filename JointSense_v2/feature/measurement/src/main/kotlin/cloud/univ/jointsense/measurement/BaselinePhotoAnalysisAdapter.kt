package cloud.univ.jointsense.measurement

import android.graphics.Bitmap
import android.graphics.Color
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.repository.CalibrationRepository
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

interface MeasurementImage {
    val width: Int
    val height: Int
}

class BitmapMeasurementImage(
    val bitmap: Bitmap,
) : MeasurementImage {
    override val width: Int = bitmap.width
    override val height: Int = bitmap.height
}

data class CropBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class BaselineAnalysisResult(
    val concentration: Float,
    val rangeStatus: RangeStatus,
    val features: RgbFeatures,
)

interface BaselinePhotoAnalysisAdapter {
    suspend fun analyze(
        image: MeasurementImage,
        cropBounds: CropBounds,
        factor: InflammationFactor,
    ): BaselineAnalysisResult
}

/** Temporary Phase-1 photo path; Phase 2 replaces this adapter with the validated pipeline. */
class AndroidBaselinePhotoAnalysisAdapter(
    private val calibrations: CalibrationRepository,
) : BaselinePhotoAnalysisAdapter {
    override suspend fun analyze(
        image: MeasurementImage,
        cropBounds: CropBounds,
        factor: InflammationFactor,
    ): BaselineAnalysisResult {
        val bitmap = (image as? BitmapMeasurementImage)?.bitmap
            ?: error("Android photo analysis requires a BitmapMeasurementImage")
        val calibration = calibrations.observeCalibration(factor).first()
            ?.takeIf { it.status == CalibrationStatus.ACTIVE }
        return withContext(Dispatchers.Default) {
            val features = extractFeatures(bitmap, cropBounds)
            val knots = calibration?.knots
                ?.sortedBy { it.concentration }
                ?.map { it.concentration to it.fittedSignal }
                .orEmpty()
                .ifEmpty { FACTORY_KNOTS.getValue(factor) }
            BaselineAnalysisResult(
                concentration = baselineConcentrationFor(features.tealness, knots),
                rangeStatus = RangeStatus.UNKNOWN,
                features = features,
            )
        }
    }
}

private fun extractFeatures(bitmap: Bitmap, bounds: CropBounds): RgbFeatures {
    val left = bounds.left.coerceIn(0, bitmap.width - 1)
    val top = bounds.top.coerceIn(0, bitmap.height - 1)
    val width = bounds.width.coerceIn(1, bitmap.width - left)
    val height = bounds.height.coerceIn(1, bitmap.height - top)
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, left, top, width, height)
    val count = pixels.size.toDouble()
    var red = 0.0
    var green = 0.0
    var blue = 0.0
    var redSquared = 0.0
    var greenSquared = 0.0
    var blueSquared = 0.0
    pixels.forEach { pixel ->
        val r = Color.red(pixel).toDouble()
        val g = Color.green(pixel).toDouble()
        val b = Color.blue(pixel).toDouble()
        red += r
        green += g
        blue += b
        redSquared += r * r
        greenSquared += g * g
        blueSquared += b * b
    }
    val rMean = (red / count).toFloat()
    val gMean = (green / count).toFloat()
    val bMean = (blue / count).toFloat()
    return RgbFeatures(
        rMean = rMean,
        gMean = gMean,
        bMean = bMean,
        rStd = sqrt((redSquared / count - rMean * rMean).coerceAtLeast(0.0)).toFloat(),
        gStd = sqrt((greenSquared / count - gMean * gMean).coerceAtLeast(0.0)).toFloat(),
        bStd = sqrt((blueSquared / count - bMean * bMean).coerceAtLeast(0.0)).toFloat(),
    )
}

/** Preserves the existing Phase-1 interpolation behavior verbatim for later replacement. */
private fun baselineConcentrationFor(
    signal: Float,
    knots: List<Pair<Float, Float>>,
): Float {
    if (signal <= knots.first().second) return 0f
    if (signal >= knots.last().second) return knots.last().first
    for (index in 1 until knots.size) {
        if (signal <= knots[index].second) {
            val (signal0, concentration0) = knots[index - 1]
            val (signal1, concentration1) = knots[index]
            if (signal1 == signal0) return concentration1
            return concentration0 +
                (concentration1 - concentration0) * (signal - signal0) / (signal1 - signal0)
        }
    }
    return knots.last().first
}

private val FACTORY_KNOTS = mapOf(
    InflammationFactor.TNF_ALPHA to listOf(0f to -8f, 20f to -4f, 50f to 0f, 100f to 20f, 200f to 26f),
    InflammationFactor.IL6 to listOf(0f to -7f, 50f to -4f, 100f to 0f, 200f to 0f, 500f to 11f),
    InflammationFactor.IL1_BETA to listOf(0f to -11f, 20f to 17f, 50f to 17f, 100f to 20f, 200f to 33f),
)
