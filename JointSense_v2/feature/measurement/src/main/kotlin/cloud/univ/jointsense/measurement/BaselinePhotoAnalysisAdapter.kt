package cloud.univ.jointsense.measurement

import android.graphics.Bitmap
import android.graphics.Color
import cloud.univ.jointsense.analysis.CurveKnot
import cloud.univ.jointsense.analysis.FactoryCurves
import cloud.univ.jointsense.analysis.FACTORY_CURVE_SIGNAL_PERCENTILE
import cloud.univ.jointsense.analysis.QuantificationResult
import cloud.univ.jointsense.analysis.StandardCurve
import cloud.univ.jointsense.analysis.nearestRankPercentile
import cloud.univ.jointsense.domain.model.Calibration
import cloud.univ.jointsense.domain.model.CalibrationKnot
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.ColorSignalMethod
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.inflammationFactorPresentationOrder
import cloud.univ.jointsense.domain.repository.CalibrationRepository
import kotlin.math.abs
import kotlin.math.floor
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
    val factor: InflammationFactor,
    val concentration: Float,
    val rangeStatus: RangeStatus,
    val features: RgbFeatures,
    val rawSignal: Float,
    val signalMethod: ColorSignalMethod,
)

interface BaselinePhotoAnalysisAdapter {
    suspend fun analyze(
        image: MeasurementImage,
        cropBounds: CropBounds,
    ): List<BaselineAnalysisResult>
}

/** Temporary Phase-1 photo path; Phase 2 replaces this adapter with the validated pipeline. */
class AndroidBaselinePhotoAnalysisAdapter(
    private val calibrations: CalibrationRepository,
) : BaselinePhotoAnalysisAdapter {
    override suspend fun analyze(
        image: MeasurementImage,
        cropBounds: CropBounds,
    ): List<BaselineAnalysisResult> {
        val bitmap = (image as? BitmapMeasurementImage)?.bitmap
            ?: error("Android photo analysis requires a BitmapMeasurementImage")
        require(cropBounds.left >= 0 && cropBounds.top >= 0)
        require(cropBounds.right <= bitmap.width && cropBounds.bottom <= bitmap.height)
        val calibrationByFactor = calibrations.observeCalibrations().first().associateBy(Calibration::factor)
        return calculateThreeWellSamplingRegions(cropBounds).map { region ->
            val statistics = extractSignalStatistics(bitmap, region)
            val quantification = quantifyMeasurementSignal(
                factor = region.factor,
                rawSignal = statistics.rawSignal,
                userCalibration = calibrationByFactor[region.factor],
            )
            BaselineAnalysisResult(
                factor = region.factor,
                concentration = quantification.concentration,
                rangeStatus = quantification.rangeStatus,
                features = statistics.features,
                rawSignal = statistics.rawSignal,
                signalMethod = ColorSignalMethod.PIXEL_BR_P90_V1,
            )
        }
    }
}

internal data class WellSamplingRegion(
    val factor: InflammationFactor,
    val cellLeft: Int,
    val cellRight: Int,
    val centerX: Int,
    val centerY: Int,
    val radiusX: Int,
    val radiusY: Int,
) {
    val sampleLeft: Int get() = centerX - radiusX
    val sampleTop: Int get() = centerY - radiusY
    val sampleRight: Int get() = centerX + radiusX
    val sampleBottom: Int get() = centerY + radiusY
}

internal fun calculateThreeWellSamplingRegions(bounds: CropBounds): List<WellSamplingRegion> {
    require(bounds.left >= 0 && bounds.top >= 0 && bounds.width >= MIN_ROW_WIDTH_PX && bounds.height >= MIN_ROW_HEIGHT_PX)
    val aspectRatio = bounds.width.toFloat() / bounds.height.toFloat()
    require(aspectRatio in MIN_ROW_ASPECT_RATIO..MAX_ROW_ASPECT_RATIO)
    val centerY = bounds.top + bounds.height / 2
    return inflammationFactorPresentationOrder.mapIndexed { index, factor ->
        val cellLeft = bounds.left + floor(index * bounds.width / 3.0).toInt()
        val cellRight = bounds.left + floor((index + 1) * bounds.width / 3.0).toInt()
        val cellWidth = cellRight - cellLeft
        val radiusX = maxOf(1, floor(cellWidth * ROI_RADIUS_FRACTION).toInt())
        val radiusY = maxOf(1, floor(bounds.height * ROI_RADIUS_FRACTION).toInt())
        WellSamplingRegion(
            factor = factor,
            cellLeft = cellLeft,
            cellRight = cellRight,
            centerX = cellLeft + cellWidth / 2,
            centerY = centerY,
            radiusX = radiusX,
            radiusY = radiusY,
        )
    }
}

