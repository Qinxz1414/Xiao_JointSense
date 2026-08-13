package cloud.univ.jointsense.measurement

import android.graphics.Bitmap
import android.graphics.Color
import cloud.univ.jointsense.analysis.CurveKnot
import cloud.univ.jointsense.analysis.FactoryCurves
import cloud.univ.jointsense.analysis.QuantificationResult
import cloud.univ.jointsense.analysis.StandardCurve
import cloud.univ.jointsense.domain.model.Calibration
import cloud.univ.jointsense.domain.model.CalibrationKnot
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.repository.CalibrationRepository
import kotlin.math.abs
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
        val features = extractFeatures(bitmap, cropBounds)
        val quantification = quantifyMeasurementSignal(
            factor = factor,
            rawSignal = features.tealness,
            userCalibration = calibration,
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
    rawSignal: Float,
    userCalibration: Calibration?,
): QuantificationResult {
    quantifyWithUserCalibration(
        factor = factor,
        rawSignal = rawSignal,
        calibration = userCalibration,
    )?.let { return it }

    // Keep the factory path in the raw-tealness domain.
    return FactoryCurves.forFactor(factor).quantify(rawSignal)
}

private fun quantifyWithUserCalibration(
    factor: InflammationFactor,
    rawSignal: Float,
    calibration: Calibration?,
): QuantificationResult? {
    calibration ?: return null
    if (calibration.status != CalibrationStatus.ACTIVE || calibration.factor != factor) return null
    val knots = calibration.knots
    val blank = knots.singleOrNull(CalibrationKnot::isBlank) ?: return null
    if (!knots.areStructurallyValid(blank)) return null

    val blankSubtractedSignal = (rawSignal.toDouble() - blank.rawSignal.toDouble())
        .toRepresentableFloatOrNull()
        ?: return null
    val curve = try {
        StandardCurve(
            knots.sortedBy(CalibrationKnot::concentration).map { knot ->
                CurveKnot(concentration = knot.concentration, signal = knot.fittedSignal)
            },
        )
    } catch (_: IllegalArgumentException) {
        return null
    }
    return curve.quantify(blankSubtractedSignal)
}

private fun List<CalibrationKnot>.areStructurallyValid(blank: CalibrationKnot): Boolean =
    size >= 2 &&
        blank.concentration == 0f &&
        blank.netSignal == 0f &&
        map(CalibrationKnot::position).distinct().size == size &&
        map(CalibrationKnot::concentration).distinct().size == size &&
        all { knot ->
            knot.concentration.isFinite() &&
                knot.concentration >= 0f &&
                knot.rawSignal.isFinite() &&
                knot.netSignal.isFinite() &&
                knot.fittedSignal.isFinite() &&
                (knot.isBlank || knot.concentration > 0f) &&
                knot.hasConsistentNetSignal(blank)
        }

private fun CalibrationKnot.hasConsistentNetSignal(blank: CalibrationKnot): Boolean {
    val expected = rawSignal.toDouble() - blank.rawSignal.toDouble()
    expected.toRepresentableFloatOrNull() ?: return false
    val actual = netSignal.toDouble()
    val tolerance = maxOf(
        NET_SIGNAL_ABSOLUTE_TOLERANCE,
        maxOf(abs(expected), abs(actual)) * NET_SIGNAL_RELATIVE_TOLERANCE,
    )
    return abs(expected - actual) <= tolerance
}

private fun Double.toRepresentableFloatOrNull(): Float? {
    if (!isFinite()) return null
    val converted = toFloat()
    return converted.takeIf { it.isFinite() && (this == 0.0 || it != 0f) }
}

private const val NET_SIGNAL_ABSOLUTE_TOLERANCE = 1e-4
private const val NET_SIGNAL_RELATIVE_TOLERANCE = 1e-5
