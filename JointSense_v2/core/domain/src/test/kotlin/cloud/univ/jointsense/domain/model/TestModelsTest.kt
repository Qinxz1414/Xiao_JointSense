package cloud.univ.jointsense.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TestModelsTest {
    @Test
    fun newResultKeepsStableScientificCodes() {
        val value = NewTestResult(
            factor = InflammationFactor.TNF_ALPHA,
            concentration = 42f,
            rangeStatus = RangeStatus.IN_RANGE,
            features = RgbFeatures(100f, 101f, 109f, 1f, 2f, 3f),
        )

        assertEquals("TNF_ALPHA", value.factor.name)
        assertEquals(9f, value.features.tealness)
    }
}