private data class WellSignalStatistics(
    val features: RgbFeatures,
    val rawSignal: Float,
)

private fun extractSignalStatistics(bitmap: Bitmap, region: WellSamplingRegion): WellSignalStatistics {
    var accepted = 0L
    var candidates = 0L
    val red = RunningMoments()
    val green = RunningMoments()
    val blue = RunningMoments()
    val tealnessSignals = IntArray((region.sampleRight - region.sampleLeft) * (region.sampleBottom - region.sampleTop))
    for (y in region.sampleTop until region.sampleBottom) {
        for (x in region.sampleLeft until region.sampleRight) {
            val normalizedX = (x + 0.5 - region.centerX) / region.radiusX
            val normalizedY = (y + 0.5 - region.centerY) / region.radiusY
            if (normalizedX * normalizedX + normalizedY * normalizedY > 1.0) continue
            candidates += 1
            val pixel = bitmap.getPixel(x, y)
            if (Color.alpha(pixel) < MIN_OPAQUE_ALPHA) continue
            accepted += 1
            red.add(Color.red(pixel).toDouble())
            green.add(Color.green(pixel).toDouble())
            blue.add(Color.blue(pixel).toDouble())
            tealnessSignals[(accepted - 1).toInt()] = Color.blue(pixel) - Color.red(pixel)
        }
    }
    require(candidates >= MIN_ACCEPTED_PIXELS)
    require(accepted >= MIN_ACCEPTED_PIXELS)
    require(accepted.toDouble() / candidates.toDouble() >= MIN_OPAQUE_COVERAGE)
    return WellSignalStatistics(
        features = RgbFeatures(
            rMean = red.mean.toFloat(),
            gMean = green.mean.toFloat(),
            bMean = blue.mean.toFloat(),
            rStd = red.populationStandardDeviation(),
            gStd = green.populationStandardDeviation(),
            bStd = blue.populationStandardDeviation(),
        ),
        rawSignal = nearestRankPercentile(
            tealnessSignals.copyOf(accepted.toInt()),
            FACTORY_CURVE_SIGNAL_PERCENTILE,
        ),
    )
}

private class RunningMoments {
    private var count = 0L
    var mean = 0.0
        private set
    private var sumOfSquaredDeviations = 0.0

    fun add(value: Double) {
        count += 1
        val delta = value - mean
        mean += delta / count
        sumOfSquaredDeviations += delta * (value - mean)
    }

    fun populationStandardDeviation(): Float =
        sqrt((sumOfSquaredDeviations / count).coerceAtLeast(0.0)).toFloat()
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
    if (
        calibration.status != CalibrationStatus.ACTIVE ||
        calibration.factor != factor ||
        calibration.signalMethod != ColorSignalMethod.PIXEL_BR_P90_V1
    ) return null
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
    return curve.quantify(blankSubtractedSignal).takeIf { result ->
        result.concentration.isFinite() && result.concentration >= 0f
    }
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
private const val MIN_ROW_WIDTH_PX = 144
private const val MIN_ROW_HEIGHT_PX = 48
private const val MIN_ROW_ASPECT_RATIO = 2.4f
private const val MAX_ROW_ASPECT_RATIO = 4.2f
private const val ROI_RADIUS_FRACTION = 0.30
private const val MIN_ACCEPTED_PIXELS = 400
private const val MIN_OPAQUE_ALPHA = 230
private const val MIN_OPAQUE_COVERAGE = 0.90
