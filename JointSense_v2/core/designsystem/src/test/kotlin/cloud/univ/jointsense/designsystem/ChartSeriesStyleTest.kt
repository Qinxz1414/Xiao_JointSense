package cloud.univ.jointsense.designsystem

import cloud.univ.jointsense.designsystem.chart.ChartLinePattern
import cloud.univ.jointsense.designsystem.chart.ChartMarkerShape
import cloud.univ.jointsense.designsystem.chart.chartSeriesStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartSeriesStyleTest {
    @Test
    fun seriesStyleMappingIsStableAndCyclesWithoutDependingOnColor() {
        val first = chartSeriesStyle(0)
        val second = chartSeriesStyle(1)
        val third = chartSeriesStyle(2)

        assertEquals(ChartMarkerShape.CIRCLE, first.markerShape)
        assertEquals(ChartLinePattern.SOLID, first.linePattern)
        assertEquals(ChartMarkerShape.SQUARE, second.markerShape)
        assertEquals(ChartLinePattern.DASHED, second.linePattern)
        assertEquals(ChartMarkerShape.TRIANGLE, third.markerShape)
        assertEquals(ChartLinePattern.DOTTED, third.linePattern)
        assertEquals(first, chartSeriesStyle(3))
    }

    @Test
    fun invalidNegativeSeriesIndexIsRejected() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            chartSeriesStyle(-1)
        }
    }
}
