package cloud.univ.jointsense.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GridSignalDetectorTest {
    @Test
    fun detectsThreeByThreeSignalsInRowMajorOrderFromCentralWindows() {
        val image = FakeGridPixelSource(width = 90, height = 90) { x, y ->
            val row = y / 30
            val col = x / 30
            argb(red = 10 + row, blue = 20 + col)
        }

        val readings = GridSignalDetector.detectGridSignals(
            source = image,
            crop = CalibrationIntBounds(0, 0, 90, 90),
            rows = 3,
            cols = 3,
        )

        assertEquals((0..8).toList(), readings.map { it.index })
        assertEquals(listOf(10f, 11f, 12f, 9f, 10f, 11f, 8f, 9f, 10f), readings.map { it.signal })
    }

    @Test
    fun averagesBlueMinusRedAcrossEveryPixelInTheSamplingWindow() {
        val image = FakeGridPixelSource(width = 10, height = 10) { x, _ ->
            if (x < 5) argb(red = 4, blue = 10) else argb(red = 8, blue = 20)
        }

        val reading = GridSignalDetector.detectGridSignals(
            source = image,
            crop = CalibrationIntBounds(0, 0, 10, 10),
            rows = 1,
            cols = 1,
            wellFraction = 1f,
        ).single()

        assertEquals(9f, reading.signal)
    }

    @Test
    fun rejectsInvalidAndOutOfBoundsCropsWithoutReadingPixels() {
        val source = FakeGridPixelSource(width = 10, height = 10) { _, _ -> argb(1, 2) }
        val invalid = listOf(
            CalibrationIntBounds(0, 0, 0, 1),
            CalibrationIntBounds(5, 5, 4, 6),
            CalibrationIntBounds(-1, 0, 1, 1),
            CalibrationIntBounds(0, -1, 1, 1),
            CalibrationIntBounds(9, 9, 11, 10),
            CalibrationIntBounds(9, 9, 10, 11),
            CalibrationIntBounds(Int.MIN_VALUE, 0, Int.MAX_VALUE, 1),
        )

        invalid.forEach { crop ->
            assertThrows(IllegalArgumentException::class.java) {
                GridSignalDetector.detectGridSignals(source, crop, rows = 3, cols = 3)
            }
        }
        assertEquals(0, source.readCalls)
    }

    @Test
    fun minimumOnePixelCropIsHandledWithoutOverflowOrClampingOutsideCrop() {
        val source = FakeGridPixelSource(width = 1, height = 1) { _, _ -> argb(4, 10) }

        val readings = GridSignalDetector.detectGridSignals(
            source,
            CalibrationIntBounds(0, 0, 1, 1),
            rows = 3,
            cols = 3,
        )

        assertEquals(9, readings.size)
        assertEquals(List(9) { 6f }, readings.map { it.signal })
        assertEquals(9, source.readCalls)
    }
}

private class FakeGridPixelSource(
    override val width: Int,
    override val height: Int,
    private val pixel: (Int, Int) -> Int,
) : GridPixelSource {
    var readCalls = 0

    override fun getPixels(left: Int, top: Int, width: Int, height: Int): IntArray =
        IntArray(width * height) { offset ->
            readCalls += 1
            pixel(left + offset % width, top + offset / width)
        }
}

private fun argb(red: Int, blue: Int): Int =
    (0xff shl 24) or (red shl 16) or blue
