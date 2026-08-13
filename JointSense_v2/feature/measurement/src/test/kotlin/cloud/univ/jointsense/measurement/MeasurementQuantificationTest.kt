package cloud.univ.jointsense.measurement

import cloud.univ.jointsense.analysis.FactoryCurves
import cloud.univ.jointsense.domain.model.Calibration
import cloud.univ.jointsense.domain.model.CalibrationKnot
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementQuantificationTest {
    @Test
    fun factoryPlateauUsesTaskOneRightContinuousQuantification() {
        val result = quantifyMeasurementSignal(
            factor = InflammationFactor.IL6,
            rawSignal = 0f,
            userCalibration = null,
        )

        assertEquals(200f, result.concentration)
        assertEquals(RangeStatus.IN_RANGE, result.rangeStatus)
    }

    @Test
    fun nonZeroBlankIsSubtractedBeforeUserCurveQuantificationAcrossBounds() {
        val knots = userKnots(blankRawSignal = 10f)

        val calibration = userCalibration(knots)
        val below = quantifyMeasurementSignal(InflammationFactor.TNF_ALPHA, 9f, calibration)
        val first = quantifyMeasurementSignal(InflammationFactor.TNF_ALPHA, 10f, calibration)
        val middle = quantifyMeasurementSignal(InflammationFactor.TNF_ALPHA, 20f, calibration)
        val last = quantifyMeasurementSignal(InflammationFactor.TNF_ALPHA, 30f, calibration)
        val above = quantifyMeasurementSignal(InflammationFactor.TNF_ALPHA, 31f, calibration)

        assertEquals(0f, below.concentration)
        assertEquals(RangeStatus.BELOW_RANGE, below.rangeStatus)
        assertEquals(0f, first.concentration)
        assertEquals(RangeStatus.IN_RANGE, first.rangeStatus)
        assertEquals(50f, middle.concentration)
        assertEquals(RangeStatus.IN_RANGE, middle.rangeStatus)
        assertEquals(100f, last.concentration)
        assertEquals(RangeStatus.IN_RANGE, last.rangeStatus)
        assertEquals(100f, above.concentration)
        assertEquals(RangeStatus.ABOVE_RANGE, above.rangeStatus)
    }

    @Test
    fun malformedBlankOrDerivedDataFallsBackToFactoryCurve() {
        val factor = InflammationFactor.TNF_ALPHA
        val rawSignal = 20f
        val expected = FactoryCurves.forFactor(factor).quantify(rawSignal)
        val valid = userKnots(blankRawSignal = 10f)
        val malformed = listOf(
            valid.map { it.copy(isBlank = false) },
            valid.mapIndexed { index, knot -> knot.copy(isBlank = index < 2) },
            valid.mapIndexed { index, knot ->
                if (index == 1) knot.copy(fittedSignal = Float.NaN) else knot
            },
            valid.mapIndexed { index, knot ->
                if (index == 0) knot.copy(rawSignal = Float.NaN) else knot
            },
            valid.mapIndexed { index, knot ->
                if (index == 1) knot.copy(netSignal = 999f) else knot
            },
            valid.mapIndexed { index, knot ->
                if (index == 1) knot.copy(concentration = 0f) else knot
            },
        )

        malformed.forEach { knots ->
            assertEquals(expected, quantifyMeasurementSignal(factor, rawSignal, userCalibration(knots)))
        }
    }

    @Test
    fun finiteButOverflowingBlankSubtractionFallsBackWithoutCrashing() {
        val factor = InflammationFactor.TNF_ALPHA
        val rawSignal = Float.MAX_VALUE
        val knots = listOf(
            CalibrationKnot(0, 0f, -Float.MAX_VALUE, 0f, 0f, isBlank = true),
            CalibrationKnot(1, 100f, 0f, Float.MAX_VALUE, 20f, isBlank = false),
        )

        assertEquals(
            FactoryCurves.forFactor(factor).quantify(rawSignal),
            quantifyMeasurementSignal(factor, rawSignal, userCalibration(knots)),
        )
    }

    @Test
    fun inactiveOrWrongFactorCalibrationCannotReplaceFactoryCurve() {
        val factor = InflammationFactor.TNF_ALPHA
        val rawSignal = 20f
        val expected = FactoryCurves.forFactor(factor).quantify(rawSignal)
        val active = userCalibration(userKnots(blankRawSignal = 10f))

        assertEquals(
            expected,
            quantifyMeasurementSignal(factor, rawSignal, active.copy(status = CalibrationStatus.NEEDS_REVIEW)),
        )
        assertEquals(
            expected,
            quantifyMeasurementSignal(factor, rawSignal, active.copy(factor = InflammationFactor.IL6)),
        )
    }

    @Test
    fun photoDomainMidpointWithExtremeFiniteUserConcentrationStaysFinite() {
        val calibration = userCalibration(
            listOf(
                CalibrationKnot(0, 0f, 0f, 0f, 0f, isBlank = true),
                CalibrationKnot(1, Float.MAX_VALUE, 10f, 10f, 10f, isBlank = false),
            ),
        )

        val result = quantifyMeasurementSignal(
            factor = InflammationFactor.TNF_ALPHA,
            rawSignal = 5f,
            userCalibration = calibration,
        )

        assertTrue(result.concentration.isFinite())
        assertTrue(result.concentration >= 0f)
        assertEquals(Float.MAX_VALUE / 2f, result.concentration, Float.MAX_VALUE * 1e-6f)
        assertEquals(RangeStatus.IN_RANGE, result.rangeStatus)
    }

    private fun userKnots(blankRawSignal: Float) = listOf(
        CalibrationKnot(0, 0f, blankRawSignal, 0f, 0f, isBlank = true),
        CalibrationKnot(1, 50f, blankRawSignal + 10f, 10f, 10f, isBlank = false),
        CalibrationKnot(2, 100f, blankRawSignal + 20f, 20f, 20f, isBlank = false),
    )

    private fun userCalibration(knots: List<CalibrationKnot>) = Calibration(
        factor = InflammationFactor.TNF_ALPHA,
        createdAt = 1L,
        version = 1,
        status = CalibrationStatus.ACTIVE,
        kitName = null,
        kitLot = null,
        knots = knots,
    )
}
