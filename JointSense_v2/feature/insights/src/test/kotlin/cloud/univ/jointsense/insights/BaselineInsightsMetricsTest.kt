package cloud.univ.jointsense.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BaselineInsightsMetricsTest {
    @Test
    fun weeklyChangeRequiresAnObservationAtLeastSevenDaysOld() {
        assertNull(BaselineInsightsMetrics.aiWeekDeltaPct(emptyList(), NOW))
        assertNull(
            BaselineInsightsMetrics.aiWeekDeltaPct(
                listOf(
                    InsightPoint(NOW - DAY_MILLIS * 6, 0.2f),
                    InsightPoint(NOW, 0.3f),
                ),
                NOW,
            ),
        )
    }

    @Test
    fun weeklyChangeAcceptsTheExactBoundaryAndUsesLatestEligibleBaseline() {
        val change = BaselineInsightsMetrics.aiWeekDeltaPct(
            listOf(
                InsightPoint(NOW - DAY_MILLIS * 10, 0.1f),
                InsightPoint(NOW - DAY_MILLIS * 7, 0.2f),
                InsightPoint(NOW - DAY_MILLIS, 0.3f),
                InsightPoint(NOW, 0.4f),
            ).shuffled(),
            NOW,
        )

        assertEquals(100f, change!!, 0.001f)
    }

    @Test
    fun weeklyChangeUsesAnOlderBaselineWithTheCurrentLatestPoint() {
        val change = BaselineInsightsMetrics.aiWeekDeltaPct(
            listOf(
                InsightPoint(NOW - DAY_MILLIS * 8, 0.4f),
                InsightPoint(NOW - DAY_MILLIS, 0.5f),
                InsightPoint(NOW, 0.6f),
            ),
            NOW,
        )

        assertEquals(50f, change!!, 0.001f)
    }

    @Test
    fun weeklyChangeRejectsStaleFutureZeroAndNonFiniteEvidence() {
        assertNull(
            BaselineInsightsMetrics.aiWeekDeltaPct(
                listOf(
                    InsightPoint(NOW - DAY_MILLIS * 9, 0.2f),
                    InsightPoint(NOW - DAY_MILLIS * 8, 0.3f),
                ),
                NOW,
            ),
        )
        assertNull(
            BaselineInsightsMetrics.aiWeekDeltaPct(
                listOf(
                    InsightPoint(NOW - DAY_MILLIS * 8, 0.2f),
                    InsightPoint(NOW + 1, 0.4f),
                ),
                NOW,
            ),
        )
        listOf(0f, Float.NaN, Float.POSITIVE_INFINITY).forEach { invalidBaseline ->
            assertNull(
                BaselineInsightsMetrics.aiWeekDeltaPct(
                    listOf(
                        InsightPoint(NOW - DAY_MILLIS * 7, invalidBaseline),
                        InsightPoint(NOW, 0.4f),
                    ),
                    NOW,
                ),
            )
        }
    }

    @Test
    fun weeklyChangeIgnoresInvalidPointsWhenValidEvidenceExists() {
        val change = BaselineInsightsMetrics.aiWeekDeltaPct(
            listOf(
                InsightPoint(NOW - DAY_MILLIS * 8, 0.25f),
                InsightPoint(NOW - DAY_MILLIS * 7, Float.NaN),
                InsightPoint(NOW - DAY_MILLIS, Float.POSITIVE_INFINITY),
                InsightPoint(NOW, 0.5f),
                InsightPoint(NOW + 1, 0.9f),
            ),
            NOW,
        )

        assertEquals(100f, change!!, 0.001f)
    }

    @Test
    fun validGradesPreserveLabelsAndRecommendations() {
        assertEquals(0, BaselineInsightsMetrics.requireValidGrade(0))
        assertEquals(4, BaselineInsightsMetrics.requireValidGrade(4))
        assertEquals(0, BaselineInsightsMetrics.grade(0f))
        assertEquals(4, BaselineInsightsMetrics.grade(1f))
    }

    @Test
    fun corruptGradeInputsFailAtTheBoundaryInsteadOfClamping() {
        listOf(-1, 5).forEach { grade ->
            assertThrows(IllegalArgumentException::class.java) { BaselineInsightsMetrics.requireValidGrade(grade) }
            assertThrows(IllegalArgumentException::class.java) { BaselineInsightsMetrics.suggestions(grade, null) }
        }
    }

    @Test
    fun corruptAiInputsCannotBecomeValidEndpointGrades() {
        listOf(-0.01f, 1.01f, Float.NaN, Float.POSITIVE_INFINITY).forEach { ai ->
            assertThrows(IllegalArgumentException::class.java) { BaselineInsightsMetrics.grade(ai) }
        }
    }

    private companion object {
        const val NOW = 2_000_000_000_000L
    }
}
