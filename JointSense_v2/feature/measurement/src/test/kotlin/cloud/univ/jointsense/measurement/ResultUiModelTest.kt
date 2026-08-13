package cloud.univ.jointsense.measurement

import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

        assertEquals(APPROVED_FACTOR_ORDER, model.factorValues.map(ResultFactorPresentation::factor))
        assertEquals(42.5f, model.factorValues.single { it.factor == InflammationFactor.TNF_ALPHA }.value)
        assertEquals(null, model.factorValues.single { it.factor == InflammationFactor.IL6 }.value)
        assertEquals(null, model.factorValues.single { it.factor == InflammationFactor.IL1_BETA }.value)
    }

    @Test
    fun corruptPersistedNumbersAreUnavailableAndNeverThrow() {
        val corrupt = result(RangeStatus.ABOVE_RANGE).copy(
            concentration = Float.NaN,
            features = RgbFeatures(
                rMean = Float.POSITIVE_INFINITY,
                gMean = 2f,
                bMean = 3f,
                rStd = 1f,
                gStd = 1f,
                bStd = 1f,
            ),
        )

        val model = createResultUiModel(session(corrupt), corrupt)

        assertEquals(InflammationFactor.TNF_ALPHA, model.measuredFactor)
        assertNull(model.concentration)
        assertNull(model.features)
        assertNull(model.factorValues.single { it.factor == InflammationFactor.TNF_ALPHA }.value)
        assertNull(model.oaIndex)
        assertNull(model.grade)
        assertTrue(model.factorValues.none { it.value?.isFinite() == false })
    }

    @Test
    fun finiteRawExtremesWithOverflowingTealnessAreUnavailable() {
        val overflow = result(RangeStatus.IN_RANGE).copy(
            features = RgbFeatures(
                rMean = -Float.MAX_VALUE,
                gMean = 0f,
                bMean = Float.MAX_VALUE,
                rStd = Float.MAX_VALUE,
                gStd = Float.MAX_VALUE,
                bStd = Float.MAX_VALUE,
            ),
        )

        val model = createResultUiModel(session(overflow), overflow)

        assertTrue(overflow.features.tealness.isInfinite())
        assertNull(model.features)
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
        val APPROVED_FACTOR_ORDER = listOf(
            InflammationFactor.TNF_ALPHA,
            InflammationFactor.IL6,
            InflammationFactor.IL1_BETA,
        )
    }
}
