package cloud.univ.jointsense.measurement

import android.graphics.Bitmap
import android.graphics.Color
import cloud.univ.jointsense.analysis.CurveKnot
import cloud.univ.jointsense.analysis.FactoryCurves
import cloud.univ.jointsense.analysis.QuantificationResult
import cloud.univ.jointsense.analysis.StandardCurve
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.repository.CalibrationRepository
import kotlin.math.sqrt
import kotlinx.coroutines.flow.first

interface MeasurementImage {
    val width: Int
    val height: Int

    fun release() = Unit
}

class BitmapMeasurementImage(
    val bitmap: Bitmap,
) : MeasurementImage {
    override val width: Int = bitmap.width
    override val height: Int = bitmap.height

    override fun release() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
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
        val features = extractFeatures(bitmap, cropBounds)
        val quantification = quantifyMeasurementSignal(
            factor = factor,
            signal = features.tealness,
            calibratedKnots = calibration?.knots
                ?.sortedBy { it.concentration }
                ?.map { it.concentration to it.fittedSignal }
                .orEmpty(),
        )
        return BaselineAnalysisResult(
            concentration = quantification.concentration,
            rangeStatus = quantification.rangeStatus,
            features = features,
        )
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

internal fun quantifyMeasurementSignal(
    factor: InflammationFactor,
    signal: Float,
    calibratedKnots: List<Pair<Float, Float>>,
): QuantificationResult {
    val curve = if (calibratedKnots.isEmpty()) {
        FactoryCurves.forFactor(factor)
    } else {
        StandardCurve(
            calibratedKnots.map { (concentration, fittedSignal) ->
                CurveKnot(concentration = concentration, signal = fittedSignal)
            },
        )
    }
    return curve.quantify(signal)
}
