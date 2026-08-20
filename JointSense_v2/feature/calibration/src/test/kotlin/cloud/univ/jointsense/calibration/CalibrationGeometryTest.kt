package cloud.univ.jointsense.calibration

import org.junit.Assert.assertEquals
import org.junit.Test

class CalibrationGeometryTest {
    @Test
    fun nonDivisibleCropUsesLegacyIntegerTruncationAtEverySamplingStep() {
        val windows = legacyCalibrationSampleWindows(
            crop = CalibrationIntBounds(left = 7, top = 11, right = 108, bottom = 114),
            rows = 3,
            cols = 3,
            wellFraction = 0.6f,
        )

        assertEquals(
            listOf(
                CalibrationSampleWindow(0, 0, 0, 13, 17, 20, 20),
                CalibrationSampleWindow(0, 1, 1, 46, 17, 20, 20),
                CalibrationSampleWindow(0, 2, 2, 80, 17, 20, 20),
                CalibrationSampleWindow(1, 0, 3, 13, 51, 20, 20),
                CalibrationSampleWindow(1, 1, 4, 46, 51, 20, 20),
                CalibrationSampleWindow(1, 2, 5, 80, 51, 20, 20),
                CalibrationSampleWindow(2, 0, 6, 13, 85, 20, 20),
                CalibrationSampleWindow(2, 1, 7, 46, 85, 20, 20),
                CalibrationSampleWindow(2, 2, 8, 80, 85, 20, 20),
            ),
            windows,
        )
    }

    @Test
    fun cropOverlayGeometryContainsBothHorizontalAndBothVerticalThirds() {
        val lines = calibrationGridLines(
            left = 10f,
            top = 20f,
            right = 109f,
            bottom = 122f,
        )

        assertEquals(
            listOf(
                CalibrationGridLine(43f, 20f, 43f, 122f),
                CalibrationGridLine(10f, 54f, 109f, 54f),
                CalibrationGridLine(76f, 20f, 76f, 122f),
                CalibrationGridLine(10f, 88f, 109f, 88f),
            ),
            lines,
        )
        assertEquals(4, lines.size)
    }
}
