package cloud.univ.jointsense.measurement

import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MeasurementQuantificationTest {
    @Test
    fun factoryPlateauUsesTaskOneRightContinuousQuantification() {
        val result = quantifyMeasurementSignal(
            factor = InflammationFactor.IL6,
            signal = 0f,
            calibratedKnots = emptyList(),
        )

        assertEquals(200f, result.concentration)
        assertEquals(RangeStatus.IN_RANGE, result.rangeStatus)
    }

    @Test
    fun activeCalibrationKnotsUseTaskOneRangeSemantics() {
        val result = quantifyMeasurementSignal(
            factor = InflammationFactor.TNF_ALPHA,
            signal = 30f,
            calibratedKnots = listOf(0f to 0f, 50f to 10f, 100f to 20f),
        )

        assertEquals(100f, result.concentration)
        assertEquals(RangeStatus.ABOVE_RANGE, result.rangeStatus)
    }
}
