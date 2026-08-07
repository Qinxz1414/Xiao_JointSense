package cloud.univ.jointsense.domain.repository

import cloud.univ.jointsense.domain.model.Calibration
import cloud.univ.jointsense.domain.model.InflammationFactor
import kotlinx.coroutines.flow.Flow

interface CalibrationRepository {
    fun observeCalibrations(): Flow<List<Calibration>>
    fun observeCalibration(factor: InflammationFactor): Flow<Calibration?>
    suspend fun save(calibration: Calibration)
    suspend fun clearAll()
}
