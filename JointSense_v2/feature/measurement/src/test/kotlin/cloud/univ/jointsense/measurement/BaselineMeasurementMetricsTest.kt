package cloud.univ.jointsense.measurement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BaselineMeasurementMetricsTest {
    @Test
    fun validGradesPreserveLabels() {
        assertEquals("No risk", BaselineMeasurementMetrics.gradeLabel(0))
        assertEquals("Very severe", BaselineMeasurementMetrics.gradeLabel(4))
        assertEquals(0, BaselineMeasurementMetrics.grade(0f))
        assertEquals(4, BaselineMeasurementMetrics.grade(1f))
    }

    @Test
    fun corruptGradesFailAtTheBoundaryInsteadOfClamping() {
        listOf(-1, 5).forEach { grade ->
            assertThrows(IllegalArgumentException::class.java) {
                BaselineMeasurementMetrics.gradeLabel(grade)
            }
        }
    }

    @Test
    fun corruptAiInputsCannotBecomeValidEndpointGrades() {
        listOf(-0.01f, 1.01f, Float.NaN, Float.POSITIVE_INFINITY).forEach { ai ->
            assertThrows(IllegalArgumentException::class.java) { BaselineMeasurementMetrics.grade(ai) }
        }
    }
}
