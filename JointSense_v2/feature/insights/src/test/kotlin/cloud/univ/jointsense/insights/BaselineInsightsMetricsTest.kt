package cloud.univ.jointsense.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BaselineInsightsMetricsTest {
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
}
