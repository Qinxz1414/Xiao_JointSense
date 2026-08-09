package cloud.univ.jointsense.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ImageDecodePolicyTest {
    @Test
    fun downsamplesLargeCameraImageToBoundedMemory() {
        assertEquals(2, calculateInSampleSize(4032, 3024, 2048))
    }

    @Test
    fun keepsImageWithinLimitAtFullResolution() {
        assertEquals(1, calculateInSampleSize(1280, 720, 2048))
    }

    @Test
    fun returnsPowerOfTwoThatBringsLongestEdgeWithinLimit() {
        assertEquals(4, calculateInSampleSize(8192, 4096, 2048))
        assertEquals(8, calculateInSampleSize(9000, 1000, 2048))
    }

    @Test
    fun rejectsInvalidDimensionsAndLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            calculateInSampleSize(0, 720, 2048)
        }
        assertThrows(IllegalArgumentException::class.java) {
            calculateInSampleSize(1280, -1, 2048)
        }
        assertThrows(IllegalArgumentException::class.java) {
            calculateInSampleSize(1280, 720, 0)
        }
    }
}
