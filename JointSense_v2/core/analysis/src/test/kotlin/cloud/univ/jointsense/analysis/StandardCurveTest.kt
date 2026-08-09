package cloud.univ.jointsense.analysis

import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StandardCurveTest {
    private val curve = StandardCurve(listOf(CurveKnot(0f, -8f), CurveKnot(20f, -4f)))

    @Test
    fun midpointUsesSignalAsInputAndConcentrationAsOutput() {
        assertEquals(10f, curve.quantify(-6f).concentration, 0.001f)
        assertEquals(RangeStatus.IN_RANGE, curve.quantify(-6f).rangeStatus)
    }

    @Test
    fun outOfRangeValuesClampWithoutUsingOaCaps() {
        assertEquals(QuantificationResult(0f, RangeStatus.BELOW_RANGE), curve.quantify(-9f))
        assertEquals(QuantificationResult(20f, RangeStatus.ABOVE_RANGE), curve.quantify(-3f))
    }

    @Test
    fun exactFirstKnotIsInRangeAtItsConcentration() {
        assertEquals(QuantificationResult(0f, RangeStatus.IN_RANGE), curve.quantify(-8f))
    }

    @Test
    fun exactPlateauSignalUsesRightContinuousUpperConcentration() {
        val plateau = StandardCurve(
            listOf(
                CurveKnot(0f, -8f),
                CurveKnot(10f, -4f),
                CurveKnot(20f, -4f),
                CurveKnot(30f, 0f),
            ),
        )

        assertEquals(20f, plateau.quantify(-4f).concentration, 0.001f)
    }

    @Test
    fun constructorRejectsFewerThanTwoKnots() {
        assertThrows(IllegalArgumentException::class.java) {
            StandardCurve(listOf(CurveKnot(0f, -8f)))
        }
    }

    @Test
    fun constructorRejectsNonIncreasingConcentrations() {
        assertThrows(IllegalArgumentException::class.java) {
            StandardCurve(listOf(CurveKnot(20f, -4f), CurveKnot(20f, 0f)))
        }
    }

    @Test
    fun quantifyRejectsNonFiniteSignals() {
        assertThrows(IllegalArgumentException::class.java) { curve.quantify(Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { curve.quantify(Float.POSITIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { curve.quantify(Float.NEGATIVE_INFINITY) }
    }

    @Test
    fun factoryKnotsUseTheApprovedRightContinuousInverse() {
        val expected = mapOf(
            InflammationFactor.TNF_ALPHA to listOf(0f to -8f, 20f to -4f, 50f to 0f, 100f to 20f, 200f to 26f),
            InflammationFactor.IL6 to listOf(0f to -7f, 50f to -4f, 200f to 0f, 500f to 11f),
            InflammationFactor.IL1_BETA to listOf(0f to -11f, 50f to 17f, 100f to 20f, 200f to 33f),
        )

        expected.forEach { (factor, knots) ->
            val factory = FactoryCurves.forFactor(factor)
            knots.forEach { (concentration, signal) ->
                assertEquals("$factor at $signal", concentration, factory.quantify(signal).concentration, 0.001f)
                assertEquals("$factor at $signal", RangeStatus.IN_RANGE, factory.quantify(signal).rangeStatus)
            }
        }
    }
}
