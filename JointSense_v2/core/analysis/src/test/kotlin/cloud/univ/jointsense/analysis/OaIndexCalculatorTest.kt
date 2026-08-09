package cloud.univ.jointsense.analysis

import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
