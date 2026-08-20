package cloud.univ.jointsense.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TestModelsTest {
    @Test
    fun newResultKeepsStableScientificCodes() {
        val value = NewTestResult(
            factor = InflammationFactor.TNF_ALPHA,
            concentration = 42f,
            rangeStatus = RangeStatus.IN_RANGE,
            features = RgbFeatures(100f, 101f, 109f, 1f, 2f, 3f),
            rawSignal = 9f,
            signalMethod = ColorSignalMethod.PIXEL_BR_P90_V1,
        )

        assertEquals("TNF_ALPHA", value.factor.name)
        assertEquals(9f, value.features.tealness)
        assertEquals(ColorSignalMethod.PIXEL_BR_P90_V1, value.signalMethod)
    }

    @Test
    fun newMeasurementBatchRequiresExactlyThreeFactorsInPhysicalOrder() {
        val results = inflammationFactorPresentationOrder.map { factor -> newResult(factor) }

        val batch = NewMeasurementBatch(results = results, timestamp = 42L)

        assertEquals(inflammationFactorPresentationOrder, batch.results.map { it.factor })
        assertEquals(42L, batch.timestamp)
        assertThrows(IllegalArgumentException::class.java) {
            NewMeasurementBatch(results.dropLast(1), timestamp = 42L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NewMeasurementBatch(results.reversed(), timestamp = 42L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NewMeasurementBatch(
                results.map { it.copy(signalMethod = ColorSignalMethod.LEGACY_MEAN_BR) },
                timestamp = 42L,
            )
        }
    }

    @Test
    fun sessionCountsThreeFactorPhotoAsOneMeasurementBatch() {
        val results = inflammationFactorPresentationOrder.mapIndexed { index, factor ->
            result(
                id = "r$index",
                measurementBatchId = "batch-a",
                factor = factor,
                timestamp = 100L,
            )
        } + result(
            id = "legacy",
            measurementBatchId = null,
            factor = InflammationFactor.IL6,
            timestamp = 200L,
        )
        val session = TestSession("s", "Test", 1L, DataSource.USER, results)

        assertEquals(2, session.measurementBatchCount())
        assertEquals(
            inflammationFactorPresentationOrder,
            session.measurementBatches().first { it.id == "batch-a" }.results.map { it.factor },
        )
    }

    private fun newResult(factor: InflammationFactor) = NewTestResult(
        factor = factor,
        concentration = 1f,
        rangeStatus = RangeStatus.IN_RANGE,
        features = RgbFeatures(1f, 2f, 3f, 0f, 0f, 0f),
        rawSignal = 2f,
        signalMethod = ColorSignalMethod.PIXEL_BR_P90_V1,
    )

    private fun result(
        id: String,
        measurementBatchId: String?,
        factor: InflammationFactor,
        timestamp: Long,
    ) = TestResult(
        id = id,
        sessionId = "s",
        draftId = null,
        factor = factor,
        concentration = 1f,
        rangeStatus = RangeStatus.IN_RANGE,
        features = RgbFeatures(1f, 2f, 3f, 0f, 0f, 0f),
        timestamp = timestamp,
        measurementBatchId = measurementBatchId,
    )
}
