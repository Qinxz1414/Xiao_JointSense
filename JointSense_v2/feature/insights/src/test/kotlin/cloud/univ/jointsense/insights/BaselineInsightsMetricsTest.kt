package cloud.univ.jointsense.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BaselineInsightsMetricsTest {
    @Test
    fun validGradesPreserveLabelsAndRecommendations() {
        assertEquals("No risk", BaselineInsightsMetrics.gradeLabel(0))
        assertEquals("Very severe", BaselineInsightsMetrics.gradeLabel(4))
        assertEquals("Minimal OA activity", BaselineInsightsMetrics.activityLabel(0))
        assertEquals("Very high OA activity", BaselineInsightsMetrics.activityLabel(4))
        assertEquals("Very low risk", BaselineInsightsMetrics.riskLabel(0))
        assertEquals("Very high risk", BaselineInsightsMetrics.riskLabel(4))
        assertEquals(0, BaselineInsightsMetrics.grade(0f))
        assertEquals(4, BaselineInsightsMetrics.grade(1f))
    }

    @Test
    fun corruptGradeInputsFailAtTheBoundaryInsteadOfClamping() {
        listOf(-1, 5).forEach { grade ->
            assertThrows(IllegalArgumentException::class.java) { BaselineInsightsMetrics.gradeLabel(grade) }
            assertThrows(IllegalArgumentException::class.java) { BaselineInsightsMetrics.activityLabel(grade) }
            assertThrows(IllegalArgumentException::class.java) { BaselineInsightsMetrics.riskLabel(grade) }
            assertThrows(IllegalArgumentException::class.java) { BaselineInsightsMetrics.suggestions(grade, null) }
        }
    }

    @Test
    fun corruptAiInputsCannotBecomeValidEndpointGrades() {
        listOf(-0.01f, 1.01f, Float.NaN, Float.POSITIVE_INFINITY).forEach { ai ->
            assertThrows(IllegalArgumentException::class.java) { BaselineInsightsMetrics.grade(ai) }
        }
    }
}
