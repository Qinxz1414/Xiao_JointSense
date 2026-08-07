package cloud.univ.jointsense.calibration

import cloud.univ.jointsense.domain.model.Calibration
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.repository.CalibrationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineCalibrationControllerTest {
    @Test
    fun savePersistsTheCurrentNineWellBaselineThroughTheDomainRepository() = runTest {
        val repository = FakeCalibrationRepository()
        val controller = BaselineCalibrationController(repository, clock = { 123L })

        controller.save(
            factor = InflammationFactor.IL6,
            concentrations = listOf(0f, 5f, 10f, 20f, 50f, 100f, 200f, 500f, 1_000f),
            signals = listOf(4f, 6f, 8f, 10f, 12f, 14f, 16f, 18f, 20f),
        )

        val saved = repository.saved.single()
        assertEquals(InflammationFactor.IL6, saved.factor)
        assertEquals(123L, saved.createdAt)
        assertEquals(CalibrationStatus.ACTIVE, saved.status)
        assertEquals(9, saved.knots.size)
        assertTrue(saved.knots.first().isBlank)
        assertEquals(0f, saved.knots.first().netSignal)
        assertEquals(16f, saved.knots.last().netSignal)
    }

    @Test
    fun restoreFactoryClearsRoomCalibrations() = runTest {
        val repository = FakeCalibrationRepository()
        val controller = BaselineCalibrationController(repository)

        controller.restoreFactory()

        assertEquals(1, repository.clearCalls)
    }
}

private class FakeCalibrationRepository : CalibrationRepository {
    val saved = mutableListOf<Calibration>()
    var clearCalls = 0

    override fun observeCalibrations(): Flow<List<Calibration>> = MutableStateFlow(saved)
    override fun observeCalibration(factor: InflammationFactor): Flow<Calibration?> =
        MutableStateFlow(saved.lastOrNull { it.factor == factor })

    override suspend fun save(calibration: Calibration) {
        saved += calibration
    }

    override suspend fun clearAll() {
        clearCalls += 1
    }
}
