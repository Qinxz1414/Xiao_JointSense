package cloud.univ.jointsense.analysis

import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class OaIndexCalculatorTest {
    @Test
    fun gradeUsesInclusiveLowerThresholds() {
        assertEquals(0, OaIndexCalculator.grade(0f))
        assertEquals(1, OaIndexCalculator.grade(0.25f))
        assertEquals(2, OaIndexCalculator.grade(0.50f))
        assertEquals(3, OaIndexCalculator.grade(0.75f))
        assertEquals(4, OaIndexCalculator.grade(0.90f))
        assertEquals(4, OaIndexCalculator.grade(1f))
    }

    @Test
    fun calculateReturnsNullWhenNoFactorsArePresent() {
        assertNull(OaIndexCalculator.calculate(emptyMap()))
    }

    @Test
    fun calculateRenormalizesWeightsAcrossPresentFactors() {
        val latest = mapOf(
            InflammationFactor.TNF_ALPHA to result(InflammationFactor.TNF_ALPHA, 500f),
            InflammationFactor.IL1_BETA to result(InflammationFactor.IL1_BETA, 0f),
        )

        assertEquals(0.4f / 0.65f, OaIndexCalculator.calculate(latest)!!, 0.0001f)
    }

    @Test
    fun calculateRejectsEveryNonFiniteConcentrationBeforeNormalization() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { concentration ->
            assertThrows(IllegalArgumentException::class.java) {
                OaIndexCalculator.calculate(
                    mapOf(InflammationFactor.TNF_ALPHA to result(InflammationFactor.TNF_ALPHA, concentration)),
                )
            }
        }
    }

    @Test
    fun gradeRejectsEveryNonFiniteIndex() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { ai ->
            assertThrows(IllegalArgumentException::class.java) { OaIndexCalculator.grade(ai) }
        }
    }

    @Test
    fun calculateNormalizesEveryFactorAtHalfAndFullCap() {
        val cases = listOf(
            InflammationFactor.TNF_ALPHA to (250f to 500f),
            InflammationFactor.IL6 to (500f to 1000f),
            InflammationFactor.IL1_BETA to (250f to 500f),
        )

        cases.forEach { (factor, values) ->
            assertEquals(0.5f, OaIndexCalculator.calculate(mapOf(factor to result(factor, values.first)))!!, 0.0001f)
            assertEquals(1f, OaIndexCalculator.calculate(mapOf(factor to result(factor, values.second)))!!, 0.0001f)
        }
    }

    @Test
    fun calculateUsesAllApprovedWeightsInMixedThreeFactorInput() {
        val latest = mapOf(
            InflammationFactor.TNF_ALPHA to result(InflammationFactor.TNF_ALPHA, 250f),
            InflammationFactor.IL6 to result(InflammationFactor.IL6, 1000f),
            InflammationFactor.IL1_BETA to result(InflammationFactor.IL1_BETA, 125f),
        )

        assertEquals(0.6125f, OaIndexCalculator.calculate(latest)!!, 0.0001f)
    }

    private fun result(factor: InflammationFactor, concentration: Float) = TestResult(
        id = factor.name,
        sessionId = "session",
        draftId = null,
        factor = factor,
        concentration = concentration,
        rangeStatus = RangeStatus.IN_RANGE,
        features = RgbFeatures(0f, 0f, 0f, 0f, 0f, 0f),
        timestamp = 1L,
    )
}
