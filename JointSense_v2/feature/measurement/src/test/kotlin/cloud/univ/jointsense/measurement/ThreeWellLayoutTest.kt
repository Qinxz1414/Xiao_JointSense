package cloud.univ.jointsense.measurement

import cloud.univ.jointsense.domain.model.InflammationFactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreeWellLayoutTest {
    @Test
    fun horizontalCropMapsLeftCenterRightToApprovedFactorOrder() {
        val regions = calculateThreeWellSamplingRegions(CropBounds(10, 20, 310, 120))

        assertEquals(
            listOf(
                InflammationFactor.TNF_ALPHA,
                InflammationFactor.IL6,
                InflammationFactor.IL1_BETA,
            ),
            regions.map { it.factor },
        )
        assertEquals(listOf(60, 160, 260), regions.map { it.centerX })
        assertEquals(listOf(70, 70, 70), regions.map { it.centerY })
        assertEquals(listOf(30, 30, 30), regions.map { it.radiusX })
        assertEquals(listOf(30, 30, 30), regions.map { it.radiusY })
        assertTrue(regions.zipWithNext().all { (left, right) -> left.sampleRight <= right.sampleLeft })
    }

    @Test
    fun remainderPixelsStayCoveredWithoutChangingFactorOrder() {
        val regions = calculateThreeWellSamplingRegions(CropBounds(3, 5, 304, 106))

        assertEquals(3, regions.size)
        assertEquals(3, regions.first().cellLeft)
        assertEquals(304, regions.last().cellRight)
        assertTrue(regions.all { it.radiusX > 0 && it.radiusY > 0 })
    }

    @Test
    fun nonHorizontalOrTooSmallCropIsRejectedBeforeAnalysis() {
        assertThrows(IllegalArgumentException::class.java) {
            calculateThreeWellSamplingRegions(CropBounds(0, 0, 100, 100))
        }
        assertThrows(IllegalArgumentException::class.java) {
            calculateThreeWellSamplingRegions(CropBounds(0, 0, 2, 1))
        }
    }
}
