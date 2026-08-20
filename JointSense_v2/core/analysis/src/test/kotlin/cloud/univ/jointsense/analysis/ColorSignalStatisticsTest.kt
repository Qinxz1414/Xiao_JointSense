package cloud.univ.jointsense.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ColorSignalStatisticsTest {
    @Test
    fun nearestRankP90UsesTheDocumentedPixelSignalDomain() {
        assertEquals(9f, nearestRankPercentile((1..10).toList().shuffled().toIntArray(), 0.90f))
        assertEquals(10f, nearestRankPercentile((1..10).toList().toIntArray(), 1f))
    }

    @Test
    fun rejectsEmptyOrInvalidPercentiles() {
        assertThrows(IllegalArgumentException::class.java) {
            nearestRankPercentile(intArrayOf(), 0.90f)
        }
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { percentile ->
            assertThrows(IllegalArgumentException::class.java) {
                nearestRankPercentile(intArrayOf(1), percentile)
            }
        }
    }
}
