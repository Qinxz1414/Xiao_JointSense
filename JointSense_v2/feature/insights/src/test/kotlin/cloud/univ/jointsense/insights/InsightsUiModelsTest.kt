package cloud.univ.jointsense.insights

import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsUiModelsTest {
    @Test
    fun homeUsesLatestTimestampAndExactlySevenMostRecentObservations() {
        val results = (1L..9L).map { timestamp -> result(timestamp) }.shuffled()
        val state = HomeUiState(
            allResults = results,
            latestValues = mapOf(InflammationFactor.IL6 to 9f),
            currentAi = 0.45f,
            currentGrade = 1,
            aiSeries = (1L..9L).map { InsightPoint(time = it, value = it / 10f) }.shuffled(),
        )

        val model = state.toHomePresentation()

        assertFalse(model.isEmpty)
        assertEquals(9L, model.latestTimestamp)
        assertEquals((3L..9L).toList(), model.recentObservations.map(InsightPoint::time))
        assertEquals(7, model.recentObservations.size)
    }

    @Test
    fun emptyHomeNeverPublishesFabricatedMetrics() {
        val state = HomeUiState(
            allResults = emptyList(),
            latestValues = mapOf(InflammationFactor.IL6 to 99f),
            currentAi = 0.99f,
            currentGrade = 4,
            aiSeries = listOf(InsightPoint(1L, 0.99f)),
        )

        val model = state.toHomePresentation()

        assertTrue(model.isEmpty)
        assertNull(model.latestTimestamp)
        assertNull(model.oaIndex)
        assertNull(model.grade)
        assertTrue(model.factorValues.all { it.value == null })
        assertTrue(model.recentObservations.isEmpty())
    }

    @Test
    fun reportAlwaysContainsAbsoluteSlotsForAllThreeFactors() {
        val state = ReportUiState(
            latestValues = mapOf(
                InflammationFactor.TNF_ALPHA to 12f,
                InflammationFactor.IL6 to 34f,
            ),
            currentAi = 0.4f,
            currentGrade = 1,
            aiWeekDeltaPct = 3f,
        )

        val model = state.toReportPresentation()

        assertEquals(APPROVED_FACTOR_ORDER, model.factorValues.map(FactorPresentation::factor))
        assertEquals(12f, model.factorValues.single { it.factor == InflammationFactor.TNF_ALPHA }.value)
        assertEquals(34f, model.factorValues.single { it.factor == InflammationFactor.IL6 }.value)
        assertNull(model.factorValues.single { it.factor == InflammationFactor.IL1_BETA }.value)
    }

    @Test
    fun homeUsesApprovedFactorOrderAndFiltersCorruptPresentationValues() {
        val state = HomeUiState(
            allResults = listOf(result(1L)),
            latestValues = mapOf(
                InflammationFactor.TNF_ALPHA to Float.NaN,
                InflammationFactor.IL6 to Float.POSITIVE_INFINITY,
                InflammationFactor.IL1_BETA to 4f,
            ),
            currentAi = 1.1f,
            currentGrade = 5,
            aiSeries = listOf(
                InsightPoint(1L, Float.NaN),
                InsightPoint(2L, -0.1f),
                InsightPoint(3L, 0.5f),
                InsightPoint(4L, Float.POSITIVE_INFINITY),
            ),
        )

        val model = state.toHomePresentation()

        assertEquals(APPROVED_FACTOR_ORDER, model.factorValues.map(FactorPresentation::factor))
        assertNull(model.oaIndex)
        assertNull(model.grade)
        assertNull(model.factorValues[0].value)
        assertNull(model.factorValues[1].value)
        assertEquals(4f, model.factorValues[2].value)
        assertEquals(listOf(InsightPoint(3L, 0.5f)), model.recentObservations)
    }

    @Test
    fun reportFiltersNonFiniteAndOutOfRangePresentationValues() {
        val model = ReportUiState(
            latestValues = mapOf(
                InflammationFactor.TNF_ALPHA to Float.NEGATIVE_INFINITY,
                InflammationFactor.IL6 to 12f,
                InflammationFactor.IL1_BETA to Float.NaN,
            ),
            currentAi = -0.1f,
            currentGrade = -1,
            aiWeekDeltaPct = Float.POSITIVE_INFINITY,
        ).toReportPresentation()

        assertEquals(APPROVED_FACTOR_ORDER, model.factorValues.map(FactorPresentation::factor))
        assertNull(model.oaIndex)
        assertNull(model.grade)
        assertNull(model.weekChangePercent)
        assertEquals(TrendInterpretation.INSUFFICIENT_DATA, model.trend)
        assertEquals(12f, model.factorValues[1].value)
        assertNull(model.factorValues[0].value)
        assertNull(model.factorValues[2].value)
    }

    @Test
    fun reportPreviewPropagatesOnlyFiniteFactorComparisons() {
        val model = ReportUiState(
            factorDeltaPct7d = mapOf(
                InflammationFactor.TNF_ALPHA to 25f,
                InflammationFactor.IL6 to Float.NaN,
                InflammationFactor.IL1_BETA to Float.POSITIVE_INFINITY,
            ),
        ).toReportPresentation()

        assertEquals(25f, model.factorValues[0].weekChangePercent)
        assertNull(model.factorValues[1].weekChangePercent)
        assertNull(model.factorValues[2].weekChangePercent)
    }

    @Test
    fun reportTrendUsesEvidenceBasedWeeklyChangeMapping() {
        assertEquals(TrendInterpretation.INSUFFICIENT_DATA, trendInterpretation(null))
        assertEquals(TrendInterpretation.RISING, trendInterpretation(10.01f))
        assertEquals(TrendInterpretation.STABLE, trendInterpretation(10f))
        assertEquals(TrendInterpretation.STABLE, trendInterpretation(-10f))
        assertEquals(TrendInterpretation.FALLING, trendInterpretation(-10.01f))
    }

    private fun result(timestamp: Long) = TestResult(
        id = "result-$timestamp",
        sessionId = "session",
        draftId = null,
        factor = InflammationFactor.IL6,
        concentration = timestamp.toFloat(),
        rangeStatus = RangeStatus.IN_RANGE,
        features = RgbFeatures(1f, 2f, 3f, 4f, 5f, 6f),
        timestamp = timestamp,
    )

    private companion object {
        val APPROVED_FACTOR_ORDER = listOf(
            InflammationFactor.TNF_ALPHA,
            InflammationFactor.IL6,
            InflammationFactor.IL1_BETA,
        )
    }
}
