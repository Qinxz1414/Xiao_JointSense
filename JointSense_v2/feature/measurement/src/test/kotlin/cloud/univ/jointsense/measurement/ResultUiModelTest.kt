package cloud.univ.jointsense.measurement

import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ResultUiModelTest {
    @Test
    fun resultPreservesConcentrationFeaturesAndEveryRangeState() {
        RangeStatus.entries.forEach { status ->
            val result = result(status)

            val model = createResultUiModel(session(result), result)

            assertEquals(InflammationFactor.TNF_ALPHA, model.measuredFactor)
            assertEquals(42.5f, model.concentration)
            assertEquals(status, model.rangeStatus)
            assertEquals(FEATURES, model.features)
        }
    }

    @Test
    fun unknownRangeIsNeverPresentedAsInRange() {
        val result = result(RangeStatus.UNKNOWN)

        val model = createResultUiModel(session(result), result)

        assertEquals(RangeStatus.UNKNOWN, model.rangeStatus)
        assertNotEquals(RangeStatus.IN_RANGE, model.rangeStatus)
    }

    @Test
    fun factorSummaryContainsAllThreeAbsoluteSlots() {
        val result = result(RangeStatus.ABOVE_RANGE)

        val model = createResultUiModel(session(result), result)

        assertEquals(InflammationFactor.entries.toList(), model.factorValues.map(ResultFactorPresentation::factor))
        assertEquals(42.5f, model.factorValues.single { it.factor == InflammationFactor.TNF_ALPHA }.value)
        assertEquals(null, model.factorValues.single { it.factor == InflammationFactor.IL6 }.value)
        assertEquals(null, model.factorValues.single { it.factor == InflammationFactor.IL1_BETA }.value)
    }

    private fun result(status: RangeStatus) = TestResult(
        id = "result",
        sessionId = "session",
        draftId = "draft",
        factor = InflammationFactor.TNF_ALPHA,
        concentration = 42.5f,
        rangeStatus = status,
        features = FEATURES,
        timestamp = 2L,
    )

    private fun session(result: TestResult) = TestSession(
        id = "session",
        name = "Session",
        createdAt = 1L,
        source = DataSource.USER,
        results = listOf(result),
    )

    private companion object {
        val FEATURES = RgbFeatures(11f, 22f, 33f, 1f, 2f, 3f)
    }
}
